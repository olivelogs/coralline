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
  cycle_dur: z
    .number()
    .positive()
    .describe("Total cycle duration in seconds. Per-note dur = cycle_dur / note_count."),
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
    input.cycle_dur,
  ];

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
    "Start a named pattern loop. Notes cycle continuously through the given pattern. " +
      "Calling loop_start with an existing loop name hot-swaps it in place — playback is not interrupted. " +
      "Semantic params are resolved by the Coralline quark.",
    loopSchema.shape,
    async (input) => {
      const args = buildLoopArgs(input);
      osc.send("/coralline/loop/start", args);
      return {
        content: [
          {
            type: "text",
            text: `Started loop '${input.loop_name}': ${input.synth} notes=[${input.notes}] cycle=${input.cycle_dur}s`,
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
