# Code Example Reference

# Ambient_experiment by Darin Wilson
> The piece consists of three long loops, each of which plays one of two randomly selected pitches. Each note has different attack, release and sleep values, so that they move in and out of phase with each other. This can play for quite awhile without repeating itself. Ethereal, ambient, bright. might play in a spa lobby.
```ruby
use_synth :hollow
with_fx :reverb, mix: 0.7 do
  live_loop :note1 do
    play choose([:D4,:E4]), attack: 6, release: 6
    sleep 8
  end
  live_loop :note2 do
    play choose([:Fs4,:G4]), attack: 4, release: 5
    sleep 10
  end
  live_loop :note3 do
    play choose([:A4, :Cs5]), attack: 5, release: 5
    sleep 11
  end
end
```

# Chord_inversions by Adrian Cheater
> bright, melodic, great for looping & layering. Very adaptable.
```ruby
[1, 3, 6, 4].each do |d|
  (range -3, 3).each do |i|
    play_chord (chord_degree d, :c, :major, 3, invert: i)
    sleep 0.25
  end
end
```

# Filtered_dnb by Sam Aaron
> fast, aggressive, fades in and out. the rrand cutoff dulls the loop to a background at random points, good for bringing in energy.
```ruby
use_sample_bpm :loop_amen

with_fx :rlpf, cutoff: 10, cutoff_slide: 4 do |c|
  live_loop :dnb do
    sample :bass_dnb_f, amp: 5
    sample :loop_amen, amp: 5
    sleep 1
    control c, cutoff: rrand(40, 120), cutoff_slide: rrand(1, 4)
  end
end
```

# Fm_noise
> this sounds like alien space sounds doing a band warmup on stage. should not stand alone for long.
```ruby
use_synth :fm
live_loop :sci_fi do
  p = play (chord :Eb3, :minor).choose - [0, 12, -12].choose, divisor: 0.01, div_slide: rrand(0, 10), depth: rrand(0.001, 2), attack: 0.01, release: rrand(0, 5), amp: 0.5
  control p, divisor: rrand(0.001, 50)
  sleep [0.5, 1, 2].choose
end
```

# Jungle by Sam Aaron
> speed, energy. this *sounds* sweaty. could be a good breakbeat.
```ruby
use_bpm 50

with_fx :lpf, cutoff: 90 do
  with_fx :reverb, mix: 0.5 do
    with_fx :compressor, pre_amp: 40 do
      with_fx :distortion, distort: 0.4 do
        live_loop :jungle do
          use_random_seed 667
          4.times do
            sample :loop_amen, beat_stretch: 1, rate: [1, 1, 1, -1].choose / 2.0, finish: 0.5, amp: 0.5
            sample :loop_amen, beat_stretch: 1
            sleep 1
          end
        end
      end
    end
  end
end
```

# Reich_phase
> Music as a process. The superimpositions of pattern form sub-melodies, often spontaneously due to echo, resonance, dynamics, and tempo, and the general perception of the listener. The rhythmic perception during phasing can vary considerably, from being very simple (in-phase), to complex and intricate. Sounds great with different short-cutoff synths, like `:piano` or `:beep`.
```ruby
use_synth :piano
notes = (ring :E4, :Fs4, :B4, :Cs5, :D5, :Fs4, :E4, :Cs5, :B4, :Fs4, :D5, :Cs5)

live_loop :slow do
  play notes.tick, release: 0.1
  sleep 0.3
end

live_loop :faster do
  play notes.tick, release: 0.1
  sleep 0.295
end
```

# Ocean by Sam Aaron
> literally sounds like ocean waves on the beach.
```ruby
with_fx :reverb, mix: 0.5 do
  live_loop :oceans do
    s = synth [:bnoise, :cnoise, :gnoise].choose, amp: rrand(0.5, 1.5), attack: rrand(0, 4), sustain: rrand(0, 2), release: rrand(1, 5), cutoff_slide: rrand(0, 5), cutoff: rrand(60, 100), pan: rrand(-1, 1), pan_slide: rrand(1, 5), amp: rrand(0.5, 1)
    control s, pan: rrand(-1, 1), cutoff: rrand(60, 110)
    sleep rrand(2, 4)
  end
end
```

# Acid by Sam Aaron
> At default 60bpm, this is a chill beat with interesting beeps. `use_random_seed` locks in the melodic combination. This is a very fun beat, gets boring after a few loops on its own.
```ruby
use_debug false
load_sample :bd_fat
8.times do
  sample :bd_fat, amp: (line 0, 5, steps: 8).tick
  sleep 0.5
end
live_loop :drums do
  sample :bd_fat, amp: 5
  sleep 0.5
end
live_loop :acid do
  cue :foo
  4.times do |i|
    use_random_seed 667
    16.times do
      use_synth :tb303
      play chord(:e3, :minor).choose, attack: 0, release: 0.1, cutoff: rrand_i(50, 90) + i * 10
      sleep 0.125
    end
  end
  cue :bar
  32.times do |i|
    use_synth :tb303
    play chord(:a3, :minor).choose, attack: 0, release: 0.05, cutoff: rrand_i(70, 98) + i, res: rrand(0.9, 0.95)
    sleep 0.125
  end
  cue :baz
  with_fx :reverb, mix: 0.3 do |r|
    32.times do |m|
      control r, mix: 0.3 + (0.5 * (m.to_f / 32.0)) unless m == 0 if m % 8 == 0
      use_synth :prophet
      play chord(:e3, :minor).choose, attack: 0, release: 0.08, cutoff: rrand_i(110, 130)
      sleep 0.125
    end
  end
  cue :quux
  in_thread do
    use_random_seed 668
    with_fx :echo, phase: 0.125 do
      16.times do
        use_synth :tb303
        play chord(:e3, :minor).choose, attack: 0, release: 0.1, cutoff: rrand(50, 100)
        sleep 0.25
      end
    end
  end

  sleep 4
end
```

# Cloud_beat by SonicPit
> mellow, finger-tappy. this is a great loop that remains interesting for a long time. 
```ruby
# Taken from "Beats basteln wie die Großen" c't 13/2017
use_bpm 100

# HISS
live_loop :hiss_loop do
  sample :vinyl_hiss, amp: 2
  sleep sample_duration :vinyl_hiss
end

# HIHAT
define :hihat do
  use_synth :pnoise
  with_fx :hpf, cutoff: 120 do
    play release: 0.01, amp: 13
  end
end
live_loop :hihat_loop do
  divisors = ring 2, 4, 2, 2, 2, 2, 2, 6
  divisors.tick.times do
    hihat
    sleep 1.0 / divisors.look
  end
end
# SNARE
live_loop :snare_loop do
  sleep ring(2.5, 3)[tick]
  with_fx :lpf, cutoff: 100 do
    sample :sn_dub, sustain: 0, release: 0.05, amp: 3
  end
  sleep ring(1.5, 1)[look]
end
# BASSDRUM
define :bassdrum do |note1, duration, note2 = note1|
  use_synth :sine
  with_fx :hpf, cutoff: 100 do
    play note1 + 24, amp: 40, release: 0.01
  end
  with_fx :distortion, distort: 0.1, mix: 0.3 do
    with_fx :lpf, cutoff: 26 do
      with_fx :hpf, cutoff: 55 do
        bass = play note1, amp: 85, release: duration, note_slide: duration
        control bass, note: note2
      end
    end
  end
  sleep duration
end
live_loop :bassdrum_schleife do
  bassdrum 36, 1.5
  if bools(0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0)[tick]
    bassdrum 36, 0.5, 40
    bassdrum 38, 1, 10
  else
    bassdrum 36, 1.5
  end
  bassdrum 36, 1.0, ring(10, 10, 10, 40)[look]
end
# CHORD CONTROL
# This part provides two rings called "chord_high" and "chord_low".
# They always contain the "permitted" notes in order that everything will be in tune.
# You can use them in other live loops to select notes.
chord_1 = chord :c4, :maj9, num_octaves: 2
chord_2 = chord :es4, :maj9, num_octaves: 2
chord_3 = chord :b3, :maj9, num_octaves: 2
chord_4 = chord :d4, :maj9, num_octaves: 2

chord_low_1 = chord :c2, :maj9
chord_low_2 = chord :es2, :maj9
chord_low_3 = chord :b1, :maj9
chord_low_4 = chord :d2, :maj9

chord_high = chord_1
chord_low = chord_low_1

live_loop :chord_selector, delay: -0.5 do
  chord_high = (knit(chord_1, 2, chord_2, 2, chord_3, 4,chord_4, 4)).tick
  chord_low = (knit(chord_low_1, 2, chord_low_2, 2, chord_low_3, 4, chord_low_4, 4)).look
  sleep 8
end

# SPHERES
define :chord_player do |the_chord|
  use_synth :blade
  the_chord.each do |note|
    play note, attack: rand(4), release: rand(6..8), cutoff: rand(50..85), vibrato_rate: rand(0.01..2), amp: 0.55
  end
end

with_fx :reverb, room: 0.99, mix: 0.7 do
  live_loop :chord_loop do
    chord_player chord_high.pick(6)
    chord_player chord_low.take(3)
    sleep 8
  end
end
```

# Monday_blues by Sam Aaron
> dancing poorly in the kitchen, synth-punk vibe. non-performative but belongs on stage.
```ruby
use_debug false
load_samples [:drum_heavy_kick, :drum_snare_soft]
live_loop :drums do
  puts "slow drums"
  6.times do
    sample :drum_heavy_kick, rate: 0.8
    sleep 0.5
  end
  
  puts "fast drums"
  8.times do
    sample :drum_heavy_kick, rate: 0.8
    sleep 0.125
  end
end

live_loop :synths, delay: 6 do
  puts "how does it feel?"
  use_synth :mod_saw
  use_synth_defaults amp: 0.5, attack: 0, sustain: 1, release: 0.25, mod_range: 12, mod_phase: 0.5, mod_invert_wave: 1
  notes = (ring :F, :C, :D, :D, :G, :C, :D, :D)
  notes.each do |n|
    tick
    play note(n, octave: 1), cutoff: (line 90, 130, steps: 16).look
    play note(n, octave: 2), cutoff: (line 90, 130, steps: 32).look
    sleep 1
  end
end
live_loop :snare, delay: 12.5 do
  sample :drum_snare_soft
  sleep 1
end
```

# Tilburg_2 by Sam Aaron
> high energy, smooth, groovy. `:fietsen` adds a very interesting high sound.
```ruby
use_debug false
load_samples :guit_em9, :bd_haus

live_loop :low do
  tick
  synth :zawa, wave: 1, phase: 0.25, release: 5, note: (knit :e1, 12, :c1, 4).look, cutoff: (line 60, 120, steps: 6).look
  sleep 4
end

with_fx :reverb, room: 1 do
  live_loop :lands, auto_cue: false do
    use_synth :dsaw
    use_random_seed 310003
    ns = (scale :e2, :minor_pentatonic, num_octaves: 4).take(4)
    16.times do
      play ns.choose, detune: 12, release: 0.1, amp: 2, amp: rand + 0.5, cutoff: rrand(70, 120), amp: 2
      sleep 0.125
    end
  end
end

live_loop :fietsen do
  sleep 0.25
  sample :guit_em9, rate: -1
  sleep 7.75
end

live_loop :tijd do
  sample :bd_haus, amp: 2.5, cutoff: 100
  sleep 0.5
end

live_loop :ind do
  sample :loop_industrial, beat_stretch: 1
  sleep 1
end
```
