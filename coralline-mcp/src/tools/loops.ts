import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { OscClient } from "../osc.js";
import { SEMANTIC_DIMENSIONS } from "../types.js";

const RAW_DESC =
  "Raw SuperDirt params after the | separator. " +
  "Semantic params are resolved by the quark — don't also send the raw params they control unless you intend to override.";

const loopSchema = z.object({
  loop_name: z.string().describe(
    "Name for this loop. Reusing an existing name hot-swaps the loop in place without stopping playback."
  ),
  synth: z.string().describe("SuperDirt synth name, e.g. supervibe, supersaw, soskick"),
  notes: z
    .string()
    .describe(
      "Space-separated SuperDirt note numbers, e.g. \"0 3 7 12\". " +
        "n=0=C5, n=-12=C4. NOT MIDI — convert with: n = midinote - 60."
    ),
  cycle_beats: z
    .number()
    .positive()
    .describe(
      "Cycle length in BEATS on the shared clock (4 = one bar of 4/4; default tempo 120 BPM, so 4 beats = 2s). " +
        "Per-note dur = cycle_beats / note_count. Tempo changes via set_tempo retune running loops."
    ),
  quant: z
    .number()
    .min(0)
    .optional()
    .describe(
      "Beat boundary the loop start (or hot-swap) waits for. Default = one bar, so layers phase-lock " +
        "and modifications land on the downbeat. 0 = start immediately (free/textural, no grid)."
    ),
  brightness: z.number().min(0).max(1).optional(),
  warmth: z.number().min(0).max(1).optional(),
  texture: z.number().min(0).max(1).optional(),
  movement: z.number().min(0).max(1).optional(),
  space: z.number().min(0).max(1).optional(),
  weight: z.number().min(0).max(1).optional(),
  attack: z.number().min(0).max(1).optional(),
  raw: z.record(z.string(), z.number()).optional().describe(RAW_DESC),
});

const stopSchema = z.object({
  loop_name: z.string().describe("Name of the loop to stop"),
});

function buildLoopArgs(input: z.infer<typeof loopSchema>): (string | number)[] {
  const args: (string | number)[] = [
    input.loop_name,
    input.synth,
    input.notes,
    input.cycle_beats,
  ];

  // Optional quant rides right after cycle_beats — the quark type-sniffs
  // a number in that slot (a string there starts the semantic params)
  if (input.quant !== undefined) {
    args.push(input.quant);
  }

  for (const dim of SEMANTIC_DIMENSIONS) {
    const val = (input as Record<string, unknown>)[dim];
    if (typeof val === "number") {
      args.push(dim, val);
    }
  }

  if (input.raw && Object.keys(input.raw).length > 0) {
    args.push("|");
    for (const [k, v] of Object.entries(input.raw)) {
      args.push(k, v);
    }
  }

  return args;
}

export function registerLoopTools(server: McpServer, osc: OscClient): void {
  server.tool(
    "loop_start",
    "Start a named pattern loop on the shared clock. Notes cycle continuously through the given pattern. " +
      "Loops start on the next bar boundary by default, so layers phase-lock — and calling loop_start with " +
      "an existing loop name hot-swaps it in place ON the bar, so changes land musically without interrupting " +
      "playback. Use get_state to see the current bar position and set_tempo to move the pulse. " +
      "Semantic params are resolved by the Coralline quark.",
    loopSchema.shape,
    async (input) => {
      const args = buildLoopArgs(input);
      osc.send("/coralline/loop/start", args);
      return {
        content: [
          {
            type: "text",
            text:
              `Started loop '${input.loop_name}': ${input.synth} notes=[${input.notes}] ` +
              `cycle=${input.cycle_beats} beats` +
              (input.quant === 0 ? " (free, unquantized)" : " (from next bar)"),
          },
        ],
      };
    }
  );

  server.tool(
    "set_tempo",
    "Set the shared clock's tempo in BPM (and optionally the meter). Running loops follow immediately — " +
      "loop durations are musical (beats), not seconds — so this is how you do tempo moves, ritardandos by " +
      "steps, or a meter change (meter changes land on the next bar line). Default clock: 120 BPM, 4/4.",
    {
      bpm: z.number().min(20).max(300).describe("Tempo in beats per minute."),
      beats_per_bar: z
        .number()
        .int()
        .min(1)
        .max(16)
        .optional()
        .describe("Beats per bar (meter). Takes effect at the next bar line."),
    },
    async ({ bpm, beats_per_bar }) => {
      const args: number[] = [bpm];
      if (beats_per_bar !== undefined) args.push(beats_per_bar);
      osc.send("/coralline/clock/set", args);
      return {
        content: [
          {
            type: "text",
            text: `Clock set to ${bpm} BPM${beats_per_bar !== undefined ? `, ${beats_per_bar} beats/bar from next bar` : ""}.`,
          },
        ],
      };
    }
  );

  server.tool(
    "loop_stop",
    "Stop a named loop. The loop is cleared immediately.",
    stopSchema.shape,
    async (input) => {
      osc.send("/coralline/loop/stop", [input.loop_name]);
      return {
        content: [{ type: "text", text: `Stopped loop '${input.loop_name}'` }],
      };
    }
  );
}
