import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { appendFileSync, existsSync, readFileSync, writeFileSync } from "fs";
import { resolve } from "path";
import { REPO_ROOT } from "../paths.js";

export const NOTEBOOK_PATH = resolve(REPO_ROOT, "notebook.md");

const NOTEBOOK_HEADER = `# Coralline Studio Notebook

Notes left across sessions — by claudes, for the claudes that come after
(and for Olive). Discoveries, recipes, quirks, things made. Append-only;
each entry is a \`##\` heading. Committed to the repo: this is lineage,
not a log.
`;

// Local time, ISO-ish: "2026-06-09 21:42"
function timestamp(): string {
  return new Date().toLocaleString("sv-SE", { dateStyle: "short", timeStyle: "short" });
}

function splitEntries(content: string): { header: string; entries: string[] } {
  const parts = content.split(/\n(?=## )/);
  return { header: parts[0] ?? "", entries: parts.slice(1) };
}

export function readNotebook(last?: number): string {
  if (!existsSync(NOTEBOOK_PATH)) {
    return (
      "The notebook doesn't exist yet — you're the first to write in it. " +
      "Use add_note to leave the inaugural entry."
    );
  }

  const content = readFileSync(NOTEBOOK_PATH, "utf8");
  const { header, entries } = splitEntries(content);

  if (last === undefined || entries.length <= last) {
    return content;
  }

  return [
    header.trimEnd(),
    `\n_(showing the ${last} most recent of ${entries.length} entries — omit \`last\` for all)_\n`,
    ...entries.slice(-last),
  ].join("\n");
}

export function addNote(title: string, note: string, tags?: string): string {
  if (!existsSync(NOTEBOOK_PATH)) {
    writeFileSync(NOTEBOOK_PATH, NOTEBOOK_HEADER);
  }

  let entry = `\n## ${timestamp()} — ${title.trim()}\n`;
  if (tags?.trim()) {
    entry += `_tags: ${tags.trim()}_\n`;
  }
  entry += `\n${note.trim()}\n`;

  appendFileSync(NOTEBOOK_PATH, entry);

  const { entries } = splitEntries(readFileSync(NOTEBOOK_PATH, "utf8"));
  return `Noted — entry ${entries.length} in the studio notebook (${NOTEBOOK_PATH}).`;
}

export function registerNotebookTools(server: McpServer): void {
  server.tool(
    "read_notebook",
    "Read the studio notebook: findings, recipes, and discoveries left by previous sessions — " +
      "claudes and Olive both write here. Read it near the start of a session, before making music: " +
      "it's how you inherit what past claudes learned (synth sweet spots, perception quirks, " +
      "combinations that sounded great, open questions worth testing). " +
      "Returns the whole notebook by default; pass `last` for only the N most recent entries.",
    {
      last: z
        .number()
        .int()
        .min(1)
        .optional()
        .describe("Only return the N most recent entries (omit for the full notebook)."),
    },
    async ({ last }) => {
      try {
        return { content: [{ type: "text", text: readNotebook(last) }] };
      } catch (err) {
        return {
          content: [{ type: "text", text: `Error: ${(err as Error).message}` }],
          isError: true,
        };
      }
    }
  );

  server.tool(
    "add_note",
    "Leave a note in the studio notebook for future sessions. Good notes: empirical discoveries " +
      "(a synth's sweet spot, a perception quirk, a mapping that lies), recipes that sounded great " +
      "(synth + semantic params + fx, exact values), context on things made (clips live in recordings/ — " +
      "reference them by filename), and open questions worth testing. Write for a claude who wasn't here: " +
      "include exact params, not vibes alone. Skip ephemera — this is a lineage document, not a session log. " +
      "One or two notes per session is plenty.",
    {
      title: z.string().describe("Short title for the entry, e.g. \"supervibe sweet spot\" or \"perceived pitch quirk\"."),
      note: z.string().describe("The note body (markdown). Include exact params/values so it's reproducible."),
      tags: z
        .string()
        .optional()
        .describe("Optional comma-separated tags, e.g. \"perception, supervibe, open-question\"."),
    },
    async ({ title, note, tags }) => {
      try {
        return { content: [{ type: "text", text: addNote(title, note, tags) }] };
      } catch (err) {
        return {
          content: [{ type: "text", text: `Error: ${(err as Error).message}` }],
          isError: true,
        };
      }
    }
  );
}
