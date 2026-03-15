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
import { registerSynthTools } from "./tools/synths.js";

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
});

process.on("SIGINT", () => process.exit(0));
process.on("SIGTERM", () => process.exit(0));
