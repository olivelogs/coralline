import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import type { OscClient } from "../osc.js";

export function registerOscTestTool(server: McpServer, osc: OscClient): void {
  server.tool(
    "osc_test",
    "Diagnostic tool — sends three test messages to SuperCollider to isolate OSC connectivity issues. " +
      "Check the SC post window for output after calling this.",
    {},
    async () => {
      // Test 1: Raw /dirt/play — does node-osc reach SuperDirt at all?
      osc.send("/dirt/play", [
        "s", "supervibe", "n", 0, "gain", 1.0, "orbit", 0,
      ]);

      // Test 2: /coralline/play — does CorallineAgent's OSCdef pick it up?
      osc.send("/coralline/play", ["supervibe", 0, "brightness", 0.5]);

      // Test 3: Bare ping — minimal message to test routing
      osc.send("/coralline/ping/state", []);

      return {
        content: [
          {
            type: "text",
            text: [
              "Sent 3 test messages to 127.0.0.1:57120:",
              "",
              "1. /dirt/play — raw SuperDirt (should produce sound if SuperDirt is running)",
              "2. /coralline/play — semantic play via CorallineAgent (should produce sound + post window log)",
              "3. /coralline/ping/state — state ping (should show 'pong/state sent' in post window)",
              "",
              "Check the SuperCollider post window for output.",
              "If test 1 works but 2/3 don't → CorallineAgent may not be started (run CorallineAgent.start in SC).",
              "If none work → node-osc may not be sending valid UDP, or SC isn't listening on port 57120.",
            ].join("\n"),
          },
        ],
      };
    }
  );
}
