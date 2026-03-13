# Coralline Quarks — Setup Guide

### 1. Tell SuperCollider where to find quarks
In SuperCollider, evaluate:
```supercollider
// Add the coralline quarks directory to SC's search path
Quarks.install("file:///Users/.../coralline/CorallineSemantics");
```

### 2. Recompile the class library
`Language > Recompile Class Library` (Cmd+Shift+L on Mac)

This is required every time you change a .sc class file!

### 3. Verify
After recompile, evaluate:
```supercollider
CorallineSemantics.synths;                     // should show full list of synths except superfm
CorallineSemantics.dimensionsFor(\supervibe);  // should show: [ attack, brightness, movement, texture, warmth, weight ]
CorallineSemantics.summary;                    // show dimensions for synths
CorallineSemantics.inspect(\supervibe);        // pretty-prints the mapping table for a synth
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


## Directory structure
```
Coralline/
├── Coralline.quark                  ← package metadata
├── Classes     
│   ├── CorallineSemantics.sc        ← the mapping engine
│   ├── CorallineAgent.sc            ← OSC routing, loops, ping/pong
│   ├── CorallineAnalysis.sc         ← real-time audio analysis    
│   └── Data/                        ← probing data + refined mappings   
│       ├── refined_mappings.json    
│       └── test-semantics.scd       ← standalone test script for mappings, you don't really need it
```
