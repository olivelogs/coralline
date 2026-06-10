# Coralline Studio Notebook

Notes left across sessions — by claudes, for the claudes that come after
(and for Olive). Discoveries, recipes, quirks, things made. Append-only;
each entry is a `##` heading. Committed to the repo: this is lineage,
not a log.

## 2026-06-09 — first field report on the new ears
_tags: perception, movement, supervibe, superhammond, open-question_

v0.1.7 gave `get_audio` the `perceived` block — the seven semantic
dimensions heard back from the bus. First chat instance to use it ran
their own tests within minutes of getting access. Two findings, relayed
via Olive:

- **Movement saturated at 1.0 on anything percussive**, even with a
  drone layered over it. The rms-variation term was too hot. Loosened in
  v0.1.8: `perceptionCal[\moveRmsCV]` 0.5 → 1.0, `\moveCentroidWt`
  0.6 → 0.65. If percussion still pins it, raise `moveRmsCV` further —
  it's live-tweakable: `CorallineAnalysis.perceptionCal[\moveRmsCV] = 1.4`.
- **Open question: `pitch_hz_median` reads supervibe an octave low and
  superhammond about two octaves low** vs. what was played. Possibly the
  Pitch UGen latching onto a subharmonic/fundamental that's really there
  (hammond is an organ — drawbars stack, the perceived pitch isn't the
  lowest partial). Worth testing across more synths before deciding if
  it's a bug or just how those instruments breathe. If you have a spare
  moment, play a known n on a few synths and compare.

Also true as of tonight: `recordings/` keeps wavs from `get_audio_clip`
(snippets-of-the-past), and this notebook is brand new — you're reading
entry one. Leave the place better documented than you found it.

— Claude (Code), findings relayed from a chat instance via Olive

## 2026-06-09 — the clock exists now (v0.1.9)
_tags: clock, timing, loops, recipe_

Loops are beats-native as of v0.1.9: `cycle_beats: 4` = one bar of 4/4 on
a shared clock (default 120 BPM). Things that are now possible and weren't:

- **Drops land on the downbeat.** Loop starts AND hot-swaps quantize to
  the bar by default — you can `loop_start` a layer mid-bar and it waits
  for the bar line. Check `get_state` for `beat_in_bar` / `next_bar_in_s`
  if you want to time something by hand.
- **`set_tempo` moves everything at once** — durations are musical, so
  running loops follow. Stepped calls make a workable ritardando. Meter
  changes (`beats_per_bar`) land on the next bar line.
- **`quant: 0` opts out** — free/textural layers that shouldn't grid.
- Polyrhythms got *better*, not obsolete: `cycle_beats: 3` against
  `cycle_beats: 4` now stays phase-locked to a shared downbeat every
  12 beats instead of drifting.

Untried as of tonight: meter changes mid-piece, tempo automation curves,
what a quantized hot-swap chain feels like as a build. Whoever tries
first, write it down.

— Claude (Code)
