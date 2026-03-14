/*
    CorallineSemantics — Semantic parameter mapping for agent-driven composition.

    Translates perceptual dimensions (brightness, warmth, texture, etc.)
    into synth-specific parameter values.

    All semantic values are normalized 0.0–1.0.

    Usage:
        // After class library compiles, load the empirical mappings:
        CorallineSemantics.loadRefined;

        // Resolve semantic params for a synth:
        CorallineSemantics.resolve(\supervibe, \brightness, 0.7);
        // returns: [[\decay, 0.86], [\detune, 0.12], [\modamp, 0.15], ...]

        // Resolve a full set of semantic params:
        CorallineSemantics.resolveAll(\supervibe, (\brightness: 0.7, \warmth: 0.4));
        // returns: (\decay: 1.14, \modamp: 0.28, \velocity: 0.52, ...)

        // List available synths:
        CorallineSemantics.synths;

        // See what a synth can do:
        CorallineSemantics.dimensionsFor(\supervibe);

        // Inspect full mapping:
        CorallineSemantics.inspect(\supervibe);

        // Check which params affect pitch (avoid for timbral work):
        CorallineSemantics.pitchControlsFor(\superpwm);

    March 2026 — Olive + Claude
    Empirical mappings: 322 timbral curves across 27 SuperDirt synths,
    derived from probe sweeps + librosa analysis.
*/

CorallineSemantics {

    // Class variable: Dictionary of synth -> semantic mappings
    // Structure: synthName -> dimension -> Array of curve specs
    classvar <mappings;

    // Class variable: the semantic vocabulary
    classvar <dimensions;

    // Class variable: pitch controls per synth (params that drift pitch, not timbre)
    classvar <pitchControls;

    // Class variable: items flagged for human review
    classvar <needsReview;

    // Whether refined mappings have been loaded
    classvar <refinedLoaded;

    // Default path to refined mappings JSON
    classvar <defaultRefinedPath;

    *initClass {
        // Define our semantic dimensions
        dimensions = #[
            \brightness,   // spectral energy distribution (dark ↔ bright)
            \warmth,       // harmonic richness, low-mid presence
            \texture,      // smooth ↔ rough/noisy
            \movement,     // modulation rate, vibrato, LFO activity
            \space,        // stereo width, detuning spread
            \weight,       // low-frequency energy, body
            \attack        // onset sharpness (soft ↔ percussive)
        ];

        // Initialize containers
        mappings = IdentityDictionary.new;
        pitchControls = IdentityDictionary.new;
        needsReview = IdentityDictionary.new;
        refinedLoaded = false;

        // Default path — adjust if your quarks live elsewhere
        defaultRefinedPath = (
            Platform.userAppSupportDir +/+
            "/coralline/quark/Coralline/Data/refined_mappings.json"
        );

        // Load hardcoded fallback mappings
        this.loadDefaultMappings;
    }

    // =========================================================
    // JSON LOADING — the main event
    // =========================================================

    // Load refined empirical mappings from JSON.
    // Replaces all existing mappings.
    *loadRefined { |path|
        path = path ?? { defaultRefinedPath };
        this.loadFromJSON(path);
    }

    // Parse refined_mappings.json and populate all class variables.
    *loadFromJSON { |path|
        var file, jsonStr, data, synthCount = 0, curveCount = 0;

        if(File.exists(path).not) {
            "CorallineSemantics: refined mappings not found at '%'".format(path).warn;
            "CorallineSemantics: using default mappings only.".postln;
            ^this
        };

        // Read and parse
        file = File.open(path, "r");
        jsonStr = file.readAllString;
        file.close;

        data = jsonStr.parseYAML;

        if(data.isNil) {
            "CorallineSemantics: failed to parse JSON at '%'".format(path).warn;
            ^this
        };

        // Clear existing mappings — empirical data replaces everything
        mappings = IdentityDictionary.new;
        pitchControls = IdentityDictionary.new;
        needsReview = IdentityDictionary.new;

        // Iterate synths
        data.keysValuesDo { |synthNameStr, synthData|
            var synthName = synthNameStr.asSymbol;
            var synthMap = IdentityDictionary.new;
            var hasTimbralMappings = false;

            // Process each semantic dimension
            dimensions.do { |dim|
                var dimStr = dim.asString;
                var rawCurves = synthData[dimStr];
                var processed;

                if(rawCurves.notNil and: { rawCurves.isArray and: { rawCurves.size > 0 }}) {
                    // Convert and deduplicate
                    processed = this.prProcessCurves(rawCurves);
                    if(processed.size > 0) {
                        synthMap[dim] = processed;
                        curveCount = curveCount + processed.size;
                        hasTimbralMappings = true;
                    };
                };
            };

            // Only add synths that have at least one timbral mapping
            if(hasTimbralMappings) {
                mappings[synthName] = synthMap;
                synthCount = synthCount + 1;
            };

            // Store pitch controls (always, even for pitch-only synths)
            this.prLoadPitchControls(synthName, synthData);

            // Store review flags
            this.prLoadNeedsReview(synthName, synthData);
        };

        refinedLoaded = true;
        "CorallineSemantics: loaded % synths, % timbral curves from '%'".format(
            synthCount, curveCount, PathName(path).fileName
        ).postln;
    }

    // =========================================================
    // PRIVATE: JSON processing helpers
    // =========================================================

    // Convert raw JSON curve array to SC spec format.
    // Deduplicates: if same param appears twice, keeps higher confidence.
    *prProcessCurves { |rawCurves|
        var byParam = IdentityDictionary.new;
        var result;

        rawCurves.do { |raw|
            var param = raw["param"].asSymbol;
            var conf = raw["confidence"].asFloat;
            var existing = byParam[param];

            // Keep the higher-confidence entry for each param
            if(existing.isNil or: { conf > existing[\confidence] }) {
                byParam[param] = (
                    \param:      param,
                    \outLo:      raw["outLo"].asFloat,
                    \outHi:      raw["outHi"].asFloat,
                    \curve:      raw["curve"].asSymbol,
                    \weight:     raw["weight"].asFloat,
                    \confidence: conf
                );
            };
        };

        // Return as array, sorted by weight descending
        result = byParam.values.asArray;
        result.sort { |a, b| a[\weight] > b[\weight] };
        ^result
    }

    // Extract pitch control metadata for a synth.
    *prLoadPitchControls { |synthName, synthData|
        var raw = synthData["pitch_controls"];
        if(raw.notNil and: { raw.isArray and: { raw.size > 0 }}) {
            pitchControls[synthName] = raw.collect { |pc|
                (\param: pc["param"].asSymbol, \driftSemitones: pc["drift_semitones"].asFloat)
            };
        };
    }

    // Extract needs_review flags.
    *prLoadNeedsReview { |synthName, synthData|
        var raw = synthData["needs_review"];
        if(raw.notNil and: { raw.isArray and: { raw.size > 0 }}) {
            needsReview[synthName] = raw.collect(_.asString);
        };
    }

    // =========================================================
    // FALLBACK: hardcoded defaults (used if JSON not loaded)
    // =========================================================

    *loadDefaultMappings {
        // ---- supervibe ----
        mappings[\supervibe] = IdentityDictionary[
            \brightness -> [
                (\param: \velocity, \outLo: 0.2, \outHi: 1.0, \curve: \lin, \weight: 0.45, \confidence: 0.99),
                (\param: \modamp,   \outLo: 0,   \outHi: 1,   \curve: \lin, \weight: 0.30, \confidence: 0.88),
                (\param: \detune,   \outLo: 0,   \outHi: 0.5, \curve: \lin, \weight: 0.15, \confidence: 0.73),
                (\param: \modfreq,  \outLo: 20,  \outHi: 0,   \curve: \exp, \weight: 0.10, \confidence: 0.65),
            ],
            \warmth -> [
                (\param: \velocity, \outLo: 0.2, \outHi: 1.0, \curve: \lin, \weight: 0.40, \confidence: 0.99),
                (\param: \modamp,   \outLo: 0,   \outHi: 1,   \curve: \lin, \weight: 0.40, \confidence: 0.99),
                (\param: \decay,    \outLo: 2,   \outHi: 0,   \curve: \lin, \weight: 0.20, \confidence: 0.99),
            ],
            \weight -> [
                (\param: \decay,    \outLo: 0,   \outHi: 2,   \curve: \lin, \weight: 0.50, \confidence: 0.99),
                (\param: \modamp,   \outLo: 1,   \outHi: 0,   \curve: \lin, \weight: 0.25, \confidence: 1.0),
                (\param: \velocity, \outLo: 1,   \outHi: 0.2, \curve: \lin, \weight: 0.25, \confidence: 0.99),
            ],
            \movement -> [
                (\param: \velocity, \outLo: 0.2, \outHi: 1.0, \curve: \lin, \weight: 0.45, \confidence: 0.97),
                (\param: \detune,   \outLo: 0,   \outHi: 0.5, \curve: \lin, \weight: 0.30, \confidence: 0.60),
                (\param: \decay,    \outLo: 2,   \outHi: 0,   \curve: \lin, \weight: 0.25, \confidence: 0.95),
            ],
            \texture -> [
                (\param: \velocity, \outLo: 1,   \outHi: 0.2, \curve: \lin, \weight: 0.55, \confidence: 0.60),
                (\param: \detune,   \outLo: 0,   \outHi: 0.5, \curve: \lin, \weight: 0.45, \confidence: 0.52),
            ],
            \attack -> [
                (\param: \velocity, \outLo: 0.2, \outHi: 1.0, \curve: \lin, \weight: 1.0, \confidence: 0.80),
            ],
        ];

        // ---- supersaw ----
        mappings[\supersaw] = IdentityDictionary[
            \brightness -> [
                (\param: \pitch1,    \outLo: 0, \outHi: 2,    \curve: \exp, \weight: 0.5, \confidence: 1.0),
                (\param: \resonance, \outLo: 0, \outHi: 0.7,  \curve: \lin, \weight: 0.3, \confidence: 0.5),
                (\param: \voice,     \outLo: 0, \outHi: 1,    \curve: \lin, \weight: 0.2, \confidence: 0.47),
            ],
            \movement -> [
                (\param: \lfo,  \outLo: 0, \outHi: 1,  \curve: \lin, \weight: 0.5, \confidence: 1.0),
                (\param: \rate, \outLo: 0, \outHi: 10, \curve: \exp, \weight: 0.5, \confidence: 0.5),
            ],
            \texture -> [
                (\param: \voice, \outLo: 0, \outHi: 1, \curve: \lin, \weight: 1.0, \confidence: 0.44),
            ],
        ];

        // ---- superpiano ----
        mappings[\superpiano] = IdentityDictionary[
            \brightness -> [
                (\param: \muffle, \outLo: 1, \outHi: 0, \curve: \lin, \weight: 1.0, \confidence: 0.94),
            ],
            \warmth -> [
                (\param: \velocity, \outLo: 0.3, \outHi: 1.0, \curve: \lin, \weight: 1.0, \confidence: 0.94),
            ],
            \space -> [
                (\param: \stereo,  \outLo: 0, \outHi: 0.5, \curve: \lin, \weight: 0.5, \confidence: 0.94),
                (\param: \detune,  \outLo: 0, \outHi: 0.3, \curve: \lin, \weight: 0.5, \confidence: 0.5),
            ],
        ];
    }

    // =========================================================
    // RESOLUTION ENGINE
    // =========================================================

    // Resolve a single semantic dimension for a synth.
    // Returns an Array of [paramName, value] pairs.
    *resolve { |synthName, dimension, value|
        var synthMap, curves, results;

        // Clamp input to 0–1
        value = value.clip(0, 1);

        synthMap = mappings[synthName];
        if(synthMap.isNil) {
            "CorallineSemantics: no mapping for synth '%'".format(synthName).warn;
            ^[]  // return empty — agent can still use raw params
        };

        curves = synthMap[dimension];
        if(curves.isNil) {
            "CorallineSemantics: synth '%' has no mapping for '%'".format(synthName, dimension).warn;
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

    // =========================================================
    // QUERY INTERFACE
    // =========================================================

    // List all synths that have timbral mappings
    *synths { ^mappings.keys.asArray.sort }

    // List all dimensions mapped for a given synth
    *dimensionsFor { |synthName|
        var synthMap = mappings[synthName];
        if(synthMap.isNil) { ^[] };
        ^synthMap.keys.asArray.sort
    }

    // Get pitch controls for a synth (params to avoid for timbral work)
    *pitchControlsFor { |synthName|
        ^pitchControls[synthName] ?? { [] }
    }

    // Is a given param a pitch control for this synth?
    *isPitchControl { |synthName, param|
        var pcs = pitchControls[synthName];
        if(pcs.isNil) { ^false };
        ^pcs.any { |pc| pc[\param] == param }
    }

    // Get the confidence range for a synth's mappings
    *confidenceFor { |synthName|
        var synthMap = mappings[synthName];
        var allConf;
        if(synthMap.isNil) { ^nil };
        allConf = [];
        synthMap.do { |curves|
            curves.do { |spec|
                if(spec[\confidence].notNil) {
                    allConf = allConf.add(spec[\confidence]);
                };
            };
        };
        if(allConf.isEmpty) { ^nil };
        ^(\min: allConf.minItem, \max: allConf.maxItem, \mean: allConf.mean)
    }

    // Get review flags for a synth
    *reviewFor { |synthName|
        ^needsReview[synthName] ?? { [] }
    }

    // =========================================================
    // INTROSPECTION / DEBUG
    // =========================================================

    // Pretty print a synth's mapping
    *inspect { |synthName|
        var synthMap = mappings[synthName];
        var pcs;
        if(synthMap.isNil) {
            "CorallineSemantics: no mapping for '%'".format(synthName).postln;
            ^this
        };
        "".postln;
        "=== % ===".format(synthName).postln;
        if(refinedLoaded) {
            var conf = this.confidenceFor(synthName);
            if(conf.notNil) {
                "  confidence: %.2f – %.2f (mean %.2f)".format(
                    conf[\min], conf[\max], conf[\mean]
                ).postln;
            };
        };
        synthMap.keys.asArray.sort.do { |dim|
            var curves = synthMap[dim];
            "  %:".format(dim).postln;
            curves.do { |spec|
                var confStr = if(spec[\confidence].notNil) {
                    " conf %.2f".format(spec[\confidence])
                } { "" };
                "    % → [%, %] (%, w:%%%)".format(
                    spec[\param], spec[\outLo].round(0.001), spec[\outHi].round(0.001),
                    spec[\curve], spec[\weight].round(0.001), confStr
                ).postln;
            };
        };
        pcs = this.pitchControlsFor(synthName);
        if(pcs.size > 0) {
            "  pitch controls (avoid for timbre):".postln;
            pcs.do { |pc|
                "    % (drift: % semitones)".format(pc[\param], pc[\driftSemitones]).postln;
            };
        };
        this.reviewFor(synthName).do { |r|
            "  ⚠ review: %".format(r).postln;
        };
    }

    // Print summary of all loaded mappings
    *summary {
        var totalCurves = 0;
        "".postln;
        "=== CorallineSemantics Summary ===".postln;
        "  refined loaded: %".format(refinedLoaded).postln;
        "  synths: %".format(this.synths.size).postln;
        this.synths.do { |s|
            var dims = this.dimensionsFor(s);
            var curves = 0;
            dims.do { |d| curves = curves + mappings[s][d].size };
            totalCurves = totalCurves + curves;
            "    % — % dimensions, % curves".format(s, dims.size, curves).postln;
        };
        "  total curves: %".format(totalCurves).postln;
        if(pitchControls.size > 0) {
            "  synths with pitch controls: %".format(pitchControls.keys.asArray.sort).postln;
        };
        "".postln;
    }

    // =========================================================
    // PROGRAMMATIC MAPPING UPDATES
    // =========================================================

    // Add or update a single mapping spec
    *addMapping { |synthName, dimension, paramSpec|
        if(mappings[synthName].isNil) {
            mappings[synthName] = IdentityDictionary.new;
        };
        if(mappings[synthName][dimension].isNil) {
            mappings[synthName][dimension] = [];
        };
        mappings[synthName][dimension] = mappings[synthName][dimension].add(paramSpec);
    }

    // Remove all mappings for a synth (useful before reloading)
    *clearSynth { |synthName|
        mappings.removeAt(synthName);
    }
}
