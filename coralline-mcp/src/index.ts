#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { Logger } from "./logger.js";
import { OscClient, OscServer } from "./osc.js";
import { registerAnalysisTools } from "./tools/analysis.js";
import { registerFxTool } from "./tools/fx.js";
import { registerLoopTools } from "./tools/loops.js";
import { registerPhraseTool } from "./tools/phrase.js";
import { registerPlayTool } from "./tools/play.js";
import { registerOscTestTool } from "./tools/osc_test.js";
import { registerSynthTools } from "./tools/synths.js";

const logger = new Logger();

// OscServer constructor binds the UDP port — fail fast if occupied.
let oscServer: OscServer;
try {
  oscServer = new OscServer(logger);
} catch (err) {
  console.error(
    "Fatal: failed to bind OSC listener on port 9501.",
    "Is another coralline-mcp instance running?",
    (err as Error).message
  );
  process.exit(1);
}

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
registerOscTestTool(server, oscClient);

const transport = new StdioServerTransport();
await server.connect(transport);

process.on("exit", () => {
  oscClient.close();
  oscServer.close();
});

process.on("SIGINT", () => process.exit(0));
process.on("SIGTERM", () => process.exit(0));
