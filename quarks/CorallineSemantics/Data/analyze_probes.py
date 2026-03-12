"""
CorallineSemantics Probe Analyzer
==============================
Processes wav files from probe_synths.scd and generates
per-param perceptual profiles for semantic mapping.

Produces:
  - probes_analysis.json: full analysis of every recording
  - probes_profiles.json: summarized perceptual profiles per param
    (this is what Qwen reads to assign semantic dimensions)

Usage:
    python analyze_probes.py [--probe-dir PATH]

March 2026 — Olive + Claude
"""

import json
import os
import sys
import numpy as np

try:
    import librosa
except ImportError:
    print("pip install librosa --break-system-packages")
    sys.exit(1)


PROBE_DIR = os.path.join(os.path.dirname(__file__), "probes")
OUTPUT_DIR = os.path.dirname(__file__)

SEMANTIC_DIMENSIONS = [
    "brightness",   # spectral centroid trend
    "warmth",       # low-mid energy, harmonic richness
    "texture",      # spectral flatness (tonal vs noisy)
    "movement",     # spectral variance over time (modulation)
    "space",        # stereo width / decorrelation
    "weight",       # low frequency energy
    "attack",       # onset sharpness
]


# Note names for key detection
NOTE_NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']

# Key profiles (Krumhansl-Kessler) for key detection
MAJOR_PROFILE = np.array([6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88])
MINOR_PROFILE = np.array([6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17])


def detect_key(chroma_mean):
    """Detect musical key from mean chroma vector using Krumhansl-Kessler profiles."""
    best_corr = -1
    best_key = "unknown"
    best_mode = "unknown"

    for shift in range(12):
        rotated = np.roll(chroma_mean, -shift)

        # Test major
        corr_maj = float(np.corrcoef(rotated, MAJOR_PROFILE)[0, 1])
        if corr_maj > best_corr:
            best_corr = corr_maj
            best_key = NOTE_NAMES[shift]
            best_mode = "major"

        # Test minor
        corr_min = float(np.corrcoef(rotated, MINOR_PROFILE)[0, 1])
        if corr_min > best_corr:
            best_corr = corr_min
            best_key = NOTE_NAMES[shift]
            best_mode = "minor"

    return {
        "key": best_key,
        "mode": best_mode,
        "confidence": round(best_corr, 3),
    }


def compute_time_series(feature_array, n_segments=8):
    """Break a feature array into time segments and compute per-segment stats.

    Returns a list of per-segment means, capturing how the feature
    evolves over the duration of the note. 8 segments for a 2s note
    = 250ms resolution, enough to see attack/sustain/decay shape.
    """
    if len(feature_array) < n_segments:
        return [float(x) for x in feature_array]

    chunk_size = len(feature_array) // n_segments
    segments = []
    for i in range(n_segments):
        start = i * chunk_size
        end = start + chunk_size if i < n_segments - 1 else len(feature_array)
        segments.append(round(float(np.mean(feature_array[start:end])), 6))
    return segments


def classify_envelope_shape(time_series):
    """Classify the temporal envelope shape of a feature.

    Returns one of: 'flat', 'attack_decay', 'swell', 'rising', 'falling', 'complex'
    """
    if len(time_series) < 3:
        return "unknown"

    arr = np.array(time_series)
    total_range = np.max(arr) - np.min(arr)
    mean_val = np.mean(arr)

    # If the range is tiny relative to the mean, it's flat
    if mean_val > 0 and total_range / (mean_val + 1e-10) < 0.15:
        return "flat"

    peak_idx = np.argmax(arr)
    trough_idx = np.argmin(arr)
    n = len(arr)

    # Peak in first quarter, then decays
    if peak_idx <= n * 0.3 and arr[-1] < arr[0] * 0.7:
        return "attack_decay"
    # Builds to peak in second half
    if peak_idx >= n * 0.5:
        return "swell"
    # Monotonically rising
    if np.all(np.diff(arr) >= -total_range * 0.05):
        return "rising"
    # Monotonically falling
    if np.all(np.diff(arr) <= total_range * 0.05):
        return "falling"

    return "complex"


def analyze_wav(filepath, sr=44100):
    """Analyze a single wav file. Returns dict of features."""
    try:
        y, sr = librosa.load(filepath, sr=sr, mono=False)
    except Exception as e:
        return {"error": str(e)}

    # Handle stereo → analyze mono mix but keep stereo info
    if y.ndim == 2:
        stereo_corr = float(np.corrcoef(y[0], y[1])[0, 1]) if y.shape[0] == 2 else 1.0
        y_mono = librosa.to_mono(y)
    else:
        stereo_corr = 1.0
        y_mono = y

    # Skip silence
    rms_total = float(np.sqrt(np.mean(y_mono ** 2)))
    if rms_total < 0.001:
        return {"error": "silence", "rms": rms_total}

    # Spectral centroid (brightness indicator)
    cent = librosa.feature.spectral_centroid(y=y_mono, sr=sr)[0]

    # Spectral flatness (tonal vs noisy)
    flat = librosa.feature.spectral_flatness(y=y_mono)[0]

    # RMS energy over time
    rms = librosa.feature.rms(y=y_mono)[0]

    # Spectral bandwidth
    bw = librosa.feature.spectral_bandwidth(y=y_mono, sr=sr)[0]

    # Zero crossing rate (related to texture/noisiness)
    zcr = librosa.feature.zero_crossing_rate(y=y_mono)[0]

    # Chroma features (for key detection)
    chroma = librosa.feature.chroma_cqt(y=y_mono, sr=sr)
    chroma_mean = np.mean(chroma, axis=1)

    # Key detection
    key_info = detect_key(chroma_mean)

    # Pitch tracking via pYIN (robust fundamental frequency estimation)
    try:
        f0, voiced_flag, voiced_prob = librosa.pyin(
            y_mono, fmin=50, fmax=2000, sr=sr
        )
        # Filter to voiced frames
        voiced_f0 = f0[voiced_flag]
        if len(voiced_f0) > 0:
            pitch_mean = float(np.nanmean(voiced_f0))
            pitch_std = float(np.nanstd(voiced_f0))
            pitch_midi = float(librosa.hz_to_midi(pitch_mean))
        else:
            pitch_mean = 0.0
            pitch_std = 0.0
            pitch_midi = 0.0
    except Exception:
        pitch_mean = 0.0
        pitch_std = 0.0
        pitch_midi = 0.0

    # Low frequency energy ratio (weight)
    S = np.abs(librosa.stft(y_mono))
    freqs = librosa.fft_frequencies(sr=sr)
    low_mask = freqs < 300  # below 300Hz = "weight"
    mid_mask = (freqs >= 300) & (freqs < 2000)  # 300-2000Hz = "warmth"
    high_mask = freqs >= 2000  # above 2000Hz = "brightness"

    total_energy = float(np.sum(S ** 2))
    if total_energy > 0:
        low_ratio = float(np.sum(S[low_mask] ** 2) / total_energy)
        mid_ratio = float(np.sum(S[mid_mask] ** 2) / total_energy)
        high_ratio = float(np.sum(S[high_mask] ** 2) / total_energy)
    else:
        low_ratio = mid_ratio = high_ratio = 0.0

    # Onset strength (attack character)
    onset_env = librosa.onset.onset_strength(y=y_mono, sr=sr)
    onset_peak = float(np.max(onset_env)) if len(onset_env) > 0 else 0.0
    onset_mean = float(np.mean(onset_env)) if len(onset_env) > 0 else 0.0

    # HPSS for harmonic vs percussive
    H, P = librosa.decompose.hpss(S)
    h_energy = float(np.sum(H ** 2))
    p_energy = float(np.sum(P ** 2))
    perc_ratio = p_energy / (h_energy + p_energy + 1e-10)

    # === Time-series: how features evolve over the note's duration ===
    cent_ts = compute_time_series(cent)
    flat_ts = compute_time_series(flat)
    rms_ts = compute_time_series(rms)
    zcr_ts = compute_time_series(zcr)

    return {
        # Summary stats (same as before)
        "rms_mean": float(np.mean(rms)),
        "rms_std": float(np.std(rms)),
        "spectral_centroid_mean": float(np.mean(cent)),
        "spectral_centroid_std": float(np.std(cent)),
        "spectral_flatness_mean": float(np.mean(flat)),
        "spectral_flatness_std": float(np.std(flat)),
        "spectral_bandwidth_mean": float(np.mean(bw)),
        "zcr_mean": float(np.mean(zcr)),
        "low_energy_ratio": low_ratio,
        "mid_energy_ratio": mid_ratio,
        "high_energy_ratio": high_ratio,
        "onset_peak": onset_peak,
        "onset_mean": onset_mean,
        "percussive_ratio": float(perc_ratio),
        "stereo_correlation": stereo_corr,
        # Key detection
        "detected_key": key_info["key"],
        "detected_mode": key_info["mode"],
        "key_confidence": key_info["confidence"],
        # Pitch tracking
        "pitch_hz": round(pitch_mean, 2),
        "pitch_std_hz": round(pitch_std, 2),
        "pitch_midi": round(pitch_midi, 1),
        # Time-series (8-segment temporal evolution)
        "ts_centroid": cent_ts,
        "ts_flatness": flat_ts,
        "ts_rms": rms_ts,
        "ts_zcr": zcr_ts,
        # Envelope shape classification
        "envelope_centroid": classify_envelope_shape(cent_ts),
        "envelope_rms": classify_envelope_shape(rms_ts),
    }


def build_profile(param_analyses):
    """
    Given a list of (step_value, analysis_dict) for one param sweep,
    compute the perceptual profile: how does each audio feature change
    as the param goes from min to max?
    """
    if not param_analyses:
        return {"error": "no data"}

    # Sort by step value
    param_analyses.sort(key=lambda x: x[0])
    values = [a[0] for a in param_analyses]
    analyses = [a[1] for a in param_analyses]

    # For each feature, compute trend (correlation with param value)
    features = [
        "spectral_centroid_mean",
        "spectral_flatness_mean",
        "rms_mean",
        "low_energy_ratio",
        "mid_energy_ratio",
        "high_energy_ratio",
        "onset_peak",
        "percussive_ratio",
        "stereo_correlation",
        "spectral_centroid_std",  # modulation indicator
        "spectral_bandwidth_mean",
        "zcr_mean",
    ]

    profile = {}
    for feat in features:
        feat_values = []
        for a in analyses:
            if "error" not in a and feat in a:
                feat_values.append(a[feat])
            else:
                feat_values.append(None)

        # Filter out Nones
        valid = [(v, f) for v, f in zip(values, feat_values) if f is not None]
        if len(valid) < 3:
            profile[feat] = {"trend": "unknown", "range": [0, 0]}
            continue

        vs, fs = zip(*valid)
        vs = np.array(vs)
        fs = np.array(fs)

        # Correlation (linear trend)
        if np.std(fs) > 1e-10:
            corr = float(np.corrcoef(vs, fs)[0, 1])
        else:
            corr = 0.0

        # Range of change
        feat_min = float(np.min(fs))
        feat_max = float(np.max(fs))
        feat_range = feat_max - feat_min

        # Classify trend
        if abs(corr) < 0.3:
            trend = "flat"
        elif corr > 0.7:
            trend = "strong_positive"
        elif corr > 0.3:
            trend = "positive"
        elif corr < -0.7:
            trend = "strong_negative"
        else:
            trend = "negative"

        profile[feat] = {
            "trend": trend,
            "correlation": round(corr, 3),
            "range": [round(feat_min, 6), round(feat_max, 6)],
            "delta": round(feat_range, 6),
        }

    # === Key/pitch drift detection ===
    # If detected pitch shifts significantly across the sweep,
    # this param affects pitch, not just timbre
    pitch_values = []
    key_values = []
    for a in analyses:
        if "error" not in a:
            if a.get("pitch_hz", 0) > 0:
                pitch_values.append(a["pitch_hz"])
            key_values.append(a.get("detected_key", "?"))

    pitch_drift = None
    if len(pitch_values) >= 3:
        pitch_range = max(pitch_values) - min(pitch_values)
        pitch_mean = np.mean(pitch_values)
        # If pitch drifts more than a semitone (~6%), flag it
        if pitch_mean > 0 and pitch_range / pitch_mean > 0.06:
            pitch_drift = {
                "drifts": True,
                "range_hz": round(pitch_range, 1),
                "range_semitones": round(12 * np.log2(max(pitch_values) / (min(pitch_values) + 1e-10)), 1),
                "note": "This param affects PITCH, not just timbre. May need special handling."
            }
        else:
            pitch_drift = {"drifts": False, "range_hz": round(pitch_range, 1)}

    profile["pitch_drift"] = pitch_drift
    profile["keys_observed"] = list(set(key_values))

    # === Envelope shape analysis ===
    # Track how envelope shapes change across the sweep
    centroid_shapes = []
    rms_shapes = []
    for a in analyses:
        if "error" not in a:
            centroid_shapes.append(a.get("envelope_centroid", "unknown"))
            rms_shapes.append(a.get("envelope_rms", "unknown"))

    profile["envelope_centroid_shapes"] = centroid_shapes
    profile["envelope_rms_shapes"] = rms_shapes

    # Check if the param changes the temporal shape (not just the value)
    if len(set(centroid_shapes)) > 1:
        profile["changes_centroid_shape"] = True
    else:
        profile["changes_centroid_shape"] = False

    if len(set(rms_shapes)) > 1:
        profile["changes_rms_shape"] = True
    else:
        profile["changes_rms_shape"] = False

    # Suggest semantic dimensions based on feature trends
    suggestions = []

    # Brightness: centroid goes up, high_energy goes up
    cent_trend = profile.get("spectral_centroid_mean", {}).get("correlation", 0)
    high_trend = profile.get("high_energy_ratio", {}).get("correlation", 0)
    if abs(cent_trend) > 0.4 or abs(high_trend) > 0.4:
        direction = "positive" if cent_trend > 0 else "negative"
        suggestions.append({
            "dimension": "brightness",
            "direction": direction,
            "confidence": round(abs(cent_trend), 2),
        })

    # Warmth: mid_energy goes up
    mid_trend = profile.get("mid_energy_ratio", {}).get("correlation", 0)
    if abs(mid_trend) > 0.4:
        suggestions.append({
            "dimension": "warmth",
            "direction": "positive" if mid_trend > 0 else "negative",
            "confidence": round(abs(mid_trend), 2),
        })

    # Texture: flatness changes
    flat_trend = profile.get("spectral_flatness_mean", {}).get("correlation", 0)
    if abs(flat_trend) > 0.4:
        suggestions.append({
            "dimension": "texture",
            "direction": "positive" if flat_trend > 0 else "negative",
            "confidence": round(abs(flat_trend), 2),
        })

    # Weight: low_energy goes up
    low_trend = profile.get("low_energy_ratio", {}).get("correlation", 0)
    if abs(low_trend) > 0.4:
        suggestions.append({
            "dimension": "weight",
            "direction": "positive" if low_trend > 0 else "negative",
            "confidence": round(abs(low_trend), 2),
        })

    # Movement: centroid variance changes (modulation)
    cent_var_trend = profile.get("spectral_centroid_std", {}).get("correlation", 0)
    if abs(cent_var_trend) > 0.4:
        suggestions.append({
            "dimension": "movement",
            "direction": "positive" if cent_var_trend > 0 else "negative",
            "confidence": round(abs(cent_var_trend), 2),
        })

    # Space: stereo correlation drops (more decorrelated = wider)
    stereo_trend = profile.get("stereo_correlation", {}).get("correlation", 0)
    if abs(stereo_trend) > 0.4:
        suggestions.append({
            "dimension": "space",
            "direction": "positive" if stereo_trend < 0 else "negative",
            "confidence": round(abs(stereo_trend), 2),
        })

    # Attack: onset peak changes
    onset_trend = profile.get("onset_peak", {}).get("correlation", 0)
    if abs(onset_trend) > 0.4:
        suggestions.append({
            "dimension": "attack",
            "direction": "positive" if onset_trend > 0 else "negative",
            "confidence": round(abs(onset_trend), 2),
        })

    # Pitch drift warning
    if pitch_drift and pitch_drift.get("drifts"):
        suggestions.append({
            "dimension": "PITCH_WARNING",
            "direction": "drifts",
            "confidence": 1.0,
            "range_semitones": pitch_drift["range_semitones"],
            "note": "Affects pitch, not just timbre — needs special semantic handling",
        })

    # Envelope shape changes → attack dimension
    if profile.get("changes_rms_shape"):
        suggestions.append({
            "dimension": "attack",
            "direction": "shape_change",
            "confidence": 0.7,
            "note": "Changes the temporal envelope shape",
        })

    profile["suggested_semantics"] = suggestions

    return profile


def main():
    probe_dir = sys.argv[1] if len(sys.argv) > 1 else PROBE_DIR

    if not os.path.exists(probe_dir):
        print(f"Probe directory not found: {probe_dir}")
        print("Run probe_synths.scd in SuperCollider first!")
        sys.exit(1)

    all_analyses = {}
    all_profiles = {}

    # Walk the probe directory
    for synth_name in sorted(os.listdir(probe_dir)):
        synth_dir = os.path.join(probe_dir, synth_name)
        if not os.path.isdir(synth_dir) or synth_name.startswith('.'):
            continue

        print(f"\n=== {synth_name} ===")
        all_analyses[synth_name] = {}
        all_profiles[synth_name] = {}

        for param_name in sorted(os.listdir(synth_dir)):
            param_dir = os.path.join(synth_dir, param_name)
            if not os.path.isdir(param_dir) or param_name.startswith('.'):
                continue

            print(f"  {param_name}:", end=" ")
            param_results = []

            for wav_file in sorted(os.listdir(param_dir)):
                if not wav_file.endswith('.wav'):
                    continue

                # Extract step number from filename
                step = int(wav_file.replace("step_", "").replace(".wav", ""))
                filepath = os.path.join(param_dir, wav_file)

                analysis = analyze_wav(filepath)
                param_results.append((step, analysis))
                print(".", end="", flush=True)

            print(f" ({len(param_results)} steps)")

            all_analyses[synth_name][param_name] = [
                {"step": step, "analysis": a} for step, a in param_results
            ]

            # Build perceptual profile
            profile = build_profile(param_results)
            all_profiles[synth_name][param_name] = profile

            # Print suggestions
            for s in profile.get("suggested_semantics", []):
                print(f"    → {s['dimension']} ({s['direction']}, confidence {s['confidence']})")

    # Save results
    analysis_path = os.path.join(OUTPUT_DIR, "probes_analysis.json")
    profiles_path = os.path.join(OUTPUT_DIR, "probes_profiles.json")

    with open(analysis_path, 'w') as f:
        json.dump(all_analyses, f, indent=2)
    print(f"\nFull analysis saved to: {analysis_path}")

    with open(profiles_path, 'w') as f:
        json.dump(all_profiles, f, indent=2)
    print(f"Profiles saved to: {profiles_path}")

    # Print summary
    print("\n=== SUMMARY ===")
    for synth_name, params in all_profiles.items():
        print(f"\n{synth_name}:")
        for param_name, profile in params.items():
            suggestions = profile.get("suggested_semantics", [])
            if suggestions:
                tags = ", ".join(
                    f"{s['dimension']}({'+'if s['direction']=='positive'else'-'})"
                    for s in suggestions
                )
                print(f"  {param_name}: {tags}")
                # Flag pitch drift prominently
                if profile.get("pitch_drift", {}).get("drifts"):
                    print(f"    ⚠️  PITCH DRIFT: {profile['pitch_drift']['range_semitones']} semitones")
                # Show envelope shape changes
                if profile.get("changes_rms_shape"):
                    shapes = profile.get('envelope_rms_shapes', [])
                    print(f"    📐 RMS envelope changes: {' → '.join(shapes)}")
            else:
                print(f"  {param_name}: (no strong semantic correlation)")


if __name__ == "__main__":
    main()
