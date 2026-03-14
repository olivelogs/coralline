# coralline (v0.1.0)

Coralline gives Claude the ability to make music in real-time with SuperCollider, effectively translating dialogue into music. Coralline consists of two things: a SuperCollider quark and the Coralline MCP.  

The quark package re-writes the base SuperDirt SynthDefs to be agent-friendly - that is, SynthDef params are defined semantically and normalized. It requires superdirt to run.  

The MCP provides a set of tools for an agent to interact with SuperCollider, such as play, run or modify loops, and ping for state and audio data.

---

## Dependencies:
- [SuperCollider IDE](https://supercollider.github.io/)
- [SuperDirt](https://codeberg.org/musikinformatik/SuperDirt) - note that is much easier to just download the entire TidalCycles package to get SuperDirt working correctly

### Optional (highly recommended, very fun):
- [TidalCycles](https://tidalcycles.org/)  
This allows you to build on what Claude writes as loops.

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
cd path/to/coralline-mcp/
```

Run:
```bash
npm install
```

To Claude's config, add:
```json
{
  "mcpServers": {
    "coralline": {
      "command": "node",
      "args": ["/Users/olivelo/MCPs/coralline-mcp/dist/index.js"],
    }
  }
}
```

Restart Claude Desktop. You should see the "coralline" in the list of local MCP servers in the Developer tab. 

Claude should be able to access the following tools with the MCP: `play`, `loop_start`, `loop_stop`, `phrase`, `get_state`, `get_audio`, `list_synths`, `get_synth_info`, `get_fx`.  

However, these won't be usable without the Quarks!

### Setup the quarks
In the SuperCollider IDE, before starting the SuperDirt server, add the coralline quarks directory to SC's search path:
```supercollider
Quarks.install("file:///Users/.../coralline/quark/Coralline");
```

The post window should show:
```txt
Installing Coralline
Adding path: /Users/olivelo/coralline/quark/Coralline
Coralline installed
-> Quark: Coralline[0.1.0]
```
  
**Recompile in SCIDE (Cmd + shift + L on Mac)**  
  
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

After running `CorallineAgent.start;` you should see this in the post window:
```txt
CorallineAgent: responders registered.
CorallineAgent: listening on port 57120, replies to 127.0.0.1:9501
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
If you edit an `.sc` class file, you will need to recompile SCIDE (Cmd + Shift + L)

---

## what's missing in v0.1
- sample reference list
- superfm synth - it's a beast

---

## What the quark does

Coralline translates raw params into semantic definitions. When you say "make the sound brighter," Claude can adjust *brightness* rather than finding the right param to adjust, as each synth reacts differently to different params. We mapped param effects to semantic meaning, using curves derived from audio analysis. Eight dimensions of sound:

| dimension   | description                                  |
|-------------|----------------------------------------------|
| brightness  | spectral energy distribution (dark ↔ bright) |
| warmth      | harmonic richness, low-mid presence          |
| texture     | smooth ↔ rough/noisy                         |
| movement    | modulation rate, vibrato, LFO activity       |
| space       | stereo width, detuning spread                |
| weight      | low-frequency energy, body                   |
| attack      | onset sharpness (soft ↔ percussive)          |

Claude uses these instead of raw params to create more expressive sound through conversation. 

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
  