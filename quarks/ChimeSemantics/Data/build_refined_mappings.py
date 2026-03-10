"""
Build refined_mappings.json from the probe analysis auto-suggestions.

Turns out the Python analyzer's auto-suggestions are more accurate than 
what Qwen 3B produced (bless its heart). This script converts them 
directly into the ChimeSemantics mapping format.

Qwen's output is not used. If we want a local model to refine further,
we'd need 7B+ or a fine-tuned model.

Usage:
    python build_refined_mappings.py

March 2026 — Olive + Claude
"""

import json
import os

DATA_DIR = os.path.dirname(__file__)
PROFILES_PATH = os.path.join(DATA_DIR, "probes_profiles.json")
OUTPUT_PATH = os.path.join(DATA_DIR, "refined_mappings.json")

# The actual param ranges from probe_synths.scd
# Format: synth -> param -> (min, max)
PARAM_RANGES = {
    "supervibe": {"decay": (0, 2), "detune": (0, 0.5), "modamp": (0, 1), "modfreq": (0, 20), "velocity": (0, 1)},
    "supersaw": {"decay": (0, 2), "lfo": (0, 1), "pitch1": (0, 3), "rate": (0, 10), "resonance": (0, 1), "semitone": (0, 12), "voice": (0, 1)},
    "superpiano": {"detune": (0, 0.5), "muffle": (0, 1), "stereo": (0, 1), "velocity": (0, 1)},
    "supermandolin": {"detune": (0, 0.5)},
    "supergong": {"decay": (0, 3), "voice": (0, 1)},
    "superhammond": {"decay": (0, 2), "perc": (0, 1), "percf": (0, 1), "vibrato": (0, 1), "voice": (0, 1), "vrate": (0, 10)},
    "superpwm": {"decay": (0, 2), "lfo": (0, 1), "pitch1": (0, 3), "rate": (0, 10), "resonance": (0, 1), "semitone": (0, 12), "voice": (0, 1)},
    "superhex": {"rate": (0, 10)},
    "superprimes": {"detune": (0, 0.5), "rate": (0, 10), "voice": (0, 1)},
    "supernoise": {"pitch1": (0, 3), "rate": (0, 10), "resonance": (0, 1), "slide": (0, 1), "voice": (0, 1)},
    "superkick": {"decay": (0, 2), "pitch1": (0, 3)},
    "supersnare": {"decay": (0, 2)},
    "super808": {"rate": (0.1, 5), "voice": (0, 1)},
    "superclap": {"delay": (0, 3), "pitch1": (0.5, 3), "rate": (0.1, 5)},
    "supersquare": {"decay": (0, 2), "lfo": (0, 1), "pitch1": (0, 3), "rate": (0, 10), "resonance": (0, 1), "semitone": (0, 12), "voice": (0.01, 0.99)},
    "supercomparator": {"decay": (0, 2), "lfo": (0, 1), "pitch1": (0, 3), "rate": (0, 10), "resonance": (0, 1), "voice": (0, 1)},
    "superchip": {"slide": (0, 100), "pitch2": (1, 5), "pitch3": (1, 5), "voice": (0, 1), "rate": (0, 5)},
    "superhoover": {"slide": (-4, 4), "decay": (0, 1)},
    "superzow": {"decay": (0, 1), "slide": (0.1, 5), "detune": (0, 3)},
    "supergrind": {"detune": (0, 10), "voice": (0, 1), "rate": (0, 5)},
    "superwavemechanics": {"detune": (0, 1.5), "voice": (0, 1), "resonance": (0, 1)},
    "supertron": {"voice": (0, 1), "detune": (0, 10)},
    "superreese": {"detune": (0, 5), "voice": (0, 2)},
    "soskick": {"pitch1": (0, 2000), "voice": (0, 5), "pitch2": (0, 0.5), "speed": (0.01, 1)},
    "soshats": {"pitch1": (0, 500), "resonance": (0, 1)},
    "sostoms": {"voice": (0, 3)},
    "sossnare": {"voice": (0, 3), "semitone": (0, 1), "pitch1": (500, 5000), "resonance": (0, 1)},
}

DIMENSIONS = ["brightness", "warmth", "texture", "movement", "space", "weight", "attack"]
MIN_CONFIDENCE = 0.4


def build_mappings():
    with open(PROFILES_PATH) as f:
        profiles = json.load(f)

    mappings = {}

    for synth_name in sorted(profiles.keys()):
        if synth_name == "test":
            continue

        synth_profiles = profiles[synth_name]
        synth_ranges = PARAM_RANGES.get(synth_name, {})

        synth_mapping = {dim: [] for dim in DIMENSIONS}
        synth_mapping["pitch_controls"] = []
        synth_mapping["needs_review"] = []

        for param_name, profile in synth_profiles.items():
            suggestions = profile.get("suggested_semantics", [])
            pitch_drift = profile.get("pitch_drift", {})
            param_range = synth_ranges.get(param_name, (0, 1))

            # Check for pitch drift
            if pitch_drift and pitch_drift.get("drifts"):
                semitones = pitch_drift.get("range_semitones", 0)
                synth_mapping["pitch_controls"].append({
                    "param": param_name,
                    "drift_semitones": semitones,
                })
                # If drift > 2 semitones, skip timbral mappings for this param
                if semitones > 2:
                    continue

            for suggestion in suggestions:
                dim = suggestion.get("dimension")
                if dim == "PITCH_WARNING":
                    continue
                if dim not in DIMENSIONS:
                    continue

                confidence = suggestion.get("confidence", 0)
                direction = suggestion.get("direction", "positive")

                if confidence < MIN_CONFIDENCE:
                    synth_mapping["needs_review"].append(
                        f"{param_name} -> {dim} (confidence {confidence})"
                    )
                    continue

                # Determine outLo/outHi based on direction
                out_lo, out_hi = param_range
                if direction == "negative":
                    out_lo, out_hi = out_hi, out_lo  # Invert

                # Guess curve from the profile's feature correlation
                # Strong correlation (>0.85) = probably linear
                # Medium (0.5-0.85) = could be exp or lin
                # We default to lin — curve fitting needs listening tests
                curve = "lin"

                entry = {
                    "param": param_name,
                    "outLo": out_lo,
                    "outHi": out_hi,
                    "curve": curve,
                    "weight": round(confidence, 2),  # Use as initial weight, normalize later
                    "confidence": round(confidence, 2),
                }

                synth_mapping[dim].append(entry)

        # Normalize weights per dimension
        for dim in DIMENSIONS:
            entries = synth_mapping[dim]
            if not entries:
                continue

            total_weight = sum(e["weight"] for e in entries)
            if total_weight > 0:
                for e in entries:
                    e["weight"] = round(e["weight"] / total_weight, 3)

        # Clean up empty lists
        if not synth_mapping["pitch_controls"]:
            synth_mapping["pitch_controls"] = []
        if not synth_mapping["needs_review"]:
            synth_mapping["needs_review"] = []

        mappings[synth_name] = synth_mapping

    return mappings


def print_summary(mappings):
    print("=== REFINED MAPPINGS SUMMARY ===\n")

    total_mappings = 0
    total_pitch_controls = 0
    total_review = 0

    for synth in sorted(mappings.keys()):
        m = mappings[synth]
        dims_used = [d for d in DIMENSIONS if m[d]]
        n_mappings = sum(len(m[d]) for d in DIMENSIONS)
        n_pitch = len(m["pitch_controls"])
        n_review = len(m["needs_review"])

        total_mappings += n_mappings
        total_pitch_controls += n_pitch
        total_review += n_review

        dims_str = ", ".join(dims_used) if dims_used else "(none)"
        print(f"  {synth}: {n_mappings} mappings across [{dims_str}]")
        if n_pitch:
            pnames = [p["param"] for p in m["pitch_controls"]]
            print(f"    pitch controls: {', '.join(pnames)}")
        if n_review:
            print(f"    needs review: {n_review} items")

    print(f"\nTotals: {total_mappings} mappings, {total_pitch_controls} pitch controls, {total_review} needing review")


def main():
    mappings = build_mappings()

    with open(OUTPUT_PATH, 'w') as f:
        json.dump(mappings, f, indent=2)

    print(f"Wrote refined mappings to: {OUTPUT_PATH}")
    print(f"Synths: {len(mappings)}\n")
    print_summary(mappings)


if __name__ == "__main__":
    main()
