#!/usr/bin/env node
import { readFileSync, writeFileSync, unlinkSync, existsSync } from "node:fs";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { Logger } from "./logger.js";
import { OscClient, OscServer } from "./osc.js";
import { PID_FILE } from "./paths.js";
import { registerAnalysisTools } from "./tools/analysis.js";
import { registerFxTool } from "./tools/fx.js";
import { registerLoopTools } from "./tools/loops.js";
import { registerPhraseTool } from "./tools/phrase.js";
import { registerPlayTool } from "./tools/play.js";
import { registerSynthTools } from "./tools/synths.js";

// ---- Pidfile: ensure only one MCP instance owns port 9601 ----

function killStalePid(): void {
  if (!existsSync(PID_FILE)) return;

  try {
    const oldPid = Number(readFileSync(PID_FILE, "utf-8").trim());
    if (!Number.isFinite(oldPid) || oldPid <= 0) {
      unlinkSync(PID_FILE);
      return;
    }

    // Check if the process is still alive
    try {
      process.kill(oldPid, 0); // signal 0 = existence check, doesn't kill
      // Still alive — kill it
      console.error(`[pid] killing stale coralline-mcp (pid ${oldPid})`);
      process.kill(oldPid, "SIGTERM");
      // Give it a moment to die
      const deadline = Date.now() + 1000;
      while (Date.now() < deadline) {
        try {
          process.kill(oldPid, 0);
        } catch {
          break; // it's gone
        }
      }
    } catch {
      // Process already dead, just clean up the pidfile
    }

    unlinkSync(PID_FILE);
  } catch {
    // Pidfile unreadable or already gone, move on
  }
}

function writePid(): void {
  writeFileSync(PID_FILE, String(process.pid), "utf-8");
}

function removePid(): void {
  try {
    // Only remove if it's still our pid (guard against race)
    const contents = readFileSync(PID_FILE, "utf-8").trim();
    if (Number(contents) === process.pid) {
      unlinkSync(PID_FILE);
    }
  } catch {
    // Already gone, that's fine
  }
}

// Kill any stale instance before we try to bind the port
killStalePid();
writePid();

// ---- Boot ----

const logger = new Logger();

const oscServer = new OscServer(logger);

const oscClient = new OscClient(logger);

const server = new McpServer({
  name: "coralline",
  version: "0.1.0",
});

registerPlayTool(server, oscClient);
registerLoopTools(server, oscClient);
registerPhraseTool(server, oscClient);
registerAnalysisTools(server, oscClient, oscServer);
registerSynthTools(server);
registerFxTool(server);

const transport = new StdioServerTransport();
await server.connect(transport);

process.on("exit", () => {
  oscClient.close();
  oscServer.close();
  removePid();
});

process.on("SIGINT", () => process.exit(0));
process.on("SIGTERM", () => process.exit(0));
