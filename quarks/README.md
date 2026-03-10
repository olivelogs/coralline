# Chime Quarks — Setup Guide

## Quick test (no install needed)
Open SuperCollider, make sure SuperDirt is running, then:
```
File > Open > /Users/olivelo/chime/quarks/test-semantics.scd
```
Select all, evaluate (Cmd+Enter). You should hear supervibe notes demonstrating brightness, warmth, and space changing.

## Installing the quarks (for real)

### 1. Tell SuperCollider where to find them
In SuperCollider, evaluate:
```supercollider
// Add the chime quarks directory to SC's search path
Quarks.install("file:///Users/olivelo/chime/quarks/ChimeSemantics");
Quarks.install("file:///Users/olivelo/chime/quarks/ChimeAgent");
```

### 2. Recompile the class library
`Language > Recompile Class Library` (Cmd+Shift+L on Mac)

This is required every time you change a .sc class file!

### 3. Verify
After recompile, evaluate:
```supercollider
ChimeSemantics.synths;           // should show: [ supervibe, superpiano, supersaw ]
ChimeSemantics.dimensionsFor(\supervibe);  // should show: [ brightness, movement, space, warmth ]
ChimeSemantics.inspect(\supervibe);        // pretty-prints the mapping table
```

### 4. Start ChimeAgent
```supercollider
// After SuperDirt is running:
ChimeAgent.start;
```
> could just put this in my startup file?

## Development workflow
When you edit a .sc class file:
1. Save the file
2. Recompile class library (Cmd+Shift+L)
3. Re-run `ChimeAgent.start` if needed

For quick iteration on mapping values, use the `test-semantics.scd` script 
(it doesn't need recompilation since it uses environment variables, not classes).

## Directory structure
```
quarks/
├── ChimeSemantics/
│   ├── ChimeSemantics.quark     ← package metadata
│   ├── Classes/
│   │   └── ChimeSemantics.sc    ← the mapping engine
│   └── Data/                    ← (future: Qwen's probing data)
│
├── ChimeAgent/
│   ├── ChimeAgent.quark         ← package metadata  
│   ├── Classes/
│   │   └── ChimeAgent.sc        ← OSC routing, loops, ping/pong
│
└── test-semantics.scd           ← standalone test script
```
