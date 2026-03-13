# Coralline Quarks — Setup Guide

### 1. Tell SuperCollider where to find quarks
In SuperCollider, evaluate:
```supercollider
// Add the coralline quarks directory to SC's search path
Quarks.install("file:///Users/.../coralline/quarks/CorallineSemantics");
Quarks.install("file:///Users/.../coralline/quarks/CorallineAgent");
```

### 2. Recompile the class library
`Language > Recompile Class Library` (Cmd+Shift+L on Mac)

This is required every time you change a .sc class file!

### 3. Verify
After recompile, evaluate:
```supercollider
CorallineSemantics.synths;           // should show: [ supervibe, superpiano, supersaw ]
CorallineSemantics.dimensionsFor(\supervibe);  // should show: [ brightness, movement, space, warmth ]
CorallineSemantics.inspect(\supervibe);        // pretty-prints the mapping table
```

### 4. Start CorallineAgent
```supercollider
// After SuperDirt is running:
CorallineAgent.start;
```
> You can also put this in your SuperDirt startup file.

## Development workflow
When you edit a .sc class file:
1. Save the file
2. Recompile class library (Cmd+Shift+L)
3. Re-run `CorallineAgent.start` if needed

For quick iteration on mapping values, use the `test-semantics.scd` script
(it doesn't need recompilation since it uses environment variables, not classes).

## Directory structure
```
quarks/
├── CorallineSemantics/
│   ├── CorallineSemantics.quark     ← package metadata
│   ├── Classes/
│   │   └── CorallineSemantics.sc    ← the mapping engine
│   └── Data/                        ← probing data + refined mappings
│
├── CorallineAgent/
│   ├── CorallineAgent.quark         ← package metadata
│   ├── Classes/
│   │   ├── CorallineAgent.sc        ← OSC routing, loops, ping/pong
│   │   └── CorallineAnalysis.sc     ← real-time audio analysis
│
└── test-semantics.scd               ← standalone test script
```
