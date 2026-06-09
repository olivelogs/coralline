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
//    Beyond the instantaneous snapshot, two rolling histories are kept:
//      - a sclang-side ring of analysis frames (historyRate Hz, historySeconds
//        deep) — getWindowAnalysis(window) summarizes the last N seconds with
//        stats and a short time-series, so "what did that sound like" works
//        no matter when you ask
//      - a server-side audio ring of the master bus — saveClip(duration)
//        writes the last N seconds to a wav for deep offline analysis
//
//    Uses a callback pattern for sending results — set pongCallback
//    to receive analysis Events without coupling to any OSC layer.
//
//    Usage:
//        CorallineAnalysis.pongCallback = { |a| a.postln };
//        CorallineAnalysis.startAnalyzer;
//        CorallineAnalysis.hear;       // quick debug check
//        CorallineAnalysis.getWindowAnalysis(4);  // last 4 seconds, summarized
//        CorallineAnalysis.saveClip(8, { |path| path.postln });
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

    // Rolling frame history (sclang-side ring buffer)
    classvar <historyTask;      // SkipJack polling buses into the ring
    classvar <historyRate;      // frames per second
    classvar <historySeconds;   // ring depth in seconds
    classvar <historyFrames;    // Array used as circular buffer
    classvar <historyIndex;     // next write slot
    classvar <historyCount;     // frames written so far (caps at ring size)

    // Audio clip ring buffer (server-side, for saveClip)
    classvar <clipBuffer;       // stereo Buffer holding the last clipSeconds of bus 0
    classvar <clipPosBus;       // control bus carrying the ring write position
    classvar <clipSynth;        // Synth recording into the ring
    classvar <clipSeconds;      // ring depth in seconds
    classvar <clipStartTime;    // when recording began (to know how much is valid)
    classvar <clipDir;          // where saveClip writes wavs

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
        historyTask = nil;
        historyRate = 10;
        historySeconds = 60;
        historyFrames = nil;
        historyIndex = 0;
        historyCount = 0;
        clipBuffer = nil;
        clipPosBus = nil;
        clipSynth = nil;
        clipSeconds = 60;
        clipStartTime = nil;
        // recordings/ at the repo root, derived from this class file's location
        // (<repo>/quark/Coralline/Classes/CorallineAnalysis.sc) — clips are
        // keepsakes, not temp files
        clipDir = this.filenameSymbol.asString.dirname.dirname.dirname.dirname +/+ "recordings";
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

            // Clip recorder: circular-record the master bus so saveClip
            // can dump the last N seconds on demand
            SynthDef(\corallineClipRec, {
                var buf = \buf.kr(0);
                var sig = In.ar(0, 2);
                var phase = Phasor.ar(0, 1, 0, BufFrames.kr(buf));
                BufWr.ar(sig, buf, phase);
                Out.kr(\posBus.kr(0), A2K.kr(phase));
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

            // Allocate the audio clip ring and start recording into it
            clipBuffer = Buffer.alloc(
                Server.default,
                (Server.default.sampleRate * clipSeconds).asInteger,
                2
            );
            clipPosBus = Bus.control(Server.default, 1);

            Server.default.sync;

            clipSynth = Synth(\corallineClipRec, [
                \buf,    clipBuffer,
                \posBus, clipPosBus.index,
            ], target: Server.default.defaultGroup, addAction: \addToTail);
            clipStartTime = Main.elapsedTime;

            // Reset onset tracking state
            lastOnsetCount = 0;
            lastOnsetTime = Main.elapsedTime;

            analyzerRunning = true;
            this.prStartHistory;
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

        if(clipSynth.notNil) {
            clipSynth.free;
            clipSynth = nil;
        };

        if(clipBuffer.notNil) {
            clipBuffer.free;
            clipBuffer = nil;
        };

        if(clipPosBus.notNil) {
            clipPosBus.free;
            clipPosBus = nil;
        };
        clipStartTime = nil;

        analyzerRunning = false;  // historyTask's stopTest picks this up

        if(historyTask.notNil) {
            historyTask.stop;
            historyTask = nil;
        };

        "CorallineAnalysis: audio analyzer stopped.".postln;
    }

    // ---- Rolling frame history ----

    // Read all buses raw (cumulative onset count, no rate state).
    // NaN-guards spectral values: FFT of silence can produce NaN,
    // which would poison every windowed mean downstream.
    *prReadFrame {
        var clean = { |v| if(v.isNaN) { 0 } { v } };
        ^(
            \time:       Main.elapsedTime,
            \rms:        clean.(analyzerBuses[\amp].getSynchronous),
            \centroid:   clean.(analyzerBuses[\centroid].getSynchronous),
            \flatness:   clean.(analyzerBuses[\flatness].getSynchronous),
            \freq:       clean.(analyzerBuses[\freq].getSynchronous),
            \hasFreq:    clean.(analyzerBuses[\hasFreq].getSynchronous),
            \onsetCount: clean.(analyzerBuses[\onsetCount].getSynchronous)
        )
    }

    *prStartHistory {
        var size = (historySeconds * historyRate).asInteger;
        historyFrames = Array.newClear(size);
        historyIndex = 0;
        historyCount = 0;

        historyTask = SkipJack(
            {
                if(analyzerRunning and: { analyzerBuses.notNil }) {
                    historyFrames[historyIndex] = this.prReadFrame;
                    historyIndex = (historyIndex + 1) % size;
                    historyCount = (historyCount + 1).min(size);
                }
            },
            dt: historyRate.reciprocal,
            stopTest: { analyzerRunning.not },
            name: "CorallineAudioHistory"
        );
    }

    // History frames oldest-first.
    *prHistoryInOrder {
        if(historyFrames.isNil or: { historyCount == 0 }) { ^[] };
        if(historyCount < historyFrames.size) {
            ^historyFrames.copyRange(0, historyCount - 1)
        };
        ^historyFrames.rotate(historyIndex.neg)
    }

    // Summarize the last `window` seconds of analysis frames.
    // Returns an Event with stats, onset density, and a short
    // time-series (rms + centroid) showing the shape of the window.
    // Returns nil if no history is available yet (caller should fall
    // back to getAnalysis).
    //
    // Spectral stats (centroid, flatness, pitch) are computed over
    // *active* frames only — frames where rms clears a silence
    // threshold — so silence between notes doesn't drag them to zero.
    *getWindowAnalysis { |window = 4|
        var activeThresh = 0.002;   // ~-54 dB: below this a frame counts as silence
        var seriesPoints = 12;
        var now, frames, span, rmsVals, activeFrames, pitched;
        var centroidMean, flatnessMean, freqMedian, pitchStability;
        var onsetCount, onsetRate, clumpSize, rmsSeries, centroidSeries;

        if(analyzerRunning.not or: { historyCount == 0 }) { ^nil };

        now = Main.elapsedTime;
        frames = this.prHistoryInOrder.select { |f| f[\time] >= (now - window) };
        if(frames.isEmpty) { ^nil };

        span = (frames.last[\time] - frames.first[\time]).max(historyRate.reciprocal);
        rmsVals = frames.collect(_[\rms]);
        activeFrames = frames.select { |f| f[\rms] > activeThresh };
        pitched = activeFrames.select { |f| f[\hasFreq] > 0.9 };

        centroidMean = if(activeFrames.notEmpty) {
            activeFrames.collect(_[\centroid]).mean
        } { 0 };
        flatnessMean = if(activeFrames.notEmpty) {
            activeFrames.collect(_[\flatness]).mean
        } { 0 };
        freqMedian = if(pitched.notEmpty) {
            pitched.collect(_[\freq]).median
        } { 0 };
        pitchStability = if(activeFrames.notEmpty) {
            pitched.size / activeFrames.size
        } { 0 };

        // Onsets within the window: delta of the cumulative count.
        // max(0) guards against analyzer restarts resetting the counter.
        onsetCount = (frames.last[\onsetCount] - frames.first[\onsetCount]).max(0);
        onsetRate = onsetCount / span;

        clumpSize = (frames.size / seriesPoints).roundUp.asInteger.max(1);
        rmsSeries = rmsVals.clump(clumpSize).collect(_.mean);
        centroidSeries = frames.collect(_[\centroid]).clump(clumpSize).collect(_.mean);

        ^(
            \window:          window,
            \span:            span,
            \frames:          frames.size,
            \active_ratio:    activeFrames.size / frames.size,
            \rms_mean:        rmsVals.mean,
            \rms_max:         rmsVals.maxItem,
            \rms_min:         rmsVals.minItem,
            \centroid_mean:   centroidMean,
            \flatness_mean:   flatnessMean,
            \freq_median:     freqMedian,
            \pitch_stability: pitchStability,
            \onset_count:     onsetCount,
            \onset_rate:      onsetRate,
            \rms_series:      rmsSeries,
            \centroid_series: centroidSeries
        )
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
    // window > 0 → windowed summary of the last N seconds (Event carries
    // \window so the callback can tell the two shapes apart); otherwise an
    // instantaneous snapshot. Falls back to snapshot if history is empty.
    *sendAudioPong { |reqId, reply, window|
        var a;
        if(window.notNil and: { window > 0 }) {
            a = this.getWindowAnalysis(window) ?? { this.getAnalysis };
        } {
            a = this.getAnalysis;
        };
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

    *handlePingAudio { |reqId, reply, window|
        if(analyzerRunning.not) {
            "CorallineAnalysis: analyzer not running — call .startAnalyzer first".warn;
            ^this
        };

        this.sendAudioPong(reqId, reply, window);
    }

    // ---- Audio clips ----

    // Write the last `duration` seconds of the master bus to a wav.
    // action is called with (path, errorString, actualDuration, sampleRate)
    // — path is nil on error, errorString is nil on success.
    *saveClip { |duration = 8, action|
        if(analyzerRunning.not or: { clipBuffer.isNil }) {
            action.value(nil, "clip recorder not running — call CorallineAnalysis.startAnalyzer first", 0, 0);
            ^this
        };

        fork {
            var sr = clipBuffer.sampleRate ?? { Server.default.sampleRate };
            var maxFrames = clipBuffer.numFrames;
            var available = ((Main.elapsedTime - clipStartTime) * sr).asInteger.min(maxFrames);
            var frames = (duration * sr).asInteger.min(available);
            var writePos, start, path;

            if(frames < (sr * 0.1)) {
                action.value(nil, "no audio captured yet — clip ring has less than 0.1s", 0, 0);
            } {
                writePos = clipPosBus.getSynchronous.asInteger.clip(0, maxFrames - 1);
                start = (writePos - frames) % maxFrames;  // sclang mod is always positive

                if(File.exists(clipDir).not) { File.mkdir(clipDir) };
                path = clipDir +/+ "clip_%_%.wav".format(
                    Date.getDate.stamp,
                    (Main.elapsedTime.frac * 1000).asInteger  // disambiguate same-second clips
                );

                this.prDumpClip(start, frames, maxFrames, sr, path, action);
            };
        };
    }

    // Unwrap the ring (possibly in two chunks around the wrap point),
    // pull to sclang, and write interleaved data as a wav.
    *prDumpClip { |start, frames, maxFrames, sr, path, action|
        var writeFile = { |data|
            var sf = SoundFile.new
                .headerFormat_("WAV")
                .sampleFormat_("int24")
                .numChannels_(2)
                .sampleRate_(sr.asInteger);
            if(sf.openWrite(path)) {
                sf.writeData(data);
                sf.close;
                "CorallineAnalysis: clip saved → % (%s)".format(path, (frames / sr).round(0.1)).postln;
                action.value(path, nil, frames / sr, sr.asInteger);
            } {
                action.value(nil, "could not open % for writing".format(path), 0, 0);
            };
        };

        if(start + frames <= maxFrames) {
            clipBuffer.loadToFloatArray(start, frames, { |data| writeFile.(data) });
        } {
            var firstChunk = maxFrames - start;
            clipBuffer.loadToFloatArray(start, firstChunk, { |dataA|
                clipBuffer.loadToFloatArray(0, frames - firstChunk, { |dataB|
                    writeFile.(dataA ++ dataB);
                });
            });
        };
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
