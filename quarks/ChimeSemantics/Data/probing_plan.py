# Synth Probing Plan — Parameters That Matter
# March 7, 2026

# These are the timbral params per synth — the ones that change
# what the sound *sounds like*, not where/when it plays.
# Excluding: freq, span, speed, speedFreq (transport/spatial)
# Excluding: superfm (too many params — do presets instead)

synths_to_probe = {
    "supervibe": {
        "params": ["decay", "detune", "modamp", "modfreq", "velocity"],
        "note": 0,  # C5
        "sustain": 2.0,
        "description": "Warm bell-like vibraphone. Our most-used synth."
    },
    "supersaw": {
        "params": ["decay", "lfo", "pitch1", "rate", "resonance", "semitone", "voice"],
        "note": 0,
        "sustain": 2.0,
        "description": "Aggressive sawtooth. Lots of timbral range."
    },
    "superpiano": {
        "params": ["detune", "muffle", "stereo", "velocity"],
        "note": 0,
        "sustain": 2.0,
        "description": "Piano. Muffle is inverse brightness."
    },
    "supermandolin": {
        "params": ["detune"],
        "note": 0,
        "sustain": 2.0,
        "description": "Plucked string. Few timbral params."
    },
    "supergong": {
        "params": ["decay", "voice"],
        "note": 0,
        "sustain": 3.0,
        "description": "Metallic resonant. Voice changes character significantly."
    },
    "superhammond": {
        "params": ["decay", "perc", "percf", "vibrato", "voice", "vrate"],
        "note": 0,
        "sustain": 2.0,
        "description": "Organ. Rich param space."
    },
    "superpwm": {
        "params": ["decay", "lfo", "pitch1", "rate", "resonance", "semitone", "voice"],
        "note": 0,
        "sustain": 2.0,
        "description": "Pulse width modulation. Similar params to supersaw."
    },
    "superfork": {
        "params": [],  # No timbral params beyond transport!
        "note": 0,
        "sustain": 2.0,
        "description": "Tuning fork. Pure. Skip probing."
    },
    "superhex": {
        "params": ["rate"],
        "note": 0,
        "sustain": 2.0,
        "description": "Hexagonal waveguide. Rate affects timbre."
    },
    "superprimes": {
        "params": ["detune", "rate", "voice"],
        "note": 0,
        "sustain": 2.0,
        "description": "Prime number harmonics. Unusual timbre."
    },
    "supernoise": {
        "params": ["pitch1", "rate", "resonance", "slide", "voice"],
        "note": 0,
        "sustain": 2.0,
        "description": "Filtered noise. All texture, no pitch."
    },
    "superkick": {
        "params": ["decay", "pitch1"],
        "note": 0,
        "sustain": 1.0,
        "description": "Bass drum."
    },
    "supersnare": {
        "params": ["decay"],
        "note": 0,
        "sustain": 1.0,
        "description": "Snare drum."
    },
    "superhat": {
        "params": [],  # n changes character but it's sample selection
        "note": 0,
        "sustain": 0.5,
        "description": "Hi-hat. Skip probing."
    },
    # --- added 2026-03-08 ---
    "super808": {
        "params": ["rate", "voice"],
        "note": 3,  # n controls chirp freq, use nonzero
        "sustain": 1.0,
        "description": "808-ish kick. Rate controls filter sweep, voice controls sinewave feedback."
    },
    "superclap": {
        "params": ["delay", "pitch1", "rate"],
        "note": 0,
        "sustain": 1.0,
        "description": "Hand clap. Delay controls echo spread, pitch1 scales bandpass."
    },
    "supersiren": {
        "params": [],  # No timbral params, just pitch sweep
        "note": 0,
        "sustain": 2.0,
        "description": "Siren. Skip probing."
    },
    "supersquare": {
        "params": ["decay", "lfo", "pitch1", "rate", "resonance", "semitone", "voice"],
        "note": 0,
        "sustain": 2.0,
        "description": "Moog-inspired square wave. Pulse width via voice."
    },
    "supercomparator": {
        "params": ["decay", "lfo", "pitch1", "rate", "resonance", "voice"],
        "note": 0,
        "sustain": 2.0,
        "description": "Comparator synth. Voice scales harmonics, gets breathier higher."
    },
    "superchip": {
        "params": ["slide", "pitch2", "pitch3", "voice", "rate"],
        "note": 0,
        "sustain": 2.0,
        "description": "Atari ST emulation. 3 oscillators, chiptune character."
    },
    "superhoover": {
        "params": ["slide", "decay"],
        "note": 0,
        "sustain": 2.0,
        "description": "Hoover/Dominator. Slide controls pitch glide direction."
    },
    "superzow": {
        "params": ["decay", "slide", "detune"],
        "note": 0,
        "sustain": 2.0,
        "description": "Phased saws. Slide controls phase movement speed."
    },
    "superstatic": {
        "params": [],  # No timbral params
        "note": 0,
        "sustain": 2.0,
        "description": "Impulse noise. Skip probing."
    },
    "supergrind": {
        "params": ["detune", "voice", "rate"],
        "note": 0,
        "sustain": 2.0,
        "description": "Crunchy feedback loop. From synthdef.art."
    },
    "superwavemechanics": {
        "params": ["detune", "voice", "resonance"],
        "note": 0,
        "sustain": 2.0,
        "description": "Resonant noise. Voice whitens/colors, resonance affects reverb."
    },
    "supertron": {
        "params": ["voice", "detune"],
        "note": 0,
        "sustain": 2.0,
        "description": "Feedback PWM. Voice controls pulse width."
    },
    "superreese": {
        "params": ["detune", "voice"],
        "note": 0,
        "sustain": 2.0,
        "description": "Reese bass. Detuned saws through resonant filters."
    },
    "soskick": {
        "params": ["pitch1", "voice", "pitch2", "speed"],
        "note": 0,
        "sustain": 1.0,
        "description": "SOS kick drum. Pitch1 controls mod freq, voice controls mod phase."
    },
    "soshats": {
        "params": ["pitch1", "resonance"],
        "note": 0,
        "sustain": 0.5,
        "description": "SOS hi-hat. Pitch1 controls oscillator modulation."
    },
    "sostoms": {
        "params": ["voice"],
        "note": 0,
        "sustain": 1.0,
        "description": "SOS tom. Voice controls PM modulation depth."
    },
    "sossnare": {
        "params": ["voice", "semitone", "pitch1", "resonance"],
        "note": 0,
        "sustain": 1.0,
        "description": "SOS snare. Rich param space for a drum."
    },
}

# Total unique params to sweep (excluding superfm):
# ~75 param-synth combinations
# At 11 steps each = ~825 recordings
# At ~2.5 seconds each (note + pause) = ~35 minutes of recording
# Analysis is fast (librosa processes much faster than realtime)

# For each param sweep:
# - 11 steps: 0.0, 0.1, 0.2, ... 0.9, 1.0 (normalized to actual range)
#   Actual ranges pulled from SynthDef source code
# - Hold all other params at default
# - Record 2s of audio per step
# - Analyze with librosa (same pipeline as giggle.json)
