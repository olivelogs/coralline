---
name: sonic-pi
description: Overview of the Sonic Pi documentation. Use for reference when creating music within the Sonic Pi live coding music environment. Ruby-based DSL for real-time audio synthesis. 
dependencies: ruby>=2.6.1
---

## Quick Start
```ruby
# Basic sound
play 60                    # MIDI note
play :C4                   # Note symbol
sample :bd_haus            # Drum sample

# With options
play :E4, amp: 0.8, pan: -0.5, release: 0.5
sample :loop_amen, rate: 0.8, amp: 1.2
```

## Live Loops (CRITICAL)
```ruby
live_loop :drums do
  sample :bd_haus
  sleep 1
end
```
**Rules:**
- MUST contain `sleep` or `sync` (infinite loop otherwise!)
- Names MUST be unique across the project
- Hot-swap: Edit and re-run to update in real-time

## Timing
```ruby
use_bpm 120              # Set tempo
sleep 0.5                # Wait (in beats)
wait 0.5                 # Alias for sleep
```

## Synths
```ruby
use_synth :tb303         # Change default synth
play :E2, cutoff: 80, res: 0.8

# Per-note synth
synth :fm, note: :C4, divisor: 2, depth: 3
```

### Common Synth Opts
| Opt | What | Default |
|-----|------|---------|
| `note:` | MIDI note (0-127) or symbol | 52 |
| `amp:` | Volume | 1 |
| `pan:` | Stereo (-1 to 1) | 0 |
| `attack:` | Fade in time (beats) | 0 |
| `decay:` | Fade to sustain (beats) | 0 |
| `sustain:` | Hold time (beats) | 0 |
| `release:` | Fade out time (beats) | 1 |
| `cutoff:` | Filter freq (MIDI, 0-130) | varies |
| `res:` | Filter resonance (0-1) | varies |

See `references/synths-quick.md` for all synths.

## Samples
```ruby
sample :bd_haus
sample :loop_amen, beat_stretch: 4  # Fit to 4 beats
sample :ambi_drone, rate: -1        # Reverse
sample :loop_amen, onset: 3         # Play 4th slice
```

### Key Sample Opts
| Opt | What |
|-----|------|
| `rate:` | Speed (negative = reverse) |
| `beat_stretch:` | Stretch to N beats |
| `rpitch:` | Pitch shift (semitones) |
| `start:`, `finish:` | Play portion (0-1) |
| `onset:` | Play specific transient |

See `references/samples-quick.md` for all samples.

## FX
```ruby
with_fx :reverb do
  play :C4
end

with_fx :lpf, cutoff: 80 do
  sample :loop_amen, beat_stretch: 4
end

# Nested (inner applies first)
with_fx :reverb, room: 0.8 do
  with_fx :distortion, distort: 0.5 do
    play :E3
  end
end
```

See `references/fx-quick.md` for all effects.

## Real-time Control
```ruby
# Control synth
s = play :E3, sustain: 8, cutoff: 60
sleep 2
control s, cutoff: 120, cutoff_slide: 1

# Control FX
with_fx :lpf, cutoff: 130 do |fx|
  live_loop :growl do
    control fx, cutoff: rrand(60, 130)
    sample :bass_dnb_f
    sleep 0.5
  end
end
```

## Sync & Cue
```ruby
live_loop :metro do
  cue :tick
  sleep 1
end

live_loop :synced do
  sync :tick
  sample :bd_haus
end
```

## Rings & Patterns
```ruby
# Ring = circular array
notes = ring(:C4, :E4, :G4)
notes[0]    # :C4
notes[3]    # :C4 (wraps)

# tick advances through ring
live_loop :arp do
  play notes.tick
  sleep 0.25
end

# look peeks without advancing
notes.look

# Reverse, shuffle, etc
notes.reverse
notes.shuffle
notes.pick(3)  # Random 3 elements

# Built-in rings
scale(:C4, :minor_pentatonic)  # Scale as ring
chord(:E3, :minor7)            # Chord as ring
```

## Randomness (Deterministic)
```ruby
rrand(60, 100)        # Float between 60-100
rrand_i(0, 10)        # Integer 0-10
dice(6)               # 1-6
one_in(4)             # true 25% of time
choose([1, 2, 3])     # Random element

# Reset random seed for reproducibility
use_random_seed 42
```

## OSC (for external control / Chime)
```ruby
# Send OSC
osc "/robot/move", 1, 0.5

# Receive OSC
live_loop :osc_listener do
  msg = sync "/osc*/trigger"
  sample :bd_haus
end

# Configure destination
use_osc "localhost", 4560
```

## MIDI
```ruby
# Output
midi :C4
midi_cc 1, 64        # CC message

# Input
live_loop :midi_in do
  n, v = sync "/midi*/note_on"
  play n, amp: v / 127.0
end
```

## State (cross-loop communication)
```ruby
set :my_val, 100
get[:my_val]  # => 100

# Time-safe get
get(:my_val, 0)  # Default if not set
```

## Common Patterns

### Euclidean Rhythms
```ruby
live_loop :euclidean do
  sleep 0.25
  sample :bd_haus if spread(3, 8).tick
end
```

### Probability
```ruby
live_loop :maybe do
  sample :bd_haus if one_in(2)
  sample :sn_dolf if rand < 0.3
  sleep 0.5
end
```

### Layered Loops
```ruby
live_loop :kick do
  sample :bd_haus
  sleep 1
end

live_loop :hat, sync: :kick do
  sample :drum_cymbal_closed
  sleep 0.5
end
```

## Gotchas
- **Forgotten sleep**: Loop freezes Sonic Pi
- **Name collision**: Two `live_loop :foo` = chaos
- **Thread scope**: Variables don't share between loops (use `set`/`get`)
- **FX in loops**: Define `with_fx` OUTSIDE `live_loop` if possible for performance
- **Sample rate**: High `rate:` values = chipmunk, negative = reverse

## References
- `references/synths-quick.md` - All synths with key opts
- `references/fx-quick.md` - All effects with key opts
- `references/samples-quick.md` - All samples by category
- `references/chords-scales.md` - Chord and scale types
- `assets/examples.md` - Simple and complex usage examples
