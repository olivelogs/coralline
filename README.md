# coralline (v0.1.0)

Coralline gives Claude the ability to make music in real-time with SuperCollider, effectively translating dialogue into music. Coralline consists of two things: the Coralline MCP and a SuperCollider quark. **Both** are required for use. 

The MCP provides a set of tools for an agent to interact with SuperCollider, such as play, run or modify loops, and ping for state and audio data.

The quark enables AI agents to interact with SuperDirt synths. There are three classes: CorallineAgent, CorallineSemantics, and CorallineAnalysis.  

**ChimeAgent** defines how the agent can use OSC to interact with the SuperDirt server.  
**ChimeAnalysis** defines audio analysis behavior, which an agent can request to "hear" the music.  
**CorallineSemantics** resolves the base SuperDirt Synthdefs to be agent-friendly - that is, Synthdefs are routed through it, and params are resolved into semantic dimensions. It requires superdirt to run.  

---

## Dependencies:
- [SuperCollider IDE](https://supercollider.github.io/)
- [SuperDirt](https://codeberg.org/musikinformatik/SuperDirt) - note that it is much easier to just download the entire TidalCycles package to get SuperDirt working correctly

### Optional (but highly recommended for full functionality):
- [TidalCycles](https://tidalcycles.org/)  
This allows you to build on what Claude writes as loops.

*These dependencies are independently licensed and have their own installation instructions.*

### what's missing in v0.1
- sample reference list
- superfm synth - it's a beast

---

## Installation
You must have the SuperCollider IDE, SuperDirt, and Claude Desktop installed. TidalCycles is optional, but highly recommended.

### Clone the repo

```bash
git clone https://github.com/olivelogs/coralline.git
```

### Setup the MCP
Navigate to the MCP directory:
```bash
cd path/to/coralline/coralline-mcp/
```

Run:
```bash
npm install
npm run build
```

To Claude's config (in Claude Desktop settings -> Developer -> Edit Config), add:
```json
{
  "mcpServers": {
    "coralline": {
      "command": "node",
      "args": ["/Users/.../coralline/coralline-mcp/dist/index.js"],
    }
  }
}
```

Restart Claude Desktop. You should see the "coralline" in the list of local MCP servers in the Developer tab.  

v0.1.0 exposes nine tools to Claude:  

| tool              | function                                                  |
|-------------------|-----------------------------------------------------------|
| `play`            | play a single note on a  superdirt synth with params      |
| `loop_start`      | start or modify a named pattern loop                      |
| `loop_stop`       | stop a named loop (manual stop with cmd + . on mac)       |
| `phrase`          | play a phrase, multiple notes                             |
| `get_state`       | get state of SC (current loops, if CorallineAgent is on)  |
| `get_audio`       | "listen", requests quick audio analysis of rolling buffer |
| `list_synths`     | show Claude available synths                              |
| `get_synth_info`  | show Claude available params for synths                   |
| `get_fx`          | show Claude available effects                             |

**However, these won't be usable without the Quarks!**

### Setup the quarks
In the SuperCollider IDE, before starting the SuperDirt server, add the coralline quarks directory to SC's search path and tell SuperCollider to manage it:
```supercollider
Quarks.install("file:///Users/.../coralline/quark/Coralline");
thisProcess.recompile;
```

The post window should show:
```txt
Installing Coralline
Adding path: /Users/olivelo/coralline/quark/Coralline
Coralline installed
-> Quark: Coralline[0.1.0]
-> a Main
```
  
After installing the quarks, **recompile in SCIDE** (Cmd + shift + L on Mac)  
*Every time you edit an `.sc` class file, you will need to recompile SCIDE (Cmd + Shift + L)*  
  
Start SuperDirt with your startup file (found in the `SuperCollider/Quarks/SuperDirt/` directory). Use `shift` + `enter` to run lines of code in SCIDE on Mac.

```supercollider
"Users/.../superdirt_startup.scd".load
````

Start Coralline in SuperCollider (you can also add this to your startup file). Shift + enter for each line.
```supercollider
CorallineSemantics.loadRefined;    // reads refined_mappings.json, replaces all mappings
CorallineSemantics.summary;        // see what loaded, writes to post window
CorallineAgent.start;              // start coralline
```

After running `CorallineSemantics.loadRefined;` you should see this in the post window:
```txt
CorallineSemantics: loaded 25 synths, 305 timbral curves from 'refined_mappings.json'

=== CorallineSemantics Summary ===
  refined loaded: true
  synths: 25
    soshats — 6 dimensions, 6 curves
    soskick — 6 dimensions, 9 curves
    sossnare — 6 dimensions, 17 curves
    super808 — 6 dimensions, 10 curves
    superchip — 3 dimensions, 3 curves
    superclap — 6 dimensions, 15 curves
    supercomparator — 6 dimensions, 14 curves
    supergong — 5 dimensions, 5 curves
    supergrind — 7 dimensions, 16 curves
    superhammond — 6 dimensions, 16 curves
    superhex — 5 dimensions, 5 curves
    superhoover — 7 dimensions, 7 curves
    superkick — 6 dimensions, 11 curves
    supermandolin — 5 dimensions, 5 curves
    supernoise — 6 dimensions, 26 curves
    superpiano — 7 dimensions, 16 curves
    superprimes — 5 dimensions, 6 curves
    superpwm — 6 dimensions, 16 curves
    superreese — 6 dimensions, 8 curves
    supersaw — 6 dimensions, 26 curves
    supersnare — 6 dimensions, 6 curves
    supersquare — 6 dimensions, 16 curves
    supertron — 6 dimensions, 11 curves
    supervibe — 6 dimensions, 20 curves
    superzow — 6 dimensions, 15 curves
  total curves: 305
  synths with pitch controls: [soshats, soskick, sossnare, sostoms, superchip, supercomparator, supergong, superhammond, superhoover, supernoise, superprimes, superpwm, supersaw, supersquare, superwavemechanics]
```

After running `CorallineAgent.start;` you should see this in the post window:
```txt
CorallineAgent: responders registered.
CorallineAgent: listening on port 57120, replies to 127.0.0.1:9601
CorallineAnalysis: audio analyzer started (listening on bus 0).
```

**That's it!** Explore the tooling together, and if you downloaded TidalCycles, you can play alongside Claude.

---

### If you do not hear sound when Claude sends an OSC message:
- First just run `s.quit`, then `Server.killAll` in SCIDE. Recompile (cmd + shift + L on Mac), and re-boot the SuperDirt server from the startup file again. 
- If that does not work, check that SuperDirt and CorallineAgent are running on the correct port (`57120`). I am working on adjusting the MCP to make ports configurable in case this happens.
- SuperCollider handles raw audio, which doesn't always get along with bluetooth headphones. If you're running into trouble here, I recommend using your built-in speakers (check audio MIDI settings in Mac) and building from there.

### note:
I strongly recommend orienting yourself in the SuperCollider IDE if you have not used it before. Read through a few pages of the docs (which are displayed in the IDE) and learn how to run code inside the SCIDE. You don't need to know sclang to use these tools - but SuperCollider is the backbone of the tooling, so you'll be in there often!

### Another note:
- To stop a loop, run Cmd + . (Mac) in SCIDE

### Acknowledgements
Building this would not have been possible without these amazing tools:
- [MCP2OSC](https://github.com/yyf/MCP2OSC) by Yuan-Yi Fan
- [AVisualizer](https://github.com/JuzzyDee/AVisualizer) by JuzzyDee

---

## What the quark does

Coralline seeks to capture musical intent by translating raw params into semantic definitions. When you say "make the sound brighter," Claude can adjust *brightness* rather than finding the right param to adjust, as each synth reacts differently to different params. We mapped param effects to semantic meaning, using curves derived from audio analysis. Seven dimensions of sound:

| dimension   | description                                  |
|-------------|----------------------------------------------|
| brightness  | spectral energy distribution (dark ↔ bright) |
| warmth      | harmonic richness, low-mid presence          |
| texture     | smooth ↔ rough/noisy                         |
| movement    | modulation rate, vibrato, LFO activity       |
| space       | stereo width, detuning spread                |
| weight      | low-frequency energy, body                   |
| attack      | onset sharpness (soft ↔ percussive)          |

Claude uses these instead of raw params to create more expressive sound through conversation. Raw params can still be passed with a pipe `|` separator. These override semantics. 

---

## Directory structure

```txt
coralline/
├── coralline-mcp/
│   └── src/
│       ├── index.ts            ← server entry, tool registration, startup
│       ├── types.ts            ← shared TypeScript types
│       ├── osc.ts              ← OscClient (sender) + OscServer (FIFO pong resolver)
│       ├──  logger.ts          ← JSONL append logger with rotation
│       └──  tools/
│             ├── play.ts               ← play tool
│             ├── loops.ts              ← loop_start, loop_stop
│             ├── phrase.ts             ← phrase tool
│             ├── analysis.ts           ← get_state, get_audio
│             ├── synths.ts             ← list_synths, get_synth_info
│             └── fx.ts                 ← get_fx
│
├── quark
│   └── Coralline
│       ├── Coralline.quark                  ← package metadata
│       └── Classes     
│           ├── CorallineSemantics.sc        ← the mapping engine
│           ├── CorallineAgent.sc            ← OSC routing, loops, ping/pong
│           ├── CorallineAnalysis.sc         ← real-time audio analysis    
│           └── Data/                           
│               ├── refined_mappings.json 
│               └── effects.json  
```
  