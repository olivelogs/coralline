//    ChimeAgent — Bidirectional OSC layer for agent ↔ SuperCollider.
//
//    Handles:
//    - /chime/play      → semantic play (routes through ChimeSemantics → SuperDirt)
//    - /chime/raw       → bypass semantics, raw SuperDirt params
//    - /chime/loop/start → start pattern loop via JITLib (Pdef)
//    - /chime/loop/stop  → stop a named loop
//    - /chime/loop/modify → hot-swap a running loop
//    - /chime/ping/state → request state (pong)
//    - /chime/pong/      → outbound analysis/state data
//
//    /chime/play supports a "|" separator for mixing semantic + raw params:
//        /chime/play supervibe 0 brightness 0.7 warmth 0.4 | gain 1.2 krush 3
//
//    CRITICAL: n uses SuperDirt's note system, NOT MIDI!
//      n 0 = C5 (middle C), n -12 = C4, n 12 = C6
//      To convert from MIDI: n = midinote - 60
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

    *initClass {
        oscResponders = [];
        loops = IdentityDictionary.new;
        isRunning = false;
    }

    *start { |listenPort = 57120, replyPort = 9501, replyHost = "127.0.0.1"|
        if(isRunning) {
            "ChimeAgent: already running. Call .stop first.".warn;
            ^this
        };

        replyAddr = NetAddr(replyHost, replyPort);

        this.registerResponders;

        isRunning = true;
        "ChimeAgent: listening on port %, replies to %:%".format(
            listenPort, replyHost, replyPort
        ).postln;
    }

    *stop {
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
        // /chime/ping/state — Request current state
        // ==========================================
        oscResponders = oscResponders.add(
            OSCdef(\chimePingState, { |msg, time, addr, recvPort|
                this.handlePingState;
            }, '/chime/ping/state')
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

    // ---- Ping/Pong handlers ----

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

    // Convenience: list all active loops
    *activeLoops {
        ^loops.keys.asArray
    }
}
