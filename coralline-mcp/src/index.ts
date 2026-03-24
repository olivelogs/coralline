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
import { registerPlayTool } from "./tools/play.js";
import { registerSynthTools } from "./tools/synths.js";

// ---- Pidfile: clean up stale pidfiles from dead processes ----
// If another coralline-mcp is alive (e.g. Claude Desktop), leave it alone.
// The new instance will degrade gracefully without the pong listener.

function cleanStalePidfile(): void {
  if (!existsSync(PID_FILE)) return;

  try {
    const oldPid = Number(readFileSync(PID_FILE, "utf-8").trim());
    if (!Number.isFinite(oldPid) || oldPid <= 0) {
      unlinkSync(PID_FILE);
      return;
    }

    try {
      process.kill(oldPid, 0); // signal 0 = existence check, doesn't kill
      // Still alive — leave it alone
      console.error(
        `[pid] another coralline-mcp is running (pid ${oldPid}), keeping it alive`
      );
    } catch {
      // Process is dead, clean up the stale pidfile
      console.error(`[pid] cleaning stale pidfile (pid ${oldPid} is gone)`);
      unlinkSync(PID_FILE);
    }
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

// Clean up pidfile if the old process is dead (don't kill live instances)
cleanStalePidfile();
writePid();

// ---- Boot ----

const logger = new Logger();

const oscServer = new OscServer(logger);

const oscClient = new OscClient(logger);

const server = new McpServer({
  name: "coralline",
  version: "0.1.3",
});

registerPlayTool(server, oscClient);
registerLoopTools(server, oscClient);
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
