//    ChimeAgent — Bidirectional OSC layer for agent ↔ SuperCollider.
//
//    Handles:
//    - /chime/play        → semantic play (routes through ChimeSemantics → SuperDirt)
//    - /chime/raw         → bypass semantics, raw SuperDirt params
//    - /chime/loop/start  → start pattern loop via JITLib (Pdef)
//    - /chime/loop/stop   → stop a named loop
//    - /chime/loop/modify → hot-swap a running loop
//    - /chime/phrase      → one-shot phrase with gradient params
//    - /chime/ping/state  → request state (pong)
//    - /chime/ping/audio  → request audio analysis snapshot (pong)
//    - /chime/listen/start → start continuous audio analysis pong
//    - /chime/listen/stop  → stop continuous audio analysis pong
//    - /chime/pong/        → outbound analysis/state data
//
//    /chime/play supports a "|" separator for mixing semantic + raw params:
//        /chime/play supervibe 0 brightness 0.7 warmth 0.4 | gain 1.2 krush 3
//
//    CRITICAL: n uses SuperDirt's note system, NOT MIDI!
//      n 0 = C5 (middle C), n -12 = C4, n 12 = C6
//      To convert from MIDI: n = midinote - 60
//
//    If using batch_send_osc, leave type_tags blank for auto-detect.
//
//    Setup:
//        // After SuperDirt is running:
//        ChimeAgent.start;
//
//        // To stop:
//        ChimeAgent.stop;
//
//        // With custom ports:
//        ChimeAgent.start(listenPort: 57120, replyPort: 9000);
//
//    March 2026 — Olive + Claude

ChimeAgent {

    classvar <oscResponders;    // Array of OSCdef responders
    classvar <replyAddr;        // NetAddr for sending pong replies
    classvar <loops;            // Dictionary of active Pdef loops
    classvar <isRunning;

    // Audio analysis
    classvar <analyzerSynth;    // Synth running FFT analysis on main output
    classvar <analyzerBuses;    // IdentityDictionary of control buses for analysis metrics
    classvar <analyzerRunning;  // Boolean
    classvar <listenTask;       // SkipJack for continuous pong mode
    classvar <listenRate;       // Pong rate in Hz when listening

    *initClass {
        oscResponders = [];
        loops = IdentityDictionary.new;
        isRunning = false;
        analyzerSynth = nil;
        analyzerBuses = nil;
        analyzerRunning = false;
        listenTask = nil;
        listenRate = 4;  // default: 4 pongs per second
    }

    *start { |listenPort = 57120, replyPort = 9501, replyHost = "127.0.0.1"|
        if(isRunning) {
            "ChimeAgent: already running. Call .stop first.".warn;
            ^this
        };

        replyAddr = NetAddr(replyHost, replyPort);

        this.registerResponders;
        this.startAnalyzer;

        isRunning = true;
        "ChimeAgent: listening on port %, replies to %:%".format(
            listenPort, replyHost, replyPort
        ).postln;
    }

    *stop {
        this.stopListening;
        this.stopAnalyzer;
        oscResponders.do { |r| r.free };
        oscResponders = [];
        // Stop all loops
        loops.keysValuesDo { |name, pdef|
            pdef.stop;
            pdef.clear;
        };
        loops = IdentityDictionary.new;
        isRunning = false;
        "ChimeAgent: stopped.".postln;
    }

    *registerResponders {

        // ==========================================
        // /chime/play — Semantic play
        // ==========================================
        // Format:
        //   /chime/play synthName n [dim1 val1 ...] [| rawKey1 rawVal1 ...]
        // The "|" separator divides semantic params (resolved via ChimeSemantics)
        // from raw SuperDirt params (passed through as-is, e.g. effects).
        //
        // n uses SuperDirt's note system, NOT MIDI!
        //   n 0 = C5 (middle C), n -12 = C4, n 12 = C6
        //   To convert from MIDI: n = midinote - 60

        oscResponders = oscResponders.add(
            OSCdef(\chimePlay, { |msg, time, addr, recvPort|
                this.handlePlay(msg);
            }, '/chime/play')
        );

        // ==========================================
        // /chime/raw — Raw SuperDirt passthrough
        // ==========================================
        // Same format as /dirt/play but via chime's routing
        oscResponders = oscResponders.add(
            OSCdef(\chimeRaw, { |msg, time, addr, recvPort|
                this.handleRaw(msg);
            }, '/chime/raw')
        );

        // ==========================================
        // /chime/loop/start — Start a named loop
        // ==========================================
        // /chime/loop/start loopName synthName "0 3 7 12" cycleDur [dim1 val1 ...] [| raw1 val1 ...]
        oscResponders = oscResponders.add(
            OSCdef(\chimeLoopStart, { |msg, time, addr, recvPort|
                this.handleLoopStart(msg);
            }, '/chime/loop/start')
        );

        // ==========================================
        // /chime/loop/modify — Hot-swap a running loop
        // ==========================================
        // Same format as loop/start — redefines the Pdef in place
        oscResponders = oscResponders.add(
            OSCdef(\chimeLoopModify, { |msg, time, addr, recvPort|
                this.handleLoopStart(msg);  // same logic, Pdef handles hot-swap
            }, '/chime/loop/modify')
        );

        // ==========================================
        // /chime/loop/stop — Stop a named loop
        // ==========================================
        oscResponders = oscResponders.add(
            OSCdef(\chimeLoopStop, { |msg, time, addr, recvPort|
                var loopName = msg[1].asSymbol;
                this.stopLoop(loopName);
            }, '/chime/loop/stop')
        );

		// ==========================================
        // /chime/phrase — One-shot phrase with gradient
        // ==========================================
        // /chime/phrase synthName "0 3 7" 0.5 [dim1 val1 [val2]] ... [| raw1 val1 [val2] ...]
        // One value after a key = blanket for all notes
        // Two values after a key = gradient (start → end) interpolated across phrase
        // dur is seconds per note
        oscResponders = oscResponders.add(
            OSCdef(\chimePhrase, { |msg, time, addr, recvPort|
                this.handlePhrase(msg);
            }, '/chime/phrase')
        );
		
        // ==========================================
        // /chime/ping/state — Request current state
        // ==========================================
        oscResponders = oscResponders.add(
            OSCdef(\chimePingState, { |msg, time, addr, recvPort|
                this.handlePingState;
            }, '/chime/ping/state')
        );

        // ==========================================
        // /chime/ping/audio — Request audio analysis snapshot
        // ==========================================
        // Returns /chime/pong/audio with rms, centroid, flatness, freq, hasFreq
        oscResponders = oscResponders.add(
            OSCdef(\chimePingAudio, { |msg, time, addr, recvPort|
                this.handlePingAudio;
            }, '/chime/ping/audio')
        );

        // ==========================================
        // /chime/listen/start — Continuous audio pong
        // ==========================================
        // Optional arg: rate in Hz (default: 4)
        //   /chime/listen/start 8  → 8 pongs per second
        oscResponders = oscResponders.add(
            OSCdef(\chimeListenStart, { |msg, time, addr, recvPort|
                var rate = if(msg.size > 1) { msg[1].asFloat } { listenRate };
                this.startListening(rate);
            }, '/chime/listen/start')
        );

        // ==========================================
        // /chime/listen/stop — Stop continuous pong
        // ==========================================
        oscResponders = oscResponders.add(
            OSCdef(\chimeListenStop, { |msg, time, addr, recvPort|
                this.stopListening;
            }, '/chime/listen/stop')
        );

        "ChimeAgent: responders registered.".postln;
    }

    // ---- Play handlers ----

    *handlePlay { |msg|
        var synthName, note, semantics, rawParams, resolved, args, pipeIdx;

        // msg format: ['/chime/play', synthName, n, key1, val1, ... | rawKey1, rawVal1, ...]
        if(msg.size < 3) {
            "ChimeAgent: /chime/play needs at least synthName and n".warn;
            ^this
        };

        synthName = msg[1].asSymbol;
        note = msg[2].asFloat;  // this is 'n', not MIDI

        // Find pipe separator index (if any)
        pipeIdx = nil;
        (3..msg.size-1).do { |i|
            if(msg[i].asString == "|") { pipeIdx = i };
        };

        // Parse semantic key-value pairs (before pipe or all if no pipe)
        semantics = IdentityDictionary.new;
        rawParams = IdentityDictionary.new;

        if(pipeIdx.notNil) {
            // Semantic params: indices 3 to pipeIdx-1
            (3, 5 .. pipeIdx - 2).do { |i|
                if(i+1 < pipeIdx) {
                    semantics[msg[i].asSymbol] = msg[i+1].asFloat;
                };
            };
            // Raw params: indices pipeIdx+1 to end
            (pipeIdx+1, pipeIdx+3 .. msg.size - 2).do { |i|
                if(i+1 < msg.size) {
                    rawParams[msg[i].asSymbol] = msg[i+1].asFloat;
                };
            };
        } {
            // No pipe — everything is semantic
            (3, 5 .. msg.size - 2).do { |i|
                if(i+1 < msg.size) {
                    semantics[msg[i].asSymbol] = msg[i+1].asFloat;
                };
            };
        };

        // Resolve semantic params through ChimeSemantics
        resolved = ChimeSemantics.resolveAll(synthName, semantics);

        // Build SuperDirt args — use 'n' not 'midinote'
        args = ["s", synthName.asString, "n", note];

        // Add resolved semantic params
        resolved.keysValuesDo { |param, val|
            args = args ++ [param.asString, val];
        };

        // Add raw params (effects, gain, pan, etc.)
        rawParams.keysValuesDo { |param, val|
            args = args ++ [param.asString, val];
        };

        // Add gain default if not specified anywhere
        if(resolved[\gain].isNil and: { rawParams[\gain].isNil }) {
            args = args ++ ["gain", 1.0];
        };

        args = args ++ ["orbit", 0];

        this.sendToDirt(args);

        "ChimeAgent: play % n % → sem:% raw:%".format(
            synthName, note, resolved, rawParams
        ).postln;
    }

    *handleRaw { |msg|
        // Strip the address, forward everything else as-is to /dirt/play
        var args = msg[1..];
        this.sendToDirt(args);
    }

    *sendToDirt { |args|
        // Send to SuperDirt via its OSC interface
        // SuperDirt listens on the same port we're on,
        // so we send to localhost:57120
        var dirt = NetAddr("127.0.0.1", 57120);
        dirt.sendMsg('/dirt/play', *args);
    }

    // ---- Loop handlers (JITLib) ----

    *handleLoopStart { |msg|
        // msg: ['/chime/loop/start', loopName, synthName, notePattern, cycleDur, ...]
        // Optional trailing args: semantic and/or raw params (with | separator)
        //   /chime/loop/start myloop supersaw "0 3 7" 2.0 brightness 0.7 | krush 3
        var loopName, synthName, noteStr, cycleDur, notes, dur;
        var semantics, rawParams, resolved, extraPairs, pipeIdx;

        if(msg.size < 5) {
            "ChimeAgent: loop needs loopName, synthName, notePattern, cycleDur".warn;
            ^this
        };

        loopName = msg[1].asSymbol;
        synthName = msg[2].asSymbol;
        noteStr = msg[3].asString;
        cycleDur = msg[4].asFloat;

        // Parse note pattern: "0 3 7 12" → [0, 3, 7, 12]
        notes = noteStr.split($ ).collect(_.asFloat);
        dur = cycleDur / notes.size;

        // Parse optional semantic + raw params (from index 5 onward)
        semantics = IdentityDictionary.new;
        rawParams = IdentityDictionary.new;

        if(msg.size > 5) {
            pipeIdx = nil;
            (5..msg.size-1).do { |i|
                if(msg[i].asString == "|") { pipeIdx = i };
            };

            if(pipeIdx.notNil) {
                (5, 7 .. pipeIdx - 2).do { |i|
                    if(i+1 < pipeIdx) {
                        semantics[msg[i].asSymbol] = msg[i+1].asFloat;
                    };
                };
                (pipeIdx+1, pipeIdx+3 .. msg.size - 2).do { |i|
                    if(i+1 < msg.size) {
                        rawParams[msg[i].asSymbol] = msg[i+1].asFloat;
                    };
                };
            } {
                (5, 7 .. msg.size - 2).do { |i|
                    if(i+1 < msg.size) {
                        semantics[msg[i].asSymbol] = msg[i+1].asFloat;
                    };
                };
            };
        };

        // Resolve semantic params
        resolved = if(semantics.size > 0) {
            ChimeSemantics.resolveAll(synthName, semantics);
        } { () };

        // Build extra Pbind pairs from resolved + raw
        extraPairs = [];
        resolved.keysValuesDo { |param, val|
            extraPairs = extraPairs ++ [param, val];
        };
        rawParams.keysValuesDo { |param, val|
            extraPairs = extraPairs ++ [param, val];
        };

        // Create or replace a Pdef (JITLib hot-swaps naturally)
        Pdef(loopName,
            Pbind(
                \type, \dirt,
                \s, synthName.asString,
                \n, Pseq(notes, inf),
                \dur, dur,
                \gain, rawParams[\gain] ? 1.0,
                \orbit, 0,
                *extraPairs
            )
        ).play;

        loops[loopName] = Pdef(loopName);

        "ChimeAgent: loop '%' started — % playing % (cycle %s, sem:% raw:%)".format(
            loopName, synthName, notes, cycleDur, resolved, rawParams
        ).postln;
    }

    *stopLoop { |loopName|
        var pdef = loops[loopName];
        if(pdef.notNil) {
            pdef.stop;
            pdef.clear;
            loops.removeAt(loopName);
            "ChimeAgent: loop '%' stopped.".format(loopName).postln;
        } {
            "ChimeAgent: no loop named '%'".format(loopName).warn;
        };
    }
	
	// ---- Phrase handler ----

    *handlePhrase { |msg|
        // msg: ['/chime/phrase', synthName, notePattern, durPerNote, ...params...]
        var synthName, noteStr, durPerNote, notes, numNotes;
        var semGradients, rawGradients, pipeIdx;
        var paramRange;

        if(msg.size < 4) {
            "ChimeAgent: /chime/phrase needs synthName, notePattern, durPerNote".warn;
            ^this
        };

        synthName = msg[1].asSymbol;
        noteStr = msg[2].asString;
        durPerNote = msg[3].asFloat;

        notes = noteStr.split($ ).collect(_.asFloat);
        numNotes = notes.size;

        if(numNotes == 0) {
            "ChimeAgent: empty note pattern".warn;
            ^this
        };

        // Find pipe separator
        pipeIdx = nil;
        (4..msg.size-1).do { |i|
            if(msg[i].asString == "|") { pipeIdx = i };
        };

        // Parse gradient params from semantic section
        semGradients = if(pipeIdx.notNil) {
            this.prParseGradientParams(msg, 4, pipeIdx - 1);
        } {
            this.prParseGradientParams(msg, 4, msg.size - 1);
        };

        // Parse gradient params from raw section (after pipe)
        rawGradients = if(pipeIdx.notNil) {
            this.prParseGradientParams(msg, pipeIdx + 1, msg.size - 1);
        } {
            IdentityDictionary.new
        };

        // Schedule the phrase in a Routine (SC-side timing)
        fork {
            notes.do { |note, idx|
                var t, semantics, rawParams, resolved, args;

                // Interpolation position: 0.0 for first note, 1.0 for last
                t = if(numNotes > 1) { idx / (numNotes - 1) } { 0.0 };

                // Interpolate semantic gradients at position t
                semantics = this.prInterpolateGradients(semGradients, t);

                // Resolve through ChimeSemantics
                resolved = if(semantics.size > 0) {
                    ChimeSemantics.resolveAll(synthName, semantics);
                } { () };

                // Interpolate raw gradients at position t
                rawParams = this.prInterpolateGradients(rawGradients, t);

                // Build SuperDirt args
                args = ["s", synthName.asString, "n", note];

                resolved.keysValuesDo { |param, val|
                    args = args ++ [param.asString, val];
                };

                rawParams.keysValuesDo { |param, val|
                    args = args ++ [param.asString, val];
                };

                if(resolved[\gain].isNil and: { rawParams[\gain].isNil }) {
                    args = args ++ ["gain", 1.0];
                };

                args = args ++ ["orbit", 0];

                this.sendToDirt(args);

                // Wait before next note (except after last)
                if(idx < (numNotes - 1)) {
                    durPerNote.wait;
                };
            };

            "ChimeAgent: phrase complete — % % notes, %s/note".format(
                synthName, numNotes, durPerNote
            ).postln;
        };

        "ChimeAgent: phrase started — % % notes, %s/note sem:% raw:%".format(
            synthName, numNotes, durPerNote, semGradients.keys, rawGradients.keys
        ).postln;
    }

    // Parse key-value pairs where each key may have 1 value (blanket) or 2 (gradient).
    // Returns IdentityDictionary of key -> [values]
    //   [0.5]     = blanket
    //   [0.3, 0.8] = gradient from start to end
    *prParseGradientParams { |msg, startIdx, endIdx|
        var result = IdentityDictionary.new;
        var i = startIdx;
        var currentKey, values;

        while { i <= endIdx } {
            var item = msg[i];

            if(item.isKindOf(String) or: { item.isKindOf(Symbol) }) {
                // Save previous key if we had one
                if(currentKey.notNil and: { values.size > 0 }) {
                    result[currentKey] = values;
                };
                // Start new key
                currentKey = item.asSymbol;
                values = [];
            } {
                // It's a number — add to current key's values
                if(currentKey.notNil) {
                    values = values.add(item.asFloat);
                };
            };

            i = i + 1;
        };

        // Don't forget the last key
        if(currentKey.notNil and: { values.size > 0 }) {
            result[currentKey] = values;
        };

        ^result
    }

    // Interpolate gradient params at position t (0.0–1.0).
    // Returns IdentityDictionary of key -> interpolated value.
    *prInterpolateGradients { |gradients, t|
        var result = IdentityDictionary.new;

        gradients.keysValuesDo { |key, values|
            result[key] = case
                { values.size == 1 } { values[0] }                          // blanket
                { values.size >= 2 } { values[0].blend(values[1], t) }      // gradient
                { true } { 0 };                                             // shouldn't happen
        };

        ^result
    }

    // ---- Audio analyzer ----

    *startAnalyzer {
        if(analyzerRunning) {
            "ChimeAgent: analyzer already running.".warn;
            ^this
        };

        fork {
            // Define the analyzer SynthDef
            SynthDef(\chimeAnalyzer, {
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
            analyzerSynth = Synth(\chimeAnalyzer, [
                \ampBus,      analyzerBuses[\amp].index,
                \freqBus,     analyzerBuses[\freq].index,
                \hasFreqBus,  analyzerBuses[\hasFreq].index,
                \centroidBus, analyzerBuses[\centroid].index,
                \flatnessBus, analyzerBuses[\flatness].index,
            ], target: Server.default.defaultGroup, addAction: \addToTail);

            analyzerRunning = true;
            "ChimeAgent: audio analyzer started (listening on bus 0).".postln;
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
        "ChimeAgent: audio analyzer stopped.".postln;
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

    // Send a single audio analysis pong.
    *sendAudioPong {
        var a = this.getAnalysis;

        replyAddr.sendMsg('/chime/pong/audio',
            "rms",      a[\rms],
            "centroid", a[\centroid],
            "flatness", a[\flatness],
            "freq",     a[\freq],
            "hasFreq",  a[\hasFreq]
        );
    }

    // Start continuous listening — sends pong at the given rate.
    *startListening { |rate|
        rate = rate ?? { listenRate };
        listenRate = rate;

        this.stopListening;  // clear any existing

        if(analyzerRunning.not) {
            "ChimeAgent: can't listen — analyzer not running.".warn;
            ^this
        };

        listenTask = SkipJack(
            { this.sendAudioPong },
            dt: rate.reciprocal,
            stopTest: { analyzerRunning.not },
            name: "ChimeAudioPong"
        );

        "ChimeAgent: listening at % Hz → /chime/pong/audio".format(rate).postln;
    }

    // Stop continuous listening.
    *stopListening {
        if(listenTask.notNil) {
            listenTask.stop;
            listenTask = nil;
            "ChimeAgent: stopped listening.".postln;
        };
    }

    // ---- Ping/Pong handlers ----

    *handlePingAudio {
        if(analyzerRunning.not) {
            "ChimeAgent: analyzer not running — call .startAnalyzer first".warn;
            ^this
        };

        this.sendAudioPong;

        // Quiet log — this gets called a lot in listen mode
        // "ChimeAgent: pong/audio sent".postln;
    }

    *handlePingState {
        var activeLoops, msg;

        activeLoops = loops.keys.asArray;

        // Reply with state
        replyAddr.sendMsg('/chime/pong/state',
            "loops", activeLoops.size,
            "loop_names", activeLoops.join(","),
            "running", isRunning.asInteger
        );

        "ChimeAgent: pong/state sent → % active loops".format(activeLoops.size).postln;
    }

    // ---- Convenience ----

    *activeLoops {
        ^loops.keys.asArray
    }

    // Quick check: what does the room sound like right now?
    *hear {
        var a = this.getAnalysis;
        "=== ChimeAgent hears ===".postln;
        "  rms:      %  (loudness)".format(a[\rms].round(0.001)).postln;
        "  centroid: % Hz  (brightness)".format(a[\centroid].round(1)).postln;
        "  flatness: %  (texture: 0=tone, 1=noise)".format(a[\flatness].round(0.001)).postln;
        "  freq:     % Hz  (pitch)".format(a[\freq].round(1)).postln;
        "  hasFreq:  %  (pitch confidence)".format(a[\hasFreq].round(0.01)).postln;
        ^a
    }
}
        