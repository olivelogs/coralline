//    CorallineAnalysis — Real-time audio analysis for the Coralline system.
//
//    Runs an FFT analyzer on the main output bus and provides:
//      - rms (amplitude/loudness)
//      - centroid (spectral brightness in Hz)
//      - flatness (0=tonal, 1=noisy)
//      - freq (detected pitch in Hz)
//      - hasFreq (pitch detection confidence)
//      - onsetRate (transient density in onsets/second)
//
//    Uses a callback pattern for sending results — set pongCallback
//    to receive analysis Events without coupling to any OSC layer.
//
//    Usage:
//        CorallineAnalysis.pongCallback = { |a| a.postln };
//        CorallineAnalysis.startAnalyzer;
//        CorallineAnalysis.hear;  // quick debug check
//
//    March 2026 — Olive + Claude

CorallineAnalysis {

    classvar <analyzerSynth;    // Synth running FFT analysis on main output
    classvar <analyzerBuses;    // IdentityDictionary of control buses for analysis metrics
    classvar <analyzerRunning;  // Boolean
    classvar <listenTask;       // SkipJack for continuous pong mode
    classvar <listenRate;       // Pong rate in Hz when listening
    classvar <listenReplyAddr;  // NetAddr the active listen stream replies to (nil = fallback)
    classvar <>pongCallback;    // Function receiving analysis Event

    // Onset rate tracking (sclang-side state)
    classvar <lastOnsetCount;
    classvar <lastOnsetTime;

    *initClass {
        analyzerSynth = nil;
        analyzerBuses = nil;
        analyzerRunning = false;
        listenTask = nil;
        listenRate = 4;  // default: 4 pongs per second
        listenReplyAddr = nil;
        pongCallback = nil;
        lastOnsetCount = 0;
        lastOnsetTime = 0;
    }


    *startAnalyzer {
        if(analyzerRunning) {
            "CorallineAnalysis: analyzer already running.".warn;
            ^this
        };

        fork {
            // Define the analyzer SynthDef
            SynthDef(\corallineAnalyzer, {
                var sig, mono, chain, onsetChain;
                var amp, freq, hasFreq, centroid, flatness;
                var onsetTrig, onsetCount;

                // Read main output bus — captures everything: SuperDirt, Tidal, all of it
                sig = In.ar(0, 2);
                mono = Mix.ar(sig) * 0.5;

                // Amplitude tracking (smoothed)
                amp = Amplitude.kr(mono, attackTime: 0.05, releaseTime: 0.2);

                // Pitch detection
                # freq, hasFreq = Pitch.kr(mono, minFreq: 60, maxFreq: 4000);

                // FFT analysis
                chain = FFT(LocalBuf(2048), mono);
                centroid = SpecCentroid.kr(chain);
                flatness = SpecFlatness.kr(chain);

                // Onset detection — needs its own FFT chain
                // Sharing with SpecCentroid/SpecFlatness interferes with Onsets' internal state
                onsetChain = FFT(LocalBuf(1024), mono);
                // \power detects energy increase (good for kicks/percussive hits)
                // threshold 0.1 is quite sensitive; raise if getting false triggers on pads
                onsetTrig = Onsets.kr(onsetChain, threshold: 0.1, odftype: \power);
                onsetCount = PulseCount.kr(onsetTrig);

                // Write to control buses
                Out.kr(\ampBus.kr(0), amp);
                Out.kr(\freqBus.kr(0), freq);
                Out.kr(\hasFreqBus.kr(0), hasFreq);
                Out.kr(\centroidBus.kr(0), centroid);
                Out.kr(\flatnessBus.kr(0), flatness);
                Out.kr(\onsetCountBus.kr(0), onsetCount);
            }).add;

            Server.default.sync;

            // Allocate control buses
            analyzerBuses = IdentityDictionary[
                \amp        -> Bus.control(Server.default, 1),
                \freq       -> Bus.control(Server.default, 1),
                \hasFreq    -> Bus.control(Server.default, 1),
                \centroid   -> Bus.control(Server.default, 1),
                \flatness   -> Bus.control(Server.default, 1),
                \onsetCount -> Bus.control(Server.default, 1),
            ];

            // Create analyzer synth at tail of default group
            // (so it reads after everything else has written to bus 0)
            analyzerSynth = Synth(\corallineAnalyzer, [
                \ampBus,        analyzerBuses[\amp].index,
                \freqBus,       analyzerBuses[\freq].index,
                \hasFreqBus,    analyzerBuses[\hasFreq].index,
                \centroidBus,   analyzerBuses[\centroid].index,
                \flatnessBus,   analyzerBuses[\flatness].index,
                \onsetCountBus, analyzerBuses[\onsetCount].index,
            ], target: Server.default.defaultGroup, addAction: \addToTail);

            // Reset onset tracking state
            lastOnsetCount = 0;
            lastOnsetTime = Main.elapsedTime;

            analyzerRunning = true;
            "CorallineAnalysis: audio analyzer started (listening on bus 0).".postln;
        };
    }

    *stopAnalyzer {
        if(analyzerRunning.not) { ^this };

        if(analyzerSynth.notNil) {
            analyzerSynth.free;
            analyzerSynth = nil;
        };

        if(analyzerBuses.notNil) {
            analyzerBuses.do { |bus| bus.free };
            analyzerBuses = nil;
        };

        analyzerRunning = false;
        "CorallineAnalysis: audio analyzer stopped.".postln;
    }

    // Read all analysis buses synchronously and return as Event.
    // .getSynchronous reads from shared memory — fast, no server roundtrip,
    // at most one control period behind.
    *getAnalysis {
        var currentCount, currentTime, dt, onsetRate;

        if(analyzerRunning.not or: { analyzerBuses.isNil }) {
            ^(\rms: 0, \centroid: 0, \flatness: 0, \freq: 0, \hasFreq: 0, \onsetRate: 0)
        };

        // Compute onset rate from count delta
        currentCount = analyzerBuses[\onsetCount].getSynchronous;
        currentTime = Main.elapsedTime;
        dt = currentTime - lastOnsetTime;
        onsetRate = if(dt > 0.01) {
            (currentCount - lastOnsetCount) / dt
        } { 0 };
        lastOnsetCount = currentCount;
        lastOnsetTime = currentTime;

        ^(
            \rms:       analyzerBuses[\amp].getSynchronous,
            \centroid:  analyzerBuses[\centroid].getSynchronous,
            \flatness:  analyzerBuses[\flatness].getSynchronous,
            \freq:      analyzerBuses[\freq].getSynchronous,
            \hasFreq:   analyzerBuses[\hasFreq].getSynchronous,
            \onsetRate: onsetRate  // onsets per second
        )
    }

    // Send a single audio analysis pong via the callback.
    // reqId is passed through for ping/pong matching (nil for continuous listening).
    // reply is the per-request NetAddr (nil → callback uses the fallback replyAddr).
    *sendAudioPong { |reqId, reply|
        var a = this.getAnalysis;
        if(pongCallback.notNil) {
            pongCallback.value(a, reqId, reply);
        };
    }

    // Start continuous listening — sends pong at the given rate to `reply`.
    // Note: a single analyzer drives one stream; if two clients start listening
    // the most recent reply target wins.
    *startListening { |rate, reply|
        rate = rate ?? { listenRate };
        listenRate = rate;
        listenReplyAddr = reply;

        this.stopListening;  // clear any existing

        if(analyzerRunning.not) {
            "CorallineAnalysis: can't listen — analyzer not running.".warn;
            ^this
        };

        listenTask = SkipJack(
            { this.sendAudioPong(nil, listenReplyAddr) },
            dt: rate.reciprocal,
            stopTest: { analyzerRunning.not },
            name: "CorallineAudioPong"
        );

        "CorallineAnalysis: listening at % Hz".format(rate).postln;
    }

    // Stop continuous listening.
    *stopListening {
        if(listenTask.notNil) {
            listenTask.stop;
            listenTask = nil;
            listenReplyAddr = nil;
            "CorallineAnalysis: stopped listening.".postln;
        };
    }

    *handlePingAudio { |reqId, reply|
        if(analyzerRunning.not) {
            "CorallineAnalysis: analyzer not running — call .startAnalyzer first".warn;
            ^this
        };

        this.sendAudioPong(reqId, reply);
    }

    // Quick check: what does the room sound like right now?
    *hear {
        var a = this.getAnalysis;
        "=== CorallineAnalysis hears ===".postln;
        "  rms:      %  (loudness)".format(a[\rms].round(0.001)).postln;
        "  centroid: % Hz  (brightness)".format(a[\centroid].round(1)).postln;
        "  flatness: %  (texture: 0=tone, 1=noise)".format(a[\flatness].round(0.001)).postln;
        "  freq:     % Hz  (pitch)".format(a[\freq].round(1)).postln;
        "  hasFreq:  %  (pitch confidence)".format(a[\hasFreq].round(0.01)).postln;
        "  onsetRate: %/s  (rhythmic density)".format(a[\onsetRate].round(0.1)).postln;
        ^a
    }
}
