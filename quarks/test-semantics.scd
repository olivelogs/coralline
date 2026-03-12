/*
    CorallineSemantics — Quick Test Script
    Run this in SuperCollider to hear semantic mappings in action.
    No quark installation needed — this is a standalone demo.

    March 2026 — Olive + Claude

    HOW TO USE:
    1. Make sure SuperDirt is running (your startup.scd should handle this)
    2. Select all this code and evaluate it (Cmd+Enter on Mac)
    3. You should hear a sequence of notes demonstrating each semantic dimension
*/

(
// === A tiny inline version of CorallineSemantics for testing ===

// Mapping table for supervibe
~corallineMappings = IdentityDictionary[
    \brightness -> IdentityDictionary[
        \modfreq -> (\outLo: 0.1, \outHi: 20, \curve: \exp, \weight: 0.6),
        \modamp  -> (\outLo: 0,   \outHi: 1,  \curve: \lin, \weight: 0.4),
    ],
    \warmth -> IdentityDictionary[
        \velocity -> (\outLo: 0.2, \outHi: 1.0, \curve: \lin, \weight: 1.0),
    ],
    \space -> IdentityDictionary[
        \detune -> (\outLo: 0, \outHi: 0.5, \curve: \lin, \weight: 1.0),
    ],
];

// Simple linear map (we'll add curves later)
~corallineMap = { |val, outLo, outHi, curve|
    case
    { curve == \lin } { val.linlin(0, 1, outLo, outHi) }
    { curve == \exp } { val.max(0.001).linexp(0.001, 1, outLo.max(0.001), outHi.max(0.001)) }
    { true } { val.linlin(0, 1, outLo, outHi) }
};

// Resolve semantics to SuperDirt args
~corallineResolve = { |semantics|
    var result = IdentityDictionary.new;
    semantics.keysValuesDo { |dim, val|
        var dimMap = ~corallineMappings[dim];
        if(dimMap.notNil) {
            dimMap.keysValuesDo { |param, spec|
                var mapped = ~corallineMap.(val, spec[\outLo], spec[\outHi], spec[\curve]);
                result[param] = mapped;
            };
        };
    };
    result
};

// Play a note using semantic params
~corallinePlay = { |midinote, semantics, dur = 1|
    var resolved = ~corallineResolve.(semantics);
    // CRITICAL: use \n not \midinote!
    // SuperDirt's n system: n 0 = C5, n -12 = C4, n 12 = C6
    var args = [
        \type, \dirt,
        \s, "supervibe",
        \n, midinote,
        \sustain, dur * 0.8,
        \gain, 1.2,
        \orbit, 0,
    ];
    // Add resolved semantic params
    resolved.keysValuesDo { |param, val|
        args = args ++ [param, val];
    };
    (args.asEvent).play;
};

"CorallineSemantics test loaded. Running demo...".postln;

// === DEMO SEQUENCE ===
// Each section demonstrates a semantic dimension changing

fork {
    var wait = 1.5;

    "".postln;
    "=== BRIGHTNESS: dark → bright ===".postln;
    [0.1, 0.3, 0.5, 0.7, 0.9].do { |b, i|
        "  brightness: %".format(b).postln;
        ~corallinePlay.(0 + (i * 2), (\brightness: b), 1);  // n 0 = C5
        wait.wait;
    };

    1.wait;

    "".postln;
    "=== WARMTH: cold → warm ===".postln;
    [0.1, 0.3, 0.5, 0.7, 0.9].do { |w, i|
        "  warmth: %".format(w).postln;
        ~corallinePlay.(0 + (i * 2), (\warmth: w), 1);  // n 0 = C5
        wait.wait;
    };

    1.wait;

    "".postln;
    "=== COMBINED: bright+warm → dark+cold ===".postln;
    5.do { |i|
        var b = 1 - (i / 4);  // 1.0 → 0.0
        var w = 1 - (i / 4);
        "  brightness: %, warmth: %".format(b.round(0.01), w.round(0.01)).postln;
        ~corallinePlay.(7 - (i * 2), (\brightness: b, \warmth: w), 1);  // n 7 = G5
        wait.wait;
    };

    1.wait;

    "".postln;
    "=== SPACE: narrow → wide ===".postln;
    [0.0, 0.25, 0.5, 0.75, 1.0].do { |sp, i|
        "  space: %".format(sp).postln;
        ~corallinePlay.(0, (\space: sp, \brightness: 0.5), 1.5);  // n 0 = C5
        2.wait;
    };

    "".postln;
    "Demo complete!".postln;
};
)
