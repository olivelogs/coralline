//    CorallineAnalysis — Real-time audio analysis for the Coralline system.
//
//    Runs an FFT analyzer on the main output bus and provides:
//      - rms (amplitude/loudness)
//      - centroid (spectral brightness in Hz)
//      - flatness (0=tonal, 1=noisy)
//      - freq (detected pitch in Hz)
//      - hasFreq (pitch detection confidence)
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
    classvar <>pongCallback;    // Function receiving analysis Event

    *initClass {
        analyzerSynth = nil;
        analyzerBuses = nil;
        analyzerRunning = false;
        listenTask = nil;
        listenRate = 4;  // default: 4 pongs per second
        pongCallback = nil;
    }

    *startAnalyzer {
        if(analyzerRunning) {
            "CorallineAnalysis: analyzer already running.".warn;
            ^this
        };

        fork {
            // Define the analyzer SynthDef
            SynthDef(\corallineAnalyzer, {
                var sig, mono, chain;
                var amp, freq, hasFreq, centroid, flatness;

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

                // Write to control buses
                Out.kr(\ampBus.kr(0), amp);
                Out.kr(\freqBus.kr(0), freq);
                Out.kr(\hasFreqBus.kr(0), hasFreq);
                Out.kr(\centroidBus.kr(0), centroid);
                Out.kr(\flatnessBus.kr(0), flatness);
            }).add;

            Server.default.sync;

            // Allocate control buses
            analyzerBuses = IdentityDictionary[
                \amp      -> Bus.control(Server.default, 1),
                \freq     -> Bus.control(Server.default, 1),
                \hasFreq  -> Bus.control(Server.default, 1),
                \centroid -> Bus.control(Server.default, 1),
                \flatness -> Bus.control(Server.default, 1),
            ];

            // Create analyzer synth at tail of default group
            // (so it reads after everything else has written to bus 0)
            analyzerSynth = Synth(\corallineAnalyzer, [
                \ampBus,      analyzerBuses[\amp].index,
                \freqBus,     analyzerBuses[\freq].index,
                \hasFreqBus,  analyzerBuses[\hasFreq].index,
                \centroidBus, analyzerBuses[\centroid].index,
                \flatnessBus, analyzerBuses[\flatness].index,
            ], target: Server.default.defaultGroup, addAction: \addToTail);

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
        if(analyzerRunning.not or: { analyzerBuses.isNil }) {
            ^(\rms: 0, \centroid: 0, \flatness: 0, \freq: 0, \hasFreq: 0)
        };

        ^(
            \rms:      analyzerBuses[\amp].getSynchronous,
            \centroid: analyzerBuses[\centroid].getSynchronous,
            \flatness: analyzerBuses[\flatness].getSynchronous,
            \freq:     analyzerBuses[\freq].getSynchronous,
            \hasFreq:  analyzerBuses[\hasFreq].getSynchronous
        )
    }

    // Send a single audio analysis pong via the callback.
    *sendAudioPong {
        var a = this.getAnalysis;
        if(pongCallback.notNil) {
            pongCallback.value(a);
        };
    }

    // Start continuous listening — sends pong at the given rate.
    *startListening { |rate|
        rate = rate ?? { listenRate };
        listenRate = rate;

        this.stopListening;  // clear any existing

        if(analyzerRunning.not) {
            "CorallineAnalysis: can't listen — analyzer not running.".warn;
            ^this
        };

        listenTask = SkipJack(
            { this.sendAudioPong },
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
            "CorallineAnalysis: stopped listening.".postln;
        };
    }

    *handlePingAudio {
        if(analyzerRunning.not) {
            "CorallineAnalysis: analyzer not running — call .startAnalyzer first".warn;
            ^this
        };

        this.sendAudioPong;
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
        ^a
    }
}
