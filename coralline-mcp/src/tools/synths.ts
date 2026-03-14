import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { readFileSync } from "fs";
import { join } from "path";
import { z } from "zod";
import { DATA_DIR } from "../paths.js";
import { SEMANTIC_DIMENSIONS, type RefinedMappings } from "../types.js";

function loadMappings(): RefinedMappings | null {
  const path =
    process.env.CORALLINE_MAPPINGS_PATH ??
    join(DATA_DIR, "refined_mappings.json");

  try {
    const raw = readFileSync(path, "utf-8");
    return JSON.parse(raw) as RefinedMappings;
  } catch (err) {
    console.error(
      `[synths] failed to load refined_mappings.json from '${path}': ${(err as Error).message}`
    );
    console.error("[synths] synth tools will return errors until mappings are available.");
    return null;
  }
}

const MAPPINGS = loadMappings();

export function registerSynthTools(server: McpServer): void {
  server.tool(
    "list_synths",
    "List all available SuperDirt synths and the semantic dimensions each one supports.",
    {},
    async () => {
      if (!MAPPINGS) {
        return {
          content: [
            {
              type: "text",
              text: "Error: synth mappings not available. Check CORALLINE_MAPPINGS_PATH env var or ensure refined_mappings.json exists.",
            },
          ],
          isError: true,
        };
      }

      const synths = Object.keys(MAPPINGS)
        .sort()
        .map((name) => {
          const entry = MAPPINGS[name];
          const dims = SEMANTIC_DIMENSIONS.filter(
            (d) => Array.isArray(entry[d]) && (entry[d] as unknown[]).length > 0
          );
          return { name, dimensions: dims };
        });

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(synths, null, 2),
          },
        ],
      };
    }
  );

  server.tool(
    "get_synth_info",
    "Get detailed timbral curve information for a specific synth, including which semantic dimensions are mapped, the raw param targets, output ranges, and confidence scores. Also shows which params affect pitch (avoid for pure timbral work).",
    { synth: z.string().describe("Synth name, e.g. supervibe") },
    async ({ synth }) => {
      if (!MAPPINGS) {
        return {
          content: [
            {
              type: "text",
              text: "Error: synth mappings not available. Check CORALLINE_MAPPINGS_PATH env var.",
            },
          ],
          isError: true,
        };
      }

      const entry = MAPPINGS[synth];
      if (!entry) {
        const available = Object.keys(MAPPINGS).sort().join(", ");
        return {
          content: [
            {
              type: "text",
              text: `Error: unknown synth '${synth}'. Available synths: ${available}`,
            },
          ],
          isError: true,
        };
      }

      const dimensions: Record<string, unknown[]> = {};
      for (const dim of SEMANTIC_DIMENSIONS) {
        const curves = entry[dim];
        if (Array.isArray(curves) && curves.length > 0) {
          dimensions[dim] = curves.map((c) => ({
            param: c.param,
            out_range: [c.outLo, c.outHi],
            curve: c.curve,
            weight: c.weight,
            confidence: c.confidence,
          }));
        }
      }

      const result = {
        synth,
        dimensions,
        pitch_controls: entry.pitch_controls ?? [],
        needs_review: entry.needs_review ?? [],
      };

      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
      };
    }
  );
}
