import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { OscClient } from "../osc.js";
import { SEMANTIC_DIMENSIONS } from "../types.js";

const NOTES_DESC =
  "Space-separated SuperDirt note numbers, e.g. \"0 3 7 12\". " +
  "n=0=C5, n=-12=C4, n=12=C6. NOT MIDI — convert with: n = midinote - 60.";

const RAW_DESC =
  "Raw SuperDirt params passed directly after the | separator, bypassing semantic resolution. " +
  "Each value can be a number (blanket) or [start, end] (gradient across the phrase). " +
  "Semantic params are resolved by the quark — don't also send the raw params they control unless you intend to override.";

// NOTE: Claude's tool system silently drops tools whose JSON Schema contains
// oneOf/anyOf (from z.union) or prefixItems (from z.tuple). Keep all fields
// as plain types — no unions.
//
// Gradient pattern: set brightness=0.3 for blanket, or brightness=0.3 +
// end={brightness: 0.9} for a gradient from 0.3→0.9 across the phrase.

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
  end: z
    .record(z.string(), z.number())
    .optional()
    .describe(
      "Gradient end values for semantic dims. Keys must match a semantic param above. " +
      "E.g. {brightness: 0.9} with brightness=0.3 creates a gradient 0.3→0.9 across the phrase."
    ),
  raw: z
    .record(z.string(), z.number())
    .optional()
    .describe(RAW_DESC),
  raw_end: z
    .record(z.string(), z.number())
    .optional()
    .describe(
      "Gradient end values for raw params. Keys must match a key in raw. " +
      "E.g. raw={gain: 1.0}, raw_end={gain: 0.5} fades gain across the phrase."
    ),
});

export function registerPlayTool(server: McpServer, osc: OscClient): void {
  server.tool(
    "play_sound",
    "Play notes with semantic timbral parameters. " +
      "A single note (default) or a phrase of multiple notes. " +
      "Semantic params (brightness, warmth, etc.) are resolved by the Coralline quark into synth-specific values. " +
      "For phrases, params can be a single value (all notes) or [start, end] for a gradient across the phrase. " +
      "Use raw for effects (room, delay, krush) or direct param overrides.",
    playSchema.shape,
    async (input) => {
      const notes = input.notes || "0";
      const dur = input.dur ?? 0.5;
      const args: (string | number)[] = [input.synth, notes, dur];
      const endVals = (input.end as Record<string, number> | undefined) ?? {};
      const rawEndVals = (input.raw_end as Record<string, number> | undefined) ?? {};

      // Append semantic params (with optional gradient end values)
      for (const dim of SEMANTIC_DIMENSIONS) {
        const val = (input as Record<string, unknown>)[dim];
        if (typeof val === "number") {
          args.push(dim, val);
          if (dim in endVals) {
            args.push(endVals[dim]!);
          }
        }
      }

      // Append raw params after pipe separator (with optional gradient end values)
      if (input.raw && Object.keys(input.raw).length > 0) {
        args.push("|");
        for (const [k, v] of Object.entries(input.raw)) {
          args.push(k, v);
          if (k in rawEndVals) {
            args.push(rawEndVals[k]!);
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
