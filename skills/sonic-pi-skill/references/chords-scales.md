# Chords & Scales Reference

## Chord Function
```ruby
chord(:C4, :major)    # => ring of MIDI notes
chord(:E3, :minor7)   # Can use any root + type
play chord(:C4, :major)  # Play all notes
```

## Chord Types
**Triads:** `:major`, `:minor`, `:dim`, `:aug`
**Sevenths:** `:dom7`, `:major7`, `:minor7`, `:dim7`, `:aug7`
**Extended:** `:m7-5`, `:m7+5`, `:sus2`, `:sus4`, `:'7sus2'`, `:'7sus4'`
**Jazz:** `:m9`, `:maj9`, `:'9'`, `:m11`, `:'11'`, `:'13'`
**Other:** `:'6'`, `:m6`, `:add2`, `:add4`, `:add9`, `:madd2`, `:madd4`, `:madd9`

## Scale Function
```ruby
scale(:C4, :major)       # => ring of MIDI notes
scale(:D3, :minor, num_octaves: 2)  # Span 2 octaves
```

## Scale Types
**Common:** `:major`, `:minor`, `:minor_pentatonic`, `:major_pentatonic`
**Modes:** `:dorian`, `:phrygian`, `:lydian`, `:mixolydian`, `:locrian`, `:ionian`, `:aeolian`
**Blues/Jazz:** `:blues_major`, `:blues_minor`, `:whole_tone`, `:chromatic`
**World:** `:egyptian`, `:japanese`, `:chinese`, `:indian`, `:hungarian_minor`
**Other:** `:harmonic_minor`, `:melodic_minor`, `:diminished`, `:augmented`

## Pattern Examples
```ruby
# Arpeggiate chord
play_pattern_timed chord(:E3, :minor7), 0.25

# Walk scale
notes = scale(:C4, :minor_pentatonic)
live_loop :melody do
  play notes.choose
  sleep 0.25
end

# Ring tick through scale
live_loop :seq do
  play scale(:C3, :dorian).tick
  sleep 0.5
end
```
