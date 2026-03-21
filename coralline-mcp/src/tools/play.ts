import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { OscClient } from "../osc.js";
import { SEMANTIC_DIMENSIONS } from "../types.js";

const NOTES_DESC =
  "Space-separated SuperDirt note numbers, e.g. \"0 3 7 12\". " +
  "n=0=C5, n=-12=C4, n=12=C6. NOT MIDI — convert with: n = midinote - 60.";

const RAW_DESC =
  "Raw SuperDirt params passed directly after the | separator, bypassing semantic resolution. " +
  "Format: space-separated key/value pairs, e.g. \"gain 1 room 0.5\". " +
  "Semantic params are resolved by the quark — don't also send the raw params they control unless you intend to override.";

// NOTE: Claude's tool system silently drops tools whose JSON Schema contains
// oneOf/anyOf (from z.union) or prefixItems (from z.tuple). Keep all fields
// as plain types — no unions.
//
// Gradient pattern: set brightness=0.3 for blanket, or brightness=0.3 +
// brightness_end=0.9 for a gradient from 0.3→0.9 across the phrase.

function parseRawPairs(raw: string | undefined): [string, number][] {
  if (!raw?.trim()) return [];

  const tokens = raw.trim().split(/\s+/);
  const pairs: [string, number][] = [];

  for (let i = 0; i + 1 < tokens.length; i += 2) {
    const key = tokens[i]!;
    const value = Number(tokens[i + 1]);
    if (!Number.isFinite(value)) continue;
    pairs.push([key, value]);
  }

  return pairs;
}

const playSchema = z.object({
  synth: z.string().describe("SuperDirt synth name, e.g. supervibe, supersaw, soskick"),
  notes: z
    .string()
    .optional()
    .describe(NOTES_DESC + " Defaults to \"0\" (C5) for a single note."),
  dur: z
    .number()
    .positive()
    .optional()
    .describe("Duration in seconds per note. Defaults to 0.5."),
  brightness: z.number().min(0).max(1).optional().describe("Spectral brightness (0=dark, 1=bright)."),
  warmth: z.number().min(0).max(1).optional().describe("Harmonic warmth / low-mid presence."),
  texture: z.number().min(0).max(1).optional().describe("Texture (0=smooth, 1=rough/noisy)."),
  movement: z.number().min(0).max(1).optional().describe("Modulation rate / vibrato / LFO activity."),
  space: z.number().min(0).max(1).optional().describe("Stereo width / detuning spread."),
  weight: z.number().min(0).max(1).optional().describe("Low-frequency body / heaviness."),
  attack: z.number().min(0).max(1).optional().describe("Onset sharpness (0=soft, 1=percussive)."),
  brightness_end: z.number().min(0).max(1).optional().describe("Optional gradient end value for brightness."),
  warmth_end: z.number().min(0).max(1).optional().describe("Optional gradient end value for warmth."),
  texture_end: z.number().min(0).max(1).optional().describe("Optional gradient end value for texture."),
  movement_end: z.number().min(0).max(1).optional().describe("Optional gradient end value for movement."),
  space_end: z.number().min(0).max(1).optional().describe("Optional gradient end value for space."),
  weight_end: z.number().min(0).max(1).optional().describe("Optional gradient end value for weight."),
  attack_end: z.number().min(0).max(1).optional().describe("Optional gradient end value for attack."),
  raw: z.string().optional().describe(RAW_DESC),
  raw_end: z
    .string()
    .optional()
    .describe(
      "Optional gradient end values for raw params, using the same key/value format as raw. " +
      "Example: raw=\"gain 1 room 0.2\", raw_end=\"gain 0.5 room 0.8\"."
    ),
});

export function registerPlayTool(server: McpServer, osc: OscClient): void {
  server.tool(
    "play",
    "Play one note or a short phrase on a SuperDirt synth. " +
      "Use semantic timbral controls like brightness, warmth, texture, movement, space, weight, and attack. " +
      "Set *_end fields to create gradients across a phrase, and use raw/raw_end for direct SuperDirt params or effects.",
    playSchema.shape,
    async (input) => {
      const notes = input.notes || "0";
      const dur = input.dur ?? 0.5;
      const args: (string | number)[] = [input.synth, notes, dur];
      const rawPairs = parseRawPairs(input.raw);
      const rawEndVals = Object.fromEntries(parseRawPairs(input.raw_end));

      // Append semantic params (with optional gradient end values)
      for (const dim of SEMANTIC_DIMENSIONS) {
        const val = (input as Record<string, unknown>)[dim];
        const endVal = (input as Record<string, unknown>)[`${dim}_end`];
        if (typeof val === "number") {
          args.push(dim, val);
          if (typeof endVal === "number") {
            args.push(endVal);
          }
        }
      }

      // Append raw params after pipe separator (with optional gradient end values)
      if (rawPairs.length > 0) {
        args.push("|");
        for (const [k, v] of rawPairs) {
          args.push(k, v);
          if (k in rawEndVals) {
            args.push(rawEndVals[k] as number);
          }
        }
      }

      osc.send("/coralline/phrase", args);

      const noteCount = notes.trim().split(/\s+/).length;
      const label = noteCount === 1
        ? `Sent play: ${input.synth} n=${notes}`
        : `Sent phrase: ${input.synth} notes=[${notes}] ${dur}s/note`;

      return {
        content: [{ type: "text", text: label }],
      };
    }
  );
}
