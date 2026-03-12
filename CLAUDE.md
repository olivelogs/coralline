# Coralline

Coralline is a system for AI agents to compose and perform music through SuperCollider. Agents think in perceptual terms (brightness, warmth, texture) and Coralline translates that into synth-specific parameters.

## Architecture

Three SuperCollider quarks (two in development, one future):

- **CorallineSemantics** (`quarks/CorallineSemantics/`) — Semantic parameter mapping engine. Maps 7 perceptual dimensions (brightness, warmth, texture, movement, space, weight, attack) normalized 0-1 to synth-specific params with weighted curves. Currently has empirically validated mappings for supervibe, supersaw, superpiano.
- **CorallineAgent** (`quarks/CorallineAgent/`) — Bidirectional OSC layer. Handles `/coralline/play` (semantic), `/coralline/raw` (bypass), `/coralline/loop/*` (JITLib/Pdef patterns), and `/coralline/ping/*` (state queries). Supports `|` pipe separator to mix semantic + raw params.
- **CorallineAnalysis** (`quarks/CorallineAgent/Classes/CorallineAnalysis.sc`) — Real-time audio analysis. FFT-based analyzer providing rms, spectral centroid, flatness, pitch, and pitch confidence. Uses a callback pattern to decouple from OSC transport.
- **CorallineDirt** (future) — Custom SynthDefs designed natively for the semantic layer.

**MCP2OSC** (`MCP2OSC/`) — Generic MCP-to-OSC bridge (Node.js). Sends to SuperDirt on port 57120. Works as-is, no modifications needed currently.

## Critical SuperCollider Gotchas

- **Note system**: SuperDirt uses `n`, NOT MIDI. `n 0 = C5` (middle C). To convert: `n = midinote - 60`. Sending `n 60` puts you in dog whistle territory.
- **Sample names in OSC**: Just the name, e.g. `"s", "giggle"` — SC already has them loaded. Use `giggle:1` for the second sample in a folder.
- **SuperDirt port**: 57120 (configured in `scd/config/superdirt_startup.scd`)
- **OSC message format**: Flat key-value args to `/dirt/play`, e.g. `["s", "supervibe", "n", 0, "gain", 1.2]`

## Quark Editing

The quarks in `quarks/` are symlinked into SuperCollider's downloaded-quarks directory. The repo is the canonical source. Changes here show up in SC after recompiling the class library (Cmd+Shift+L in SC IDE).

## Probing Pipeline (CorallineSemantics/Data/)

How semantic mappings are built:
1. `probing_plan.py` — reference of all synths and params to probe
2. `probe_synths.scd` — SC script that sweeps params and records wavs
3. `analyze_probes.py` — librosa analysis, produces `probes_analysis.json` and `probes_profiles.json`
4. Human review of auto-suggested mappings → refined mappings
5. Mappings get hardcoded into `CorallineSemantics.sc` (eventual goal: `loadMappingsFromFile`)

## Effects

Effects are passed as raw SuperDirt params after the `|` pipe separator:
```
/coralline/play supervibe 0 brightness 0.7 | room 0.5 lpf 3000 krush 3
```
Reference: `scd/docs/effects-reference.md`

## Project Conventions

- This is Olive's project. Ask before making architectural decisions.
- Keep things simple — don't over-engineer. This is a creative tool, not enterprise software.
- SuperCollider code lives in `.sc` files (quark classes) and `.scd` files (scripts/configs).
- The project is in active development and pivots happen. Read `scd/docs/overview_2026-03-07.md` and `scd/docs/chime-quarks-sketch.md` for current direction.
- Files in `archive/`, `skills/`, `tidal/` are from earlier iterations and not currently active.

## What Not To Do

- Don't use `midinote` in SuperDirt messages — use `n`.
- Don't modify `MCP2OSC/` without asking — it works and is generic.
- Don't create new files when you can edit existing ones.
- Don't add Sonic Pi code — we've moved past that.
- TidalCycles is still used for live layering — Olive plays Tidal while Claude plays via OSC/SuperDirt, building together. Tidal code is welcome.
