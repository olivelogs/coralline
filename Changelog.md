# Changelog

## [0.1.8] - 2026-06-09
### Added
- **Studio notebook.** New `read_notebook` / `add_note` tools backed by `notebook.md` at the repo root (committed — it's lineage, not a log). Claudes leave findings, recipes, quirks, and open questions for the sessions that come after; reading it at session start is how a new claude inherits what past claudes learned. Seeded with the first field report on the perceived semantics.

### Changed
- Loosened movement calibration per first field report: any percussive content pinned `perceived.movement` at 1.0, even under a drone. `perceptionCal[\moveRmsCV]` 0.5 → 1.0, `\moveCentroidWt` 0.6 → 0.65. Constants remain live-tweakable.

### Fixed
- MCP server version string in `index.ts` was stuck at 0.1.5; now tracks the release.

## [0.1.7] - 2026-06-09
### Added
- **Perceived semantics — the loop closes in one vocabulary.** `get_audio`'s windowed reply now includes `perceived`: an estimate of the same seven dimensions `play` speaks (brightness, warmth, texture, movement, space, weight, attack), heard back from the bus. Ask for brightness 0.7, hear back what the room actually gave you. New ears in the analyzer: band energies (sub+bass <150 Hz for weight, low-mid 250–800 Hz for warmth, high-mid 2–5 kHz as a harshness penalty), a fast envelope for crest factor (attack), and L/R correlation (space — the analyzer finally listens in stereo). Movement is computed from the variation of the centroid/rms history over the window — it can't exist in a snapshot at all.
- Calibration constants live in `CorallineAnalysis.perceptionCal` (hand-tuned v1, tweakable live); fitting them from the probing-pipeline data is the planned v2, which doubles as training-data generation.
- Known fuzz, honestly labeled: brightness/weight/texture track tightly; warmth and movement are decent (deep sub-bass material can read up to ~0.3 movement from FFT bin jitter); attack is the loosest. `perceived` describes the whole mix — one voice is a closed loop, multiple loops give a mastering-engineer's read of the sum.
- OSC: windowed `/coralline/pong/audio` gains `p_brightness` … `p_attack` fields; the MCP treats them as optional, so an older quark keeps working.

## [0.1.6] - 2026-06-09
### Added
- **Windowed `get_audio`.** `get_audio` now summarizes the last N seconds (default 4, `window` param, 0 = old instantaneous snapshot) instead of a single control-period snapshot that often landed between notes. CorallineAnalysis keeps a sclang-side ring of analysis frames (10 Hz × 60 s); the windowed reply carries level stats (mean/max/min rms), spectral centroid/flatness means over *active* frames (silence doesn't drag them down), median pitch + pitch stability, onset count/rate within the window, `active_ratio` (sound vs silence), and 12-point rms/centroid time-series showing the shape of the window. `/coralline/ping/audio` takes an optional third arg (window seconds); the pong shape with no `window` key is unchanged for back-compat.
- **`get_audio_clip` tool.** Saves the last N seconds (default 8, max 60) of the master bus to a wav in `recordings/` at the repo root (gitignored, kept as snippets-of-the-past) and returns the path — for deep offline analysis with external tools (e.g. audio-analyzer-rs: frequency bands, key, tempo, stereo field). Server-side stereo audio ring (`\corallineClipRec`, Phasor + BufWr) records continuously while the analyzer runs; `CorallineAnalysis.saveClip(duration, action)` unwraps the ring and writes int24 WAV. New OSC pair `/coralline/ping/clip` → `/coralline/pong/clip`.

### Fixed
- `onset_rate` no longer depends on when you last asked. It was computed as the onset-count delta since the previous `get_audio` call, so the first call after a long pause averaged over minutes. Windowed analysis counts onsets inside the window; the snapshot path keeps the old behavior.
- Windowed spectral stats NaN-guard: FFT analysis of silence can produce NaN, which would have poisoned every windowed mean.

## [0.1.5] - 2026-06-08
### Added
- `get_diagnostics` tool: inspects which processes hold the SuperCollider ports (57110/57120) and the MCP's reply port, checks the ping→pong path, and auto-flags duplicate/zombie SuperCollider processes and the `sclang`/`scsynth` shared-socket (FD inheritance) bug. macOS-only (uses `lsof`).

### Changed
- **Per-client reply ports.** Each MCP instance now binds its own OS-assigned ephemeral UDP port instead of the fixed `9601`, and every ping carries its reply port so SuperCollider answers the right client. Multiple clients (e.g. Claude Code and chat) can now both use `get_state`/`get_audio` at once. CorallineAgent falls back to the fixed `9601` reply address when a ping omits a port (back-compat).

### Fixed
- The `9601` "port held by another instance" failures are gone — there's no shared port to contend for, so the EADDRINUSE race, the false "held by another instance" error, and the stale-port-after-crash problem no longer apply. Removed the now-unneeded pidfile coordination.
- `get_diagnostics` liveness check now sends its reply port on the ping (matching `get_state`). Previously it omitted the port, so SuperCollider replied to the fallback `9601` and the diagnostic reported a false "no pong" even when `get_state` worked.

### Removed
- Pidfile (`coralline.pid`) and its stale-process cleanup — only existed to coordinate the shared port, which no longer exists. Supersedes the 0.1.3 graceful-port-sharing degrade.

## [0.1.4] - 2026-03-28
### Added
- Added SuperDirt synth superfm to mappings: `CorallineSemantics.sc` now has a synth alias for four superfm presets (1 through 4). These resolve semantics against their own mappings, then send `superfm` + `voice: N` to SuperDirt.
- Merged pipeline data + hand-mapped LFO curves. 29 playable synths, superfm_v1-v4 included. lfodepth capped at 0.3 in the semantic layer with a documented safe_max and note for future instances.

## [0.1.3] - 2026-03-24
### Fixed
- Graceful port sharing: second MCP instance no longer kills the first. Claude Desktop stays connected when Claude Code starts. Play/loop/synth/fx tools work from both clients; get_state and get_audio surface a clear "port in use" message on the second instance.

## [0.1.2] - 2026-03-20
### Fixed
- Tool visibility on Claude Desktop: flattened `play` input schema
- Audio analysis for onset_rate returning 0. Separate FFT chain for Onsets (no sharing with centroid/flatness), `\power` instead of `\rcomplex` (energy detection, not spectral novelty), and threshold 0.1 (sensitive enough for both kicks and pad note-ons).

### Changed
- Combined `play` and `phrase` into one `play` tool
- Tightened the `play` tool description for better discoverability

## [0.1.1] - 2026-03-17
### Fixed
- OSC pong decoding for audio analysis & state retrieval
- Port cleanup on MCP shutdown
- Multiple listener processes accumulating on 9601

### Changed
- inbound decoding of OSC messages in osc.ts for `s`, `i`, `f`, `d` types.
- changed from FIFO pong resolver to reqId-based

## [0.1.0] - 2026-03-15
### Added
- Initial release
- 8 MCP tools (play, loop_start, loop_stop, get_state, get_audio, list_synths, get_synth_info, get_fx)
- CorallineSemantics with 305 empirically-validated timbral curves across 25 synths
- CorallineAgent OSC router with loop support via JITLib Pdef
- CorallineAnalysis audio analyzer
