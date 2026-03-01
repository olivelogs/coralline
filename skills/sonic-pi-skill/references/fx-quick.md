# FX Quick Reference

All FX support: `amp:`, `mix:` (0=dry, 1=wet), `pre_amp:`

## Reverb & Space
| FX | Effect | Key Opts |
|----|--------|----------|
| `:reverb` | Room reverb | `room:`, `damp:`, mix default 0.4 |
| `:gverb` | Large space reverb | `room:`, `spread:`, `damp:`, `release:` |

## Delay & Echo  
| FX | Effect | Key Opts |
|----|--------|----------|
| `:echo` | Repeating delay | `phase:` (time), `decay:`, `max_phase:` |

## Filters
| FX | Effect | Key Opts |
|----|--------|----------|
| `:lpf` | Low pass (remove highs) | `cutoff:` |
| `:hpf` | High pass (remove lows) | `cutoff:` |
| `:bpf` | Band pass | `centre:`, `res:` |
| `:rlpf` | Resonant low pass | `cutoff:`, `res:` |
| `:rhpf` | Resonant high pass | `cutoff:`, `res:` |
| `:rbpf` | Resonant band pass | `centre:`, `res:` |
| `:band_eq` | EQ boost/cut | `freq:`, `res:`, `db:` |

**Normalised versions** (`nlpf`, `nhpf`, `nbpf`, `nrlpf`, `nrhpf`, `nrbpf`): Same but compensate for volume loss.

## Distortion
| FX | Effect | Key Opts |
|----|--------|----------|
| `:distortion` | Overdrive | `distort:` (0-1) |
| `:bitcrusher` | Lo-fi, retro | `bits:`, `sample_rate:` |
| `:krush` | Aggressive crush | `gain:`, `cutoff:`, `res:` |
| `:tanh` | Soft saturation | `krunch:` |

## Modulation
| FX | Effect | Key Opts |
|----|--------|----------|
| `:flanger` | Swooshy flange | `phase:`, `depth:`, `feedback:` |
| `:wobble` | Filter wobble | `phase:`, `cutoff_min:`, `cutoff_max:`, `wave:` |
| `:slicer` | Rhythmic gating | `phase:`, `wave:`, `probability:` |
| `:panslicer` | Rhythmic L/R pan | `phase:`, `wave:` |
| `:ixi_techno` | Techno resonance | `phase:`, `cutoff_min:`, `cutoff_max:` |

**Wave values**: 0=saw, 1=pulse, 2=triangle, 3=sine, 4=cubic

## Pitch
| FX | Effect | Key Opts |
|----|--------|----------|
| `:pitch_shift` | Pitch up/down | `pitch:` (semitones), `window_size:` |
| `:whammy` | Whammy bar bend | `transpose:` |
| `:octaver` | Add octaves | `super_amp:`, `sub_amp:` |
| `:ring_mod` | Ring modulation | `freq:`, `mod_amp:` |

## Dynamics
| FX | Effect | Key Opts |
|----|--------|----------|
| `:compressor` | Dynamic compression | `threshold:`, `slope_above:`, `clamp_time:`, `relax_time:` |
| `:normaliser` | Normalize volume | `level:` |
| `:level` | Simple volume | — |

## Other
| FX | Effect | Key Opts |
|----|--------|----------|
| `:pan` | Stereo position | `pan:` |
| `:vowel` | Vowel formant | `vowel_sound:` (1-5), `voice:` (0-4) |

---

## FX Nesting Pattern

FX are applied from innermost outward:
```ruby
with_fx :reverb do
  with_fx :distortion do
    play :e3  # Distortion first, then reverb
  end
end
```

## Real-time Control
```ruby
with_fx :lpf, cutoff: 130 do |fx|
  loop do
    control fx, cutoff: rrand(60, 130)
    sample :bd_haus
    sleep 0.5
  end
end
```

For full parameter details, see `raw-docs/fx.md`
