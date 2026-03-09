/*
    ChimeSemantics — Semantic parameter mapping for agent-driven composition.

    Translates perceptual dimensions (brightness, warmth, texture, etc.)
    into synth-specific parameter values.

    All semantic values are normalized 0.0–1.0.

    Usage:
        // Resolve semantic params for a synth:
        ChimeSemantics.resolve(\supervibe, \brightness, 0.7);
        // returns: [[\modfreq, 11.2], [\modamp, 0.21]]

        // Resolve a full set of semantic params:
        ChimeSemantics.resolveAll(\supervibe, (\brightness: 0.7, \warmth: 0.4));
        // returns: (\modfreq: 11.2, \modamp: 0.21, \velocity: 0.58)

    March 2026 — Olive + Claude
*/

ChimeSemantics {

    // Class variable: Dictionary of synth -> semantic mappings
    // Structure: synthName -> dimension -> Array of curve specs
    classvar <mappings;

    // Class variable: the semantic vocabulary
    classvar <dimensions;

    *initClass {
        // Define our semantic dimensions
        // These are the "knobs" an agent can turn
        dimensions = #[
            \brightness,   // spectral energy distribution (dark ↔ bright)
            \warmth,       // harmonic richness, low-mid presence
            \texture,      // smooth ↔ rough/noisy
            \movement,     // modulation rate, vibrato, LFO activity
            \space,        // stereo width, detuning spread
            \weight,       // low-frequency energy, body
            \attack,       // onset sharpness (soft ↔ percussive)
        ];

        // Initialize empty mappings dictionary
        mappings = IdentityDictionary.new;

        // Load hardcoded starter mappings
        // (these will eventually come from Qwen's probing data)
        this.loadDefaultMappings;
    }

    *loadDefaultMappings {
        // ---- supervibe ----
        // Warm, bell-like vibraphone synth. Our most-used.
        // EMPIRICALLY VALIDATED March 2026 via probe sweep.
        // Key findings:
        //   - velocity is the dominant brightness AND warmth control (+0.99 both)
        //   - modfreq is INVERSE brightness (-0.65), not positive!
        //   - decay is the weight dimension (+0.99 weight, -0.99 warmth)
        //   - modamp is warmth(+0.99) and anti-weight(-1.0)
        //   - No pitch drift on any param. Clean timbral space.
        mappings[\supervibe] = IdentityDictionary[
            \brightness -> [
                (\param: \velocity, \outLo: 0.2, \outHi: 1.0, \curve: \lin, \weight: 0.45),
                (\param: \modamp,   \outLo: 0,   \outHi: 1,   \curve: \lin, \weight: 0.30),
                (\param: \detune,   \outLo: 0,   \outHi: 0.5, \curve: \lin, \weight: 0.15),
                // modfreq is inverse — high brightness = LOW modfreq
                (\param: \modfreq,  \outLo: 20,  \outHi: 0,   \curve: \exp, \weight: 0.10),
            ],
            \warmth -> [
                (\param: \velocity, \outLo: 0.2, \outHi: 1.0, \curve: \lin, \weight: 0.40),
                (\param: \modamp,   \outLo: 0,   \outHi: 1,   \curve: \lin, \weight: 0.40),
                // decay is inverse warmth — high warmth = LOW decay
                (\param: \decay,    \outLo: 2,   \outHi: 0,   \curve: \lin, \weight: 0.20),
            ],
            \weight -> [
                // decay adds weight, modamp/velocity remove it
                (\param: \decay,    \outLo: 0,   \outHi: 2,   \curve: \lin, \weight: 0.50),
                (\param: \modamp,   \outLo: 1,   \outHi: 0,   \curve: \lin, \weight: 0.25),
                (\param: \velocity, \outLo: 1,   \outHi: 0.2, \curve: \lin, \weight: 0.25),
            ],
            \movement -> [
                (\param: \velocity, \outLo: 0.2, \outHi: 1.0, \curve: \lin, \weight: 0.45),
                (\param: \detune,   \outLo: 0,   \outHi: 0.5, \curve: \lin, \weight: 0.30),
                // decay is inverse movement
                (\param: \decay,    \outLo: 2,   \outHi: 0,   \curve: \lin, \weight: 0.25),
            ],
            \texture -> [
                // velocity smooths, detune roughens
                (\param: \velocity, \outLo: 1,   \outHi: 0.2, \curve: \lin, \weight: 0.55),
                (\param: \detune,   \outLo: 0,   \outHi: 0.5, \curve: \lin, \weight: 0.45),
            ],
            \attack -> [
                (\param: \velocity, \outLo: 0.2, \outHi: 1.0, \curve: \lin, \weight: 1.0),
            ],
        ];

        // ---- supersaw ----
        // Aggressive sawtooth synth. voice controls waveform character.
        // pitch1/resonance control filter → brightness.
        // lfo/rate control modulation → movement.
        mappings[\supersaw] = IdentityDictionary[
            \brightness -> [
                (\param: \pitch1,    \outLo: 0, \outHi: 2,    \curve: \exp, \weight: 0.5),
                (\param: \resonance, \outLo: 0, \outHi: 0.7,  \curve: \lin, \weight: 0.3),
                (\param: \voice,     \outLo: 0, \outHi: 1,    \curve: \lin, \weight: 0.2),
            ],
            \movement -> [
                (\param: \lfo,  \outLo: 0, \outHi: 1,  \curve: \lin, \weight: 0.5),
                (\param: \rate, \outLo: 0, \outHi: 10, \curve: \exp, \weight: 0.5),
            ],
            \texture -> [
                (\param: \voice, \outLo: 0, \outHi: 1, \curve: \lin, \weight: 1.0),
            ],
        ];

        // ---- superpiano ----
        // Piano synth. muffle is inverse brightness. velocity is dynamics.
        mappings[\superpiano] = IdentityDictionary[
            \brightness -> [
                // Note: muffle is INVERSE — high muffle = dark sound
                // So we map brightness 0→1 to muffle 1→0
                (\param: \muffle, \outLo: 1, \outHi: 0, \curve: \lin, \weight: 1.0),
            ],
            \warmth -> [
                (\param: \velocity, \outLo: 0.3, \outHi: 1.0, \curve: \lin, \weight: 1.0),
            ],
            \space -> [
                (\param: \stereo,  \outLo: 0, \outHi: 0.5, \curve: \lin, \weight: 0.5),
                (\param: \detune,  \outLo: 0, \outHi: 0.3, \curve: \lin, \weight: 0.5),
            ],
        ];
    }

    // Resolve a single semantic dimension for a synth.
    // Returns an Array of [paramName, value] pairs.
    *resolve { |synthName, dimension, value|
        var synthMap, curves, results;

        // Clamp input to 0–1
        value = value.clip(0, 1);

        synthMap = mappings[synthName];
        if(synthMap.isNil) {
            "ChimeSemantics: no mapping for synth '%'".format(synthName).warn;
            ^[]  // return empty — agent can still use raw params
        };

        curves = synthMap[dimension];
        if(curves.isNil) {
            "ChimeSemantics: synth '%' has no mapping for '%'".format(synthName, dimension).warn;
            ^[]
        };

        // Apply each curve mapping
        results = curves.collect { |spec|
            var mapped;
            mapped = this.mapValue(value, spec[\outLo], spec[\outHi], spec[\curve]);
            [spec[\param], mapped]
        };

        ^results
    }

    // Resolve multiple semantic dimensions at once.
    // Returns a flat Event (dictionary) of param -> value,
    // with weighted blending when multiple dimensions affect the same param.
    *resolveAll { |synthName, semantics|
        var paramAccum, paramWeights, result;

        paramAccum = IdentityDictionary.new;
        paramWeights = IdentityDictionary.new;

        semantics.keysValuesDo { |dimension, value|
            var pairs = this.resolve(synthName, dimension, value);
            pairs.do { |pair|
                var param = pair[0], val = pair[1];
                var curves, weight;

                // Find the weight for this param in this dimension
                curves = mappings[synthName][dimension];
                weight = curves.detect { |s| s[\param] == param };
                weight = if(weight.notNil) { weight[\weight] } { 1.0 };

                // Accumulate weighted values
                if(paramAccum[param].isNil) {
                    paramAccum[param] = val * weight;
                    paramWeights[param] = weight;
                } {
                    paramAccum[param] = paramAccum[param] + (val * weight);
                    paramWeights[param] = paramWeights[param] + weight;
                };
            };
        };

        // Normalize by total weight
        result = Event.new;
        paramAccum.keysValuesDo { |param, accum|
            result[param] = accum / paramWeights[param];
        };

        ^result
    }

    // Map a 0–1 value through a curve to an output range.
    *mapValue { |input, outLo, outHi, curve|
        // input is already clipped to 0–1
        ^case
            { curve == \lin } { input.linlin(0, 1, outLo, outHi) }
            { curve == \exp } {
                // Can't use linexp with 0, so offset slightly
                input.max(0.001).linexp(0.001, 1, outLo.max(0.001), outHi.max(0.001))
            }
            { curve == \log } { input.lincurve(0, 1, outLo, outHi, -4) }
            { curve.isNumber } { input.lincurve(0, 1, outLo, outHi, curve) }
            // default: linear
            { true } { input.linlin(0, 1, outLo, outHi) }
    }

    // List all synths that have mappings
    *synths { ^mappings.keys.asArray.sort }

    // List all dimensions mapped for a given synth
    *dimensionsFor { |synthName|
        var synthMap = mappings[synthName];
        if(synthMap.isNil) { ^[] };
        ^synthMap.keys.asArray.sort
    }

    // Pretty print a synth's mapping for debugging
    *inspect { |synthName|
        var synthMap = mappings[synthName];
        if(synthMap.isNil) {
            "ChimeSemantics: no mapping for '%'".format(synthName).postln;
            ^this
        };
        "=== % ===".format(synthName).postln;
        synthMap.keysValuesDo { |dim, curves|
            "  %:".format(dim).postln;
            curves.do { |spec|
                "    % → [%, %] (%, weight %)".format(
                    spec[\param], spec[\outLo], spec[\outHi],
                    spec[\curve], spec[\weight]
                ).postln;
            };
        };
    }

    // Add or update a mapping programmatically
    // (Qwen's probing results get loaded this way)
    *addMapping { |synthName, dimension, paramSpec|
        if(mappings[synthName].isNil) {
            mappings[synthName] = IdentityDictionary.new;
        };
        if(mappings[synthName][dimension].isNil) {
            mappings[synthName][dimension] = [];
        };
        mappings[synthName][dimension] = mappings[synthName][dimension].add(paramSpec);
    }
}
