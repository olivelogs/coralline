/*
    Quick probe test — ONE param, THREE steps
    Verify recording works before running the full sweep.

    Tests: supervibe modamp at 0.0, 0.5, 1.0
    Output: /Users/olivelo/chime/quarks/ChimeSemantics/Data/probes/test/

    Run in SuperCollider after SuperDirt is loaded.
*/

(
var outputDir = "/Users/olivelo/chime/quarks/ChimeSemantics/Data/probes/test";
var noteDur = 2.0;
var note = 0;  // C5

File.mkdir(outputDir);

"=== Quick probe test ===".postln;
"Recording supervibe modamp at 0.0, 0.5, 1.0".postln;
"Output: %".postln.format(outputDir);

fork {
    [0.0, 0.5, 1.0].do { |val, i|
        var filename = outputDir +/+ "modamp_%.wav".format(i);
        var buf;

        "  Step %: modamp = % ...".format(i, val).postln;

        // Allocate buffer for recording
        buf = Buffer.alloc(s, 44100 * noteDur, 2);
        s.sync;

        // Start recorder synth FIRST (tail of chain = captures output)
        {
            RecordBuf.ar(In.ar(0, 2), buf, loop: 0, doneAction: 2);
        }.play(s, addAction: \addToTail);

        // Small delay to ensure recorder is running
        0.1.wait;

        // Play the note
        (
            type: \dirt,
            s: "supervibe",
            n: note,
            sustain: noteDur * 0.7,
            gain: 1.0,
            modamp: val,
            orbit: 0,
        ).play;

        // Wait for note + a little extra
        (noteDur + 0.3).wait;

        // Write to disk
        buf.write(filename, "wav");
        "    → saved %".format(filename).postln;

        0.3.wait;
        buf.free;
        0.5.wait;
    };

    "".postln;
    "=== Test complete! ===".postln;
    "Check % for 3 wav files".format(outputDir).postln;
    "If they contain audio (not silence), the full probe will work.".postln;
    "Listen to them — modamp 0 should be plain, modamp 1 should be bright/buzzy.".postln;
};
)
