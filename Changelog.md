# Changelog

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
