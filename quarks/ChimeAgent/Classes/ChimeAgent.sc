//    ChimeAgent — Bidirectional OSC layer for agent ↔ SuperCollider.
//
//    Handles:
//    - /chime/play     → semantic play (routes through ChimeSemantics → SuperDirt)
//    - /chime/raw      → bypass semantics, raw SuperDirt params
//    - /chime/loop/   → pattern looping via JITLib (Pdef)
//    - /chime/ping/   → request state/analysis (pong)
//    - /chime/pong/   → outbound analysis/state data
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
        // Expected format:
        //   /chime/play synthName note [dim1 val1 dim2 val2 ...]
        // Plus optional raw params after a "|" separator:
        //   /chime/play supervibe 60 brightness 0.7 warmth 0.4 | gain 1.2 pan 0.3
        //
        // For now (v0.1), simplified:
        //   /chime/play synthName n semanticKey1 val1 semanticKey2 val2 ...
        //
        // CRITICAL: n uses SuperDirt's note system, NOT MIDI!
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
        // /chime/loop/start loopName synthName "0 3 7 12" cycleDur
        oscResponders = oscResponders.add(
            OSCdef(\chimeLoopStart, { |msg, time, addr, recvPort|
                this.handleLoopStart(msg);
            }, '/chime/loop/start')
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
        var synthName, midinote, semantics, resolved, args;

        // msg format: ['/chime/play', synthName, n, key1, val1, key2, val2, ...]
        if(msg.size < 3) {
            "ChimeAgent: /chime/play needs at least synthName and n".warn;
            ^this
        };

        synthName = msg[1].asSymbol;
        midinote = msg[2].asFloat;  // this is 'n', not MIDI — var name kept for brevity

        // Parse semantic key-value pairs
        semantics = IdentityDictionary.new;
        (3, 5 .. msg.size - 2).do { |i|
            var key = msg[i].asSymbol;
            var val = msg[i+1].asFloat;
            semantics[key] = val;
        };

        // Resolve through ChimeSemantics
        resolved = ChimeSemantics.resolveAll(synthName, semantics);

        // Build SuperDirt args — use 'n' not 'midinote'
        args = ["s", synthName.asString, "n", midinote];

        // Add resolved params
        resolved.keysValuesDo { |param, val|
            args = args ++ [param.asString, val];
        };

        // Add gain default if not specified
        if(resolved[\gain].isNil) {
            args = args ++ ["gain", 1.0];
        };

        args = args ++ ["orbit", 0];

        // Send to SuperDirt
        // (In practice this goes through ~dirt, but for OSC-based agents
        // we send directly to SuperDirt's OSC port)
        this.sendToDirt(args);

        // Log it
        "ChimeAgent: play % n % → %".format(synthName, midinote, resolved).postln;
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
        // msg: ['/chime/loop/start', loopName, synthName, notePattern, cycleDur]
        // notePattern is a string like "0 3 7 12" (space-separated note numbers)
        var loopName, synthName, noteStr, cycleDur, notes, dur;

        if(msg.size < 5) {
            "ChimeAgent: /chime/loop/start needs loopName, synthName, notePattern, cycleDur".warn;
            ^this
        };

        loopName = msg[1].asSymbol;
        synthName = msg[2].asSymbol;
        noteStr = msg[3].asString;
        cycleDur = msg[4].asFloat;

        // Parse note pattern: "0 3 7 12" → [0, 3, 7, 12]
        notes = noteStr.split($ ).collect(_.asFloat);
        dur = cycleDur / notes.size;

        // Create or replace a Pdef
        Pdef(loopName,
            Pbind(
                \type, \dirt,
                \s, synthName.asString,
                \n, Pseq(notes, inf),
                \dur, dur,
                \gain, 1.0,
                \orbit, 0,
            )
        ).play;

        loops[loopName] = Pdef(loopName);

        "ChimeAgent: loop '%' started — % playing % (cycle %s)".format(
            loopName, synthName, notes, cycleDur
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
