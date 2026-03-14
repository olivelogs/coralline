import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { OscClient } from "../osc.js";
import { SEMANTIC_DIMENSIONS } from "../types.js";

// A param value is either a blanket number or a [start, end] gradient tuple.
const gradientValue = z.union([
  z.number(),
  z.tuple([z.number(), z.number()]),
]);

const phraseSchema = z.object({
  synth: z.string().describe("SuperDirt synth name"),
  notes: z
    .string()
    .describe(
      "Space-separated SuperDirt note numbers, e.g. \"0 3 7 12\". " +
        "n=0=C5, n=-12=C4. NOT MIDI — convert with: n = midinote - 60."
    ),
  dur_per_note: z
    .number()
    .positive()
    .describe("Duration in seconds per note"),
  brightness: gradientValue.optional().describe("Single value = blanket for all notes. [start, end] = gradient across phrase."),
  warmth: gradientValue.optional(),
  texture: gradientValue.optional(),
  movement: gradientValue.optional(),
  space: gradientValue.optional(),
  weight: gradientValue.optional(),
  attack: gradientValue.optional(),
  raw: z
    .record(z.string(), gradientValue)
    .optional()
    .describe(
      "Raw SuperDirt params after the | separator. Each value can be a number (blanket) or [start, end] (gradient). " +
        "Semantic params are resolved by the quark — don't also send the raw params they control unless you intend to override."
    ),
});

function appendGradientArgs(
  args: (string | number)[],
  params: Record<string, number | [number, number]>
): void {
  for (const [key, val] of Object.entries(params)) {
    if (typeof val === "number") {
      args.push(key, val);
    } else {
      args.push(key, val[0], val[1]);
    }
  }
}

export function registerPhraseTool(server: McpServer, osc: OscClient): void {
  server.tool(
    "phrase",
    "Play a one-shot phrase: a sequence of notes played once. " +
      "Semantic params can be a single value (applied to all notes) or a [start, end] pair for a gradient across the phrase. " +
      "For example, brightness: [0.2, 0.9] fades from dark to bright.",
    phraseSchema.shape,
    async (input) => {
      const args: (string | number)[] = [input.synth, input.notes, input.dur_per_note];

      // Collect semantic gradient params
      const semParams: Record<string, number | [number, number]> = {};
      for (const dim of SEMANTIC_DIMENSIONS) {
        const val = (input as Record<string, unknown>)[dim];
        if (val !== undefined && val !== null) {
          semParams[dim] = val as number | [number, number];
        }
      }

      appendGradientArgs(args, semParams);

      // Append raw params after pipe separator
      if (input.raw && Object.keys(input.raw).length > 0) {
        args.push("|");
        appendGradientArgs(args, input.raw as Record<string, number | [number, number]>);
      }

      osc.send("/coralline/phrase", args);

      return {
        content: [
          {
            type: "text",
            text: `Sent phrase: ${input.synth} notes=[${input.notes}] ${input.dur_per_note}s/note`,
          },
        ],
      };
    }
  );
}
