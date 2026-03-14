import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { OscClient } from "../osc.js";
import { SEMANTIC_DIMENSIONS } from "../types.js";

const NOTE_DESC =
  "SuperDirt note number. n=0 is C5, n=-12 is C4, n=12 is C6. " +
  "This is NOT MIDI — to convert from MIDI: n = midinote - 60.";

const RAW_DESC =
  "Raw SuperDirt params passed directly after the | separator, bypassing semantic resolution. " +
  "Semantic params are resolved by the quark — don't also send the raw params they control unless you intend to override.";

const semSchema = z.object({
  synth: z.string().describe("SuperDirt synth name, e.g. supervibe, supersaw, soskick"),
  note: z.number().int().default(0).describe(NOTE_DESC),
  brightness: z.number().min(0).max(1).optional().describe("Spectral brightness (0=dark, 1=bright)"),
  warmth: z.number().min(0).max(1).optional().describe("Harmonic warmth / low-mid presence"),
  texture: z.number().min(0).max(1).optional().describe("Texture (0=smooth, 1=rough/noisy)"),
  movement: z.number().min(0).max(1).optional().describe("Modulation rate / vibrato / LFO activity"),
  space: z.number().min(0).max(1).optional().describe("Stereo width / detuning spread"),
  weight: z.number().min(0).max(1).optional().describe("Low-frequency body / heaviness"),
  attack: z.number().min(0).max(1).optional().describe("Onset sharpness (0=soft, 1=percussive)"),
  raw: z
    .record(z.string(), z.number())
    .optional()
    .describe(RAW_DESC),
});

export function registerPlayTool(server: McpServer, osc: OscClient): void {
  server.tool(
    "play",
    "Play a single note using a SuperDirt synth with semantic timbral parameters. " +
      "Semantic params (brightness, warmth, etc.) are resolved by the Coralline quark into synth-specific SuperDirt values. " +
      "Use raw for effects (room, delay, krush) or direct param overrides.",
    semSchema.shape,
    async (input) => {
      const args: (string | number)[] = [input.synth, input.note];

      // Append semantic dimensions that were provided
      for (const dim of SEMANTIC_DIMENSIONS) {
        const val = (input as Record<string, unknown>)[dim];
        if (typeof val === "number") {
          args.push(dim, val);
        }
      }

      // Append raw params after pipe separator
      if (input.raw && Object.keys(input.raw).length > 0) {
        args.push("|");
        for (const [k, v] of Object.entries(input.raw)) {
          args.push(k, v);
        }
      }

      osc.send("/coralline/play", args);

      return {
        content: [
          {
            type: "text",
            text: `Sent /coralline/play ${input.synth} n=${input.note}`,
          },
        ],
      };
    }
  );
}
