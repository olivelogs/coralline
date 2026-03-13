# Coralline

Coralline gives Claude the ability to make music in real-time with SuperCollider, effectively translating dialogue into music. This should be used with the coralline-mcp. 

The quark package re-writes superdirt synthdefs to be agent-friendly - that is, SynthDef params are defined semantically and normalized. it requires superdirt to run.

---

## Dependencies:
SuperCollider IDE
SuperDirt
coralline-mcp

### Optional (highly recommended, very fun):
TidalCycles
This allows you to build on what Claude writes as loops.

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

## Usage:
1. Install SuperCollider IDE and SuperDirt, following their instructions. 

2. Make sure coralline-mcp has been installed and Claude's config has been updated to reflect the MCP access and correct port (57120)

3. Recompile in supercollider (cmd + shift + L on Mac)

4. Run your startup file to start SuperDirt with `"Users/.../superdirt_startup.scd".load`

4. Run this in SuperCollider (shift + enter per line on Mac)

```supercollider
CorallineSemantics.loadRefined;    // reads refined_mappings.json, replaces all mappings
CorallineSemantics.summary;        // see what loaded, writes to post window
CorallineAgent.start;              // start coralline
```

after running `CorallineAgent.start;` you should see this in the post window (ports may be different):
```txt
CorallineAgent: responders registered.
CorallineAgent: listening on port 57120, replies to 127.0.0.1:9501
CorallineAnalysis: audio analyzer started (listening on bus 0).
```

5. ask claude to play a sound with `/coralline/play`.

6. That's it! Explore the tooling together, and if you downloaded TidalCycles, you can play alongside Claude.

---

### If you do not hear sound:
- First just try run `Server.killAll` in supercollider, recompile (cmd + shift + L on Mac), and re-boot the server again. 
- If that does not work, check that Claude's config is set up with the correct port (SuperDirt runs on port 57120)
- SuperCollider handles raw audio, which doesn't always get along with bluetooth headphones. If you're running into trouble here, I recommend using your built-in speakers (check audio MIDI settings in Mac) and building from there.

### note:
i strongly recommend orienting yourself in the SuperCollider IDE if you have not used it before. Read through a few pages of the docs (which are displayed in the IDE) and learn how to run code inside the SCIDE. You don't need to know sclang to use these tools - but SuperCollider is the backbone of the tooling, so you'll be in there often!

---

what's missing in v0.1
- fx 
- sample reference list
- superfm synth - it's a beast
