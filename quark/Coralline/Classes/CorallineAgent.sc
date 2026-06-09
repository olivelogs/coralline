//    CorallineAgent — Bidirectional OSC layer for agent ↔ SuperCollider.
//
//    Handles:
//    - /coralline/play        → semantic play (routes through CorallineSemantics → SuperDirt)
//    - /coralline/raw         → bypass semantics, raw SuperDirt params
//    - /coralline/loop/start  → start pattern loop via JITLib (Pdef)
//    - /coralline/loop/stop   → stop a named loop
//    - /coralline/loop/modify → hot-swap a running loop
//    - /coralline/phrase      → one-shot phrase with gradient params
//    - /coralline/ping/state  → request state (pong)
//    - /coralline/ping/audio  → request audio analysis snapshot (pong via CorallineAnalysis)
//    - /coralline/listen/start → start continuous audio analysis pong
//    - /coralline/listen/stop  → stop continuous audio analysis pong
//    - /coralline/pong/        → outbound analysis/state data
//
//    /coralline/play supports a "|" separator for mixing semantic + raw params:
//        /coralline/play supervibe 0 brightness 0.7 warmth 0.4 | gain 1.2 krush 3
//
//    CRITICAL: n uses SuperDirt's note system, NOT MIDI!
//      n 0 = C5 (middle C), n -12 = C4, n 12 = C6
//      To convert from MIDI: n = midinote - 60
//
//    If using batch_send_osc, leave type_tags blank for auto-detect.
//
//    Setup:
//        // After SuperDirt is running:
//        CorallineAgent.start;
//
//        // To stop:
//        CorallineAgent.stop;
//
//        // With custom ports:
//        CorallineAgent.start(listenPort: 57120, replyPort: 9601);
//
//    March 2026 — Olive + Claude

CorallineAgent {

    classvar <oscResponders;    // Array of OSCdef responders
    classvar <replyAddr;        // NetAddr for sending pong replies
    classvar <loops;            // Dictionary of active Pdef loops
    classvar <isRunning;

    // Synth aliases: maps virtual synth names to [realSynthName, extraParams]
    // e.g. superfm_v3 → superfm with voice:3 injected into raw params.
    // Semantic resolution uses the alias name (so superfm_v3 gets its own mappings),
    // but SuperDirt receives the real synth name + injected params.
    classvar <synthAliases;

    *initClass {
        oscResponders = [];
        loops = IdentityDictionary.new;
        isRunning = false;

        // Register synth aliases
        synthAliases = IdentityDictionary[
            \superfm_v1 -> (\realSynth: \superfm, \inject: (\voice: 1)),
            \superfm_v2 -> (\realSynth: \superfm, \inject: (\voice: 2)),
            \superfm_v3 -> (\realSynth: \superfm, \inject: (\voice: 3)),
            \superfm_v4 -> (\realSynth: \superfm, \inject: (\voice: 4)),
        ];
    }

    // Resolve a synth alias. Returns an Event:
    //   \semanticName — name for CorallineSemantics lookup (the alias)
    //   \dirtName     — name for SuperDirt (the real SynthDef)
    //   \inject       — extra params to inject into SuperDirt args
    // If not an alias, semanticName == dirtName and inject is empty.
    *resolveAlias { |synthName|
        var alias = synthAliases[synthName];
        if(alias.notNil) {
            ^(\semanticName: synthName, \dirtName: alias[\realSynth], \inject: alias[\inject])
        } {
            ^(\semanticName: synthName, \dirtName: synthName, \inject: ())
        }
    }

    *start { |listenPort = 57120, replyPort = 9601, replyHost = "127.0.0.1"|
        if(isRunning) {
            "CorallineAgent: already running. Call .stop first.".warn;
            ^this
        };

        replyAddr = NetAddr(replyHost, replyPort);

        this.registerResponders;

        // Set up audio analysis with pong callback
        // reqId is threaded through from ping → pong for request matching
        CorallineAnalysis.pongCallback = { |a, reqId, reply|
            var msg = ['/coralline/pong/audio',
                "rms",       a[\rms],
                "centroid",  a[\centroid],
                "flatness",  a[\flatness],
                "freq",      a[\freq],
                "hasFreq",   a[\hasFreq],
                "onsetRate", a[\onsetRate]
            ];
            if(reqId.notNil) { msg = msg ++ ["reqId", reqId] };
            (reply ? replyAddr).sendMsg(*msg);
        };
        CorallineAnalysis.startAnalyzer;

        isRunning = true;
        "CorallineAgent: listening on port %, fallback reply %:% (pings carry their own reply port)".format(
            listenPort, replyHost, replyPort
        ).postln;
    }

    *stop {
        CorallineAnalysis.stopListening;
        CorallineAnalysis.stopAnalyzer;
        oscResponders.do { |r| r.free };
        oscResponders = [];
        // Stop all loops
        loops.keysValuesDo { |name, pdef|
            pdef.stop;
            pdef.clear;
        };
        loops = IdentityDictionary.new;
        isRunning = false;
        "CorallineAgent: stopped.".postln;
    }

    *registerResponders {

        // ==========================================
        // /coralline/play — Semantic play
        // ==========================================
        // Format:
        //   /coralline/play synthName n [dim1 val1 ...] [| rawKey1 rawVal1 ...]
        // The "|" separator divides semantic params (resolved via CorallineSemantics)
        // from raw SuperDirt params (passed through as-is, e.g. effects).
        //
        // n uses SuperDirt's note system, NOT MIDI!
        //   n 0 = C5 (middle C), n -12 = C4, n 12 = C6
        //   To convert from MIDI: n = midinote - 60

        oscResponders = oscResponders.add(
            OSCdef(\corallinePlay, { |msg, time, addr, recvPort|
                this.handlePlay(msg);
            }, '/coralline/play')
        );

        // ==========================================
        // /coralline/raw — Raw SuperDirt passthrough
        // ==========================================
        // Same format as /dirt/play but via coralline's routing
        oscResponders = oscResponders.add(
            OSCdef(\corallineRaw, { |msg, time, addr, recvPort|
                this.handleRaw(msg);
            }, '/coralline/raw')
        );

        // ==========================================
        // /coralline/loop/start — Start a named loop
        // ==========================================
        // /coralline/loop/start loopName synthName "0 3 7 12" cycleDur [dim1 val1 ...] [| raw1 val1 ...]
        oscResponders = oscResponders.add(
            OSCdef(\corallineLoopStart, { |msg, time, addr, recvPort|
                this.handleLoopStart(msg);
            }, '/coralline/loop/start')
        );

        // ==========================================
        // /coralline/loop/modify — Hot-swap a running loop
        // ==========================================
        // Same format as loop/start — redefines the Pdef in place
        oscResponders = oscResponders.add(
            OSCdef(\corallineLoopModify, { |msg, time, addr, recvPort|
                this.handleLoopStart(msg);  // same logic, Pdef handles hot-swap
            }, '/coralline/loop/modify')
        );

        // ==========================================
        // /coralline/loop/stop — Stop a named loop
        // ==========================================
        oscResponders = oscResponders.add(
            OSCdef(\corallineLoopStop, { |msg, time, addr, recvPort|
                var loopName = msg[1].asSymbol;
                this.stopLoop(loopName);
            }, '/coralline/loop/stop')
        );

        // ==========================================
        // /coralline/phrase — One-shot phrase with gradient
        // ==========================================
        // /coralline/phrase synthName "0 3 7" 0.5 [dim1 val1 [val2]] ... [| raw1 val1 [val2] ...]
        // One value after a key = blanket for all notes
        // Two values after a key = gradient (start → end) interpolated across phrase
        // dur is seconds per note
        oscResponders = oscResponders.add(
            OSCdef(\corallinePhrase, { |msg, time, addr, recvPort|
                this.handlePhrase(msg);
            }, '/coralline/phrase')
        );

        // ==========================================
        // /coralline/ping/state — Request current state
        // ==========================================
        oscResponders = oscResponders.add(
            OSCdef(\corallinePingState, { |msg, time, addr, recvPort|
                var reqId = if(msg.size > 1) { msg[1].asString } { nil };
                var reply = this.replyTo(if(msg.size > 2) { msg[2].asInteger } { nil });
                this.handlePingState(reqId, reply);
            }, '/coralline/ping/state')
        );

        // ==========================================
        // /coralline/ping/audio — Request audio analysis snapshot
        // ==========================================
        // Returns /coralline/pong/audio with rms, centroid, flatness, freq, hasFreq
        oscResponders = oscResponders.add(
            OSCdef(\corallinePingAudio, { |msg, time, addr, recvPort|
                var reqId = if(msg.size > 1) { msg[1].asString } { nil };
                var reply = this.replyTo(if(msg.size > 2) { msg[2].asInteger } { nil });
                CorallineAnalysis.handlePingAudio(reqId, reply);
            }, '/coralline/ping/audio')
        );

        // ==========================================
        // /coralline/listen/start — Continuous audio pong
        // ==========================================
        // Optional arg: rate in Hz (default: 4)
        //   /coralline/listen/start 8  → 8 pongs per second
        oscResponders = oscResponders.add(
            OSCdef(\corallineListenStart, { |msg, time, addr, recvPort|
                var rate = if(msg.size > 1) { msg[1].asFloat } { CorallineAnalysis.listenRate };
                var reply = this.replyTo(if(msg.size > 2) { msg[2].asInteger } { nil });
                CorallineAnalysis.startListening(rate, reply);
            }, '/coralline/listen/start')
        );

        // ==========================================
        // /coralline/listen/stop — Stop continuous pong
        // ==========================================
        oscResponders = oscResponders.add(
            OSCdef(\corallineListenStop, { |msg, time, addr, recvPort|
                CorallineAnalysis.stopListening;
            }, '/coralline/listen/stop')
        );

        "CorallineAgent: responders registered.".postln;
    }

    // ---- Play handlers ----

    *handlePlay { |msg|
        var synthName, note, semantics, rawParams, resolved, args, pipeIdx;
        var aliasInfo, semanticName, dirtName, injectParams;

        // msg format: ['/coralline/play', synthName, n, key1, val1, ... | rawKey1, rawVal1, ...]
        if(msg.size < 3) {
            "CorallineAgent: /coralline/play needs at least synthName and n".warn;
            ^this
        };

        synthName = msg[1].asSymbol;
        note = msg[2].asFloat;  // this is 'n', not MIDI

        // Resolve synth alias (e.g. superfm_v3 → superfm + voice:3)
        aliasInfo = this.resolveAlias(synthName);
        semanticName = aliasInfo[\semanticName];
        dirtName = aliasInfo[\dirtName];
        injectParams = aliasInfo[\inject];

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

        // Resolve semantic params through CorallineSemantics (using alias name)
        resolved = CorallineSemantics.resolveAll(semanticName, semantics);

        // Build SuperDirt args — use dirtName (real SynthDef name), 'n' not 'midinote'
        args = ["s", dirtName.asString, "n", note];

        // Add resolved semantic params
        resolved.keysValuesDo { |param, val|
            args = args ++ [param.asString, val];
        };

        // Add injected alias params (e.g. voice:3 for superfm_v3)
        injectParams.keysValuesDo { |param, val|
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

        "CorallineAgent: play % n % → sem:% raw:% alias:%".format(
            semanticName, note, resolved, rawParams,
            if(dirtName != semanticName) { dirtName } { "none" }
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
        // msg: ['/coralline/loop/start', loopName, synthName, notePattern, cycleDur, ...]
        // Optional trailing args: semantic and/or raw params (with | separator)
        //   /coralline/loop/start myloop supersaw "0 3 7" 2.0 brightness 0.7 | krush 3
        var loopName, synthName, noteStr, cycleDur, notes, dur;
        var semantics, rawParams, resolved, extraPairs, pipeIdx;
        var aliasInfo, semanticName, dirtName, injectParams;

        if(msg.size < 5) {
            "CorallineAgent: loop needs loopName, synthName, notePattern, cycleDur".warn;
            ^this
        };

        loopName = msg[1].asSymbol;
        synthName = msg[2].asSymbol;
        noteStr = msg[3].asString;
        cycleDur = msg[4].asFloat;

        // Resolve synth alias (e.g. superfm_v3 → superfm + voice:3)
        aliasInfo = this.resolveAlias(synthName);
        semanticName = aliasInfo[\semanticName];
        dirtName = aliasInfo[\dirtName];
        injectParams = aliasInfo[\inject];

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

        // Resolve semantic params (using alias name)
        resolved = if(semantics.size > 0) {
            CorallineSemantics.resolveAll(semanticName, semantics);
        } { () };

        // Build extra Pbind pairs from resolved + injected alias + raw
        extraPairs = [];
        resolved.keysValuesDo { |param, val|
            extraPairs = extraPairs ++ [param, val];
        };
        injectParams.keysValuesDo { |param, val|
            extraPairs = extraPairs ++ [param, val];
        };
        rawParams.keysValuesDo { |param, val|
            extraPairs = extraPairs ++ [param, val];
        };

        // Create or replace a Pdef (JITLib hot-swaps naturally)
        // Use dirtName (real SynthDef name) for SuperDirt
        Pdef(loopName,
            Pbind(
                \type, \dirt,
                \s, dirtName.asString,
                \n, Pseq(notes, inf),
                \dur, dur,
                \gain, rawParams[\gain] ? 1.0,
                \orbit, 0,
                *extraPairs
            )
        ).play;

        loops[loopName] = Pdef(loopName);

        "CorallineAgent: loop '%' started — % playing % (cycle %s, sem:% raw:% alias:%)".format(
            loopName, semanticName, notes, cycleDur, resolved, rawParams,
            if(dirtName != semanticName) { dirtName } { "none" }
        ).postln;
    }

    *stopLoop { |loopName|
        var pdef = loops[loopName];
        if(pdef.notNil) {
            pdef.stop;
            pdef.clear;
            loops.removeAt(loopName);
            "CorallineAgent: loop '%' stopped.".format(loopName).postln;
        } {
            "CorallineAgent: no loop named '%'".format(loopName).warn;
        };
    }

    // ---- Phrase handler ----

    *handlePhrase { |msg|
        // msg: ['/coralline/phrase', synthName, notePattern, durPerNote, ...params...]
        var synthName, noteStr, durPerNote, notes, numNotes;
        var semGradients, rawGradients, pipeIdx;
        var aliasInfo, semanticName, dirtName, injectParams;

        if(msg.size < 4) {
            "CorallineAgent: /coralline/phrase needs synthName, notePattern, durPerNote".warn;
            ^this
        };

        synthName = msg[1].asSymbol;
        noteStr = msg[2].asString;
        durPerNote = msg[3].asFloat;

        // Resolve synth alias (e.g. superfm_v3 → superfm + voice:3)
        aliasInfo = this.resolveAlias(synthName);
        semanticName = aliasInfo[\semanticName];
        dirtName = aliasInfo[\dirtName];
        injectParams = aliasInfo[\inject];

        notes = noteStr.split($ ).collect(_.asFloat);
        numNotes = notes.size;

        if(numNotes == 0) {
            "CorallineAgent: empty note pattern".warn;
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

                // Resolve through CorallineSemantics (using alias name)
                resolved = if(semantics.size > 0) {
                    CorallineSemantics.resolveAll(semanticName, semantics);
                } { () };

                // Interpolate raw gradients at position t
                rawParams = this.prInterpolateGradients(rawGradients, t);

                // Build SuperDirt args (using dirtName for real SynthDef)
                args = ["s", dirtName.asString, "n", note];

                resolved.keysValuesDo { |param, val|
                    args = args ++ [param.asString, val];
                };

                // Add injected alias params (e.g. voice:3 for superfm_v3)
                injectParams.keysValuesDo { |param, val|
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

            "CorallineAgent: phrase complete — % % notes, %s/note".format(
                semanticName, numNotes, durPerNote
            ).postln;
        };

        "CorallineAgent: phrase started — % % notes, %s/note sem:% raw:% alias:%".format(
            semanticName, numNotes, durPerNote, semGradients.keys, rawGradients.keys,
            if(dirtName != semanticName) { dirtName } { "none" }
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

    // ---- Ping/Pong handlers ----

    // Resolve a client-supplied reply port to a NetAddr. Each client sends its
    // own ephemeral port in the ping, so concurrent clients each get their pong.
    // Falls back to the fixed replyAddr when a ping omits the port (back-compat).
    *replyTo { |replyPort|
        ^if(replyPort.notNil and: { replyPort > 0 }) {
            NetAddr("127.0.0.1", replyPort)
        } {
            replyAddr
        }
    }

    *handlePingState { |reqId, reply|
        var activeLoops, msg;

        activeLoops = loops.keys.asArray;

        // Build reply without loop_names when there are no loops.
        // osc-min chokes on empty OSC strings.
        msg = ['/coralline/pong/state',
            "loops", activeLoops.size,
            "running", isRunning.asInteger
        ];

        if(activeLoops.notEmpty) {
            msg = msg ++ ["loop_names", activeLoops.join(",")];
        };

        if(reqId.notNil) { msg = msg ++ ["reqId", reqId] };

        (reply ? replyAddr).sendMsg(*msg);

        "CorallineAgent: pong/state sent → % active loops".format(activeLoops.size).postln;
    }

    // ---- Convenience ----

    *activeLoops {
        ^loops.keys.asArray
    }
}
