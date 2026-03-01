# Synths Quick Reference

All synths support standard opts: `note:`, `amp:`, `pan:`, `attack:`, `decay:`, `sustain:`, `release:`

## Basic Waveforms
| Synth | Sound | Special Opts |
|-------|-------|--------------|
| `:beep` / `:sine` | Pure sine wave | — |
| `:saw` | Bright, buzzy | — |
| `:square` | Hollow, retro | `width:` (pulse width) |
| `:pulse` | Variable square | `pulse_width:` |
| `:tri` | Soft, mellow | `width:` |
| `:noise` | White noise | `cutoff:`, `res:` |
| `:pnoise` | Pink noise (darker) | `cutoff:`, `res:` |
| `:bnoise` | Brown noise (deep) | `cutoff:`, `res:` |

## Detuned (thick/chorused)
| Synth | Sound | Special Opts |
|-------|-------|--------------|
| `:dsaw` | Thick detuned saws | `detune:` |
| `:dpulse` | Detuned pulses | `detune:`, `pulse_width:` |
| `:dtri` | Detuned triangles | `detune:` |

## Modulated (movement/wobble)
| Synth | Sound | Special Opts |
|-------|-------|--------------|
| `:mod_saw` | Saw + tremolo | `mod_phase:`, `mod_range:`, `mod_width:` |
| `:mod_sine` | Sine + tremolo | `mod_phase:`, `mod_range:` |
| `:mod_tri` | Triangle + tremolo | `mod_phase:`, `mod_range:` |
| `:mod_pulse` | Pulse + tremolo | `mod_phase:`, `mod_pulse_width:` |
| `:mod_dsaw` | Detuned saw + mod | `detune:`, `mod_phase:` |

## FM Synthesis
| Synth | Sound | Special Opts |
|-------|-------|--------------|
| `:fm` | Basic FM, bell-like | `divisor:`, `depth:` |
| `:mod_fm` | FM + modulation | `divisor:`, `depth:`, `mod_phase:` |

## Character Synths
| Synth | Sound | Special Opts |
|-------|-------|--------------|
| `:tb303` | Acid bass, squelchy | `cutoff:`, `res:`, `wave:` (0=saw,1=pulse) |
| `:supersaw` | Trance supersaw | `cutoff:`, `res:` |
| `:hoover` | 90s rave hoover | `cutoff:`, `res:` |
| `:prophet` | Dark PWM pad | `cutoff:`, `res:` |
| `:zawa` | Synced saws, metallic | `phase:`, `cutoff:`, `wave:` |
| `:growl` | Aggressive growl | `cutoff:`, `res:` |
| `:hollow` | Hollow, breathy | `cutoff:`, `res:` |
| `:dark_ambience` | Dark texture | `cutoff:`, `res:`, `detune1:`, `detune2:` |

## Bells & Keys
| Synth | Sound | Special Opts |
|-------|-------|--------------|
| `:dull_bell` | Muted bell | — |
| `:pretty_bell` | Bright bell | — |
| `:piano` | Synth piano | `vel:`, `hard:`, `stereo_width:` |
| `:pluck` | Karplus-Strong string | `noise_amp:`, `max_delay_time:` |
| `:blade` | Blade Runner strings | `vibrato_rate:`, `vibrato_depth:` |

## Utility
| Synth | Sound | Special Opts |
|-------|-------|--------------|
| `:sound_in` | Live audio input | `input:` (1=L, 2=R) |

---

## v4+ Synths (check UI for full opts)

These synths were added in newer Sonic Pi versions:

| Synth | Sound | Version |
|-------|-------|---------|
| `:kalimba` | Thumb piano | v3.3+ |
| `:bass_foundation` | Sub bass | v4+ |
| `:bass_highend` | Bright bass | v4+ |
| `:chipbass` | Chiptune bass | v4+ |
| `:chiplead` | Chiptune lead | v4+ |
| `:chipnoise` | Chiptune noise | v4+ |
| `:tech_saws` | Tech house saws | v4+ |
| `:winwood_lead` | Classic lead | v4+ |

*Check Sonic Pi's built-in help for full documentation on these.*

---

## Common Opt Patterns

**Envelope** (all synths): `attack:`, `decay:`, `sustain:`, `release:`, `attack_level:`, `sustain_level:`

**Filter** (many synths): `cutoff:` (MIDI note 0-130), `res:` (0-1)

**Slides** (real-time control): Most opts support `_slide` suffix for smooth transitions
```ruby
s = play :e3, cutoff: 60
control s, cutoff: 120, cutoff_slide: 2  # Sweep over 2 beats
```

For full parameter details, see `raw-docs/synths.md`
