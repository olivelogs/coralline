# Coralline

Coralline is a system for AI agents to compose and perform music through SuperCollider. Agents think in perceptual terms (brightness, warmth, texture) and Coralline translates that into synth-specific parameters.

## Architecture

One supercollider quark with three classes:

- **CorallineSemantics** (`quark/Coralline/Classes/CorallineSemantics.sc`) — Semantic parameter mapping engine. Maps 7 perceptual dimensions (brightness, warmth, texture, movement, space, weight, attack) normalized 0-1 to synth-specific params with weighted curves. Currently has empirically validated mappings for supervibe, supersaw, superpiano.
- **CorallineAgent** (`quark/Coralline/Classes/CorallineAgent.sc`) — Bidirectional OSC layer. Handles `/coralline/play` (semantic), `/coralline/raw` (bypass), `/coralline/loop/*` (JITLib/Pdef patterns), and `/coralline/ping/*` (state queries). Supports `|` pipe separator to mix semantic + raw params.
- **CorallineAnalysis** (`quark/Coralline/Classes/CorallineAnalysis.sc`) — Real-time audio analysis. FFT-based analyzer providing rms, spectral centroid, flatness, pitch, pitch confidence, and onset rate. Uses a callback pattern to decouple from OSC transport.

**coralline-mcp** (`coralline-mcp/`) — TypeScript MCP server bridging Claude to SuperCollider via OSC. Reads synth mappings and effects data from `quark/Coralline/Data/`. The old generic MCP2OSC bridge is archived in `resources/MCP2OSC/`.

## Critical SuperCollider Gotchas

- **Note system**: SuperDirt uses `n`, NOT MIDI. `n 0 = C5` (middle C). To convert: `n = midinote - 60`. Sending `n 60` puts you in dog whistle territory.
- **Sample names in OSC**: Just the name, e.g. `"s", "giggle"` — SC already has them loaded. Use `giggle:1` for the second sample in a folder.
- **SuperDirt port**: 57120 (configured in `resources/scd/config/superdirt_startup.scd`)
- **OSC message format**: Flat key-value args to `/dirt/play`, e.g. `["s", "supervibe", "n", 0, "gain", 1.2]`

## Quark Editing

The quark in `quark/Coralline/` loaded into SuperCollider from the repo. Changes here show up in SC after recompiling the class library (Cmd+Shift+L in SC IDE).

## Probing Pipeline (quark/Coralline/Data/mapping/)

How semantic mappings are built (now in `resources/`)
1. `probing_plan.py` — reference of all synths and params to probe
2. `probe_synths.scd` — SC script that sweeps params and records wavs
3. `analyze_probes.py` — librosa analysis, produces `probes_analysis.json` and `probes_profiles.json`
4. Human review of auto-suggested mappings → refined mappings
5. Mappings are loaded into `CorallineSemantics.sc` with `loadMappingsFromFile`

## Effects

Effects are passed as raw SuperDirt params after the `|` pipe separator:
```
/coralline/play supervibe 0 brightness 0.7 | room 0.5 lpf 3000 krush 3
```
Reference: `resources/scd/docs/effects-reference.md`

## Project Conventions

- This is Olive's project. Ask before making architectural decisions.
- Keep things simple — don't over-engineer. This is a creative tool, not enterprise software.
- SuperCollider code lives in `quark/`.
- The project is in active development and pivots happen. Read `changelog.md` and `directions.md` in addition to `README.md`, these stay updated.
- Files in `resources/` are from earlier iterations and not currently active.

## What Not To Do

- Don't use `midinote` in SuperDirt messages — use `n`.
- Don't modify `resources/MCP2OSC/` — it's an archived reference copy.
- Don't create new files when you can edit existing ones.
- Don't add Sonic Pi code — we've moved past that.

