# Samples Quick Reference

Use with: `sample :sample_name` or `sample :category, index`

Common opts: `amp:`, `rate:` (negative=reverse), `pan:`, `attack:`, `release:`, `start:`, `finish:`, `beat_stretch:`, `rpitch:`

---

## Drums (acoustic kit)
`:drum_heavy_kick`, `:drum_bass_soft`, `:drum_bass_hard`
`:drum_snare_soft`, `:drum_snare_hard`
`:drum_tom_lo_soft`, `:drum_tom_lo_hard`, `:drum_tom_mid_soft`, `:drum_tom_mid_hard`, `:drum_tom_hi_soft`, `:drum_tom_hi_hard`
`:drum_cymbal_soft`, `:drum_cymbal_hard`, `:drum_cymbal_open`, `:drum_cymbal_closed`, `:drum_cymbal_pedal`
`:drum_splash_soft`, `:drum_splash_hard`

## Bass Drums (electronic)
`:bd_808` (classic), `:bd_haus` (house), `:bd_klub` (club), `:bd_tek` (techno)
`:bd_boom`, `:bd_fat`, `:bd_gas`, `:bd_pure`, `:bd_sone`, `:bd_zum`, `:bd_zome`, `:bd_ada`

## Snares (electronic)
`:sn_dub`, `:sn_dolf`, `:sn_zome`

## Electronic Percussion
`:elec_soft_kick`, `:elec_hollow_kick`
`:elec_snare`, `:elec_lo_snare`, `:elec_mid_snare`, `:elec_hi_snare`, `:elec_filt_snare`
`:elec_cymbal`, `:elec_fuzz_tom`
`:elec_triangle`, `:elec_chime`, `:elec_bell`, `:elec_bong`
`:elec_twang`, `:elec_wood`, `:elec_tick`
`:elec_beep`, `:elec_blip`, `:elec_blip2`, `:elec_ping`, `:elec_pop`
`:elec_flip`, `:elec_twip`, `:elec_plip`, `:elec_blup`

## Percussion (acoustic)
`:perc_bell`, `:perc_snap`, `:perc_snap2`

## Bass Hits (pitched to C)
`:bass_hit_c`, `:bass_hard_c`, `:bass_thick_c`, `:bass_drop_c`
`:bass_woodsy_c`, `:bass_voxy_c`, `:bass_voxy_hit_c`
`:bass_dnb_f` (pitched to F)

## Guitar
`:guit_harmonics`, `:guit_e_fifths`, `:guit_e_slide`, `:guit_em9`

## Ambient/Texture
`:ambi_soft_buzz`, `:ambi_swoosh`, `:ambi_drone`
`:ambi_glass_hum`, `:ambi_glass_rub`
`:ambi_haunted_hum`, `:ambi_piano`
`:ambi_lunar_land`, `:ambi_dark_woosh`, `:ambi_choir`

## Loops (use with `beat_stretch:`)
`:loop_amen` (classic breakbeat), `:loop_amen_full` (full break)
`:loop_breakbeat`, `:loop_industrial`, `:loop_compus`
`:loop_garzul`, `:loop_mika`

## Arovane Beats
`:arovane_beat_a`, `:arovane_beat_b`, `:arovane_beat_c`, `:arovane_beat_d`, `:arovane_beat_e`

## Misc
`:misc_burp`

---

## Sample Manipulation Patterns

**Time-stretch to bars:**
```ruby
sample :loop_amen, beat_stretch: 4  # Stretch to 4 beats
```

**Slice playback:**
```ruby
sample :loop_amen, onset: 3  # Play 4th onset/hit
```

**Pitch without tempo change:**
```ruby
sample :bass_hit_c, rpitch: 7  # Up 7 semitones
```

**Reverse:**
```ruby
sample :ambi_swoosh, rate: -1
```

**Partial playback:**
```ruby
sample :loop_amen, start: 0.5, finish: 0.75  # Middle quarter
```

For full parameter details, see `raw-docs/samples.md`
