import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { readFileSync } from "fs";
import { join } from "path";
import { z } from "zod";
import { DATA_DIR } from "../paths.js";
import type { EffectsData } from "../types.js";

function loadEffects(): EffectsData | null {
  const path =
    process.env.CORALLINE_EFFECTS_PATH ??
    join(DATA_DIR, "effects.json");

  try {
    const raw = readFileSync(path, "utf-8");
    return JSON.parse(raw) as EffectsData;
  } catch (err) {
    console.error(
      `[fx] failed to load effects.json from '${path}': ${(err as Error).message}`
    );
    return null;
  }
}

const EFFECTS = loadEffects();

export function registerFxTool(server: McpServer): void {
  server.tool(
    "get_fx",
    "List available SuperDirt effects by category (filters, space, dynamics, distortion, spectral, etc.). " +
      "These are passed as raw params after the | separator in play, loop_start, and phrase. " +
      "Optionally filter by category name.",
    {
      category: z
        .string()
        .optional()
        .describe(
          "Filter by category name, e.g. 'spectral', 'distortion', 'space'. Omit to get all categories."
        ),
    },
    async ({ category }) => {
      if (!EFFECTS) {
        const defaultPath = join(DATA_DIR, "effects.json");
        return {
          content: [
            {
              type: "text",
              text:
                `Error: effects.json not found. Expected at '${defaultPath}'. ` +
                "Set CORALLINE_EFFECTS_PATH to the correct location.",
            },
          ],
          isError: true,
        };
      }

      let categories = EFFECTS.categories;
      if (category) {
        const lower = category.toLowerCase();
        categories = categories.filter((c) =>
          c.category.toLowerCase().includes(lower)
        );
        if (categories.length === 0) {
          const available = EFFECTS.categories.map((c) => c.category).join(", ");
          return {
            content: [
              {
                type: "text",
                text: `No category matching '${category}'. Available: ${available}`,
              },
            ],
            isError: true,
          };
        }
      }

      const result = {
        categories,
        quick_combos: EFFECTS.quick_combos,
      };

      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
      };
    }
  );
}
