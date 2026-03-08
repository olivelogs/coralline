/*
    ChimeSemantics Probing Script
    =============================
    Systematically sweeps every timbral parameter on every synth,
    records each step, and saves wav files for analysis.

    Run this after SuperDirt is loaded.
    Output goes to: /Users/olivelo/chime/quarks/ChimeSemantics/Data/probes/

    March 2026 — Olive + Claude

    HOW TO USE:
    1. Make sure SuperDirt is running
    2. Evaluate this entire file (Cmd+Enter)
    3. It will take ~15-20 minutes to run all sweeps
    4. Wavs land in Data/probes/{synthName}/{paramName}/step_{n}.wav
    5. Run the Python analyzer on the output folder
*/

(
// === Configuration ===
var outputBase = "/Users/olivelo/chime/quarks/ChimeSemantics/Data/probes";
var stepCount = 11;        // 0.0, 0.1, 0.2, ... 1.0
var noteDur = 2.0;         // seconds per recording
var pauseBetween = 0.5;    // seconds between recordings
var recSampleRate = 44100;
var note = 0;              // C5 in SuperDirt's n system
var defaultGain = 1.0;

// === Synth definitions: which params to sweep, and their useful ranges ===
// Format: [synthName, [paramName, minVal, maxVal], [paramName, minVal, maxVal], ...]
// Ranges are empirical "useful" ranges, not technical limits
var synthSpecs = [
    [\supervibe, [
        [\decay, 0, 2],
        [\detune, 0, 0.5],
        [\modamp, 0, 1],
        [\modfreq, 0, 20],
        [\velocity, 0, 1],
    ]],
    [\supersaw, [
        [\decay, 0, 2],
        [\lfo, 0, 1],
        [\pitch1, 0, 3],
        [\rate, 0, 10],
        [\resonance, 0, 1],
        [\semitone, 0, 12],
        [\voice, 0, 1],
    ]],
    [\superpiano, [
        [\detune, 0, 0.5],
        [\muffle, 0, 1],
        [\stereo, 0, 1],
        [\velocity, 0, 1],
    ]],
    [\supermandolin, [
        [\detune, 0, 0.5],
    ]],
    [\supergong, [
        [\decay, 0, 3],
        [\voice, 0, 1],
    ]],
    [\superhammond, [
        [\decay, 0, 2],
        [\perc, 0, 1],
        [\percf, 0, 1],
        [\vibrato, 0, 1],
        [\voice, 0, 1],
        [\vrate, 0, 10],
    ]],
    [\superpwm, [
        [\decay, 0, 2],
        [\lfo, 0, 1],
        [\pitch1, 0, 3],
        [\rate, 0, 10],
        [\resonance, 0, 1],
        [\semitone, 0, 12],
        [\voice, 0, 1],
    ]],
    [\superhex, [
        [\rate, 0, 10],
    ]],
    [\superprimes, [
        [\detune, 0, 0.5],
        [\rate, 0, 10],
        [\voice, 0, 1],
    ]],
    [\supernoise, [
        [\pitch1, 0, 3],
        [\rate, 0, 10],
        [\resonance, 0, 1],
        [\slide, 0, 1],
        [\voice, 0, 1],
    ]],
    [\superkick, [
        [\decay, 0, 2],
        [\pitch1, 0, 3],
    ]],
    [\supersnare, [
        [\decay, 0, 2],
    ]],
    // --- added 2026-03-08 ---
    [\super808, [
        [\rate, 0.1, 5],
        [\voice, 0, 1],
    ]],
    [\superclap, [
        [\delay, 0, 3],
        [\pitch1, 0.5, 3],
        [\rate, 0.1, 5],
    ]],
    // supersiren: skip (no timbral params)
    [\supersquare, [
        [\decay, 0, 2],
        [\lfo, 0, 1],
        [\pitch1, 0, 3],
        [\rate, 0, 10],
        [\resonance, 0, 1],
        [\semitone, 0, 12],
        [\voice, 0.01, 0.99],  // 0 or 1 exactly = silence
    ]],
    [\supercomparator, [
        [\decay, 0, 2],
        [\lfo, 0, 1],
        [\pitch1, 0, 3],
        [\rate, 0, 10],
        [\resonance, 0, 1],
        [\voice, 0, 1],
    ]],
    [\superchip, [
        [\slide, 0, 100],
        [\pitch2, 1, 5],
        [\pitch3, 1, 5],
        [\voice, 0, 1],
        [\rate, 0, 5],
    ]],
    [\superhoover, [
        [\slide, -4, 4],
        [\decay, 0, 1],
    ]],
    [\superzow, [
        [\decay, 0, 1],
        [\slide, 0.1, 5],
        [\detune, 0, 3],
    ]],
    // superstatic: skip (no timbral params)
    [\supergrind, [
        [\detune, 0, 10],
        [\voice, 0, 1],
        [\rate, 0, 5],
    ]],
    [\superwavemechanics, [
        [\detune, 0, 1.5],
        [\voice, 0, 1],
        [\resonance, 0, 1],
    ]],
    [\supertron, [
        [\voice, 0, 1],
        [\detune, 0, 10],
    ]],
    [\superreese, [
        [\detune, 0, 5],
        [\voice, 0, 2],
    ]],
    [\soskick, [
        [\pitch1, 0, 2000],
        [\voice, 0, 5],
        [\pitch2, 0, 0.5],
        [\speed, 0.01, 1],
    ]],
    [\soshats, [
        [\pitch1, 0, 500],
        [\resonance, 0, 1],
    ]],
    [\sostoms, [
        [\voice, 0, 3],
    ]],
    [\sossnare, [
        [\voice, 0, 3],
        [\semitone, 0, 1],
        [\pitch1, 500, 5000],
        [\resonance, 0, 1],
    ]],
];

// === Recording infrastructure ===
var totalProbes = 0;
var completedProbes = 0;

// Count total probes
synthSpecs.do { |spec|
    spec[1].do { totalProbes = totalProbes + stepCount };
};

"=== ChimeSemantics Probing ===".postln;
"Total recordings to make: %".format(totalProbes).postln;
"Estimated time: % minutes".format((totalProbes * (noteDur + pauseBetween) / 60).round(0.1)).postln;
"Output: %".format(outputBase).postln;
"".postln;

fork {
    synthSpecs.do { |synthSpec|
        var synthName = synthSpec[0];
        var params = synthSpec[1];
        var synthDir = outputBase +/+ synthName.asString;

        "--- Probing: % ---".format(synthName).postln;

        params.do { |paramSpec|
            var paramName = paramSpec[0];
            var minVal = paramSpec[1];
            var maxVal = paramSpec[2];
            var paramDir = synthDir +/+ paramName.asString;

            // Create output directory
            // (PathName is SC's way of dealing with filesystem)
            File.mkdir(synthDir);
            File.mkdir(paramDir);

            stepCount.do { |step|
                var val = step.linlin(0, stepCount - 1, minVal, maxVal);
                var filename = paramDir +/+ "step_%.wav".format(step);
                var buf;

                // Record to buffer
                buf = Buffer.alloc(s, recSampleRate * noteDur, 2);

                s.sync;

                // Start recording to buffer
                {
                    var sig = In.ar(0, 2);  // capture main output
                    RecordBuf.ar(sig, buf, loop: 0, doneAction: 2);
                }.play(s, addAction: \addToTail);

                s.sync;

                // Play the note via SuperDirt with the swept param
                (
                    type: \dirt,
                    s: synthName.asString,
                    n: note,
                    sustain: noteDur * 0.8,
                    gain: defaultGain,
                    orbit: 0,
                ).putAll(
                    Event.new.put(paramName, val)
                ).play;

                // Wait for the note to finish
                noteDur.wait;

                // Write buffer to file
                buf.write(filename, "wav");

                // Small pause for disk I/O
                0.2.wait;
                buf.free;

                completedProbes = completedProbes + 1;

                "  %:% = % → % [%/%]".format(
                    synthName, paramName, val.round(0.001), 
                    PathName(filename).fileName,
                    completedProbes, totalProbes
                ).postln;

                pauseBetween.wait;
            };
        };
    };

    "".postln;
    "=== Probing complete! ===".postln;
    "% recordings saved to %".format(totalProbes, outputBase).postln;
    "Run the Python analyzer next.".postln;
};
)
