# Changelog

## [0.1.1] - 2026-03-17
### Fixed
- OSC pong decoding for audio analysis
- Port cleanup on MCP shutdown
- Multiple listener processes accumulating on 9601

### Changed
- Consolidated logging format

## [0.1.0] - 2026-03-15
### Added
- Initial release
- 9 MCP tools (play, loop_start, loop_stop, phrase, get_state, get_audio, list_synths, get_synth_info, get_fx)
- CorallineSemantics with 305 empirically-validated timbral curves across 25 synths
- CorallineAgent OSC router with loop support via JITLib Pdef
- CorallineAnalysis audio analyzer