import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { randomUUID } from "crypto";
import type { OscClient } from "../osc.js";
import type { OscServer } from "../osc.js";

export function registerAnalysisTools(
  server: McpServer,
  oscClient: OscClient,
  oscServer: OscServer
): void {
  server.tool(
    "get_state",
    "Query the current state of SuperCollider: which loops are active and whether CorallineAgent is running. " +
      "Sends a ping and waits up to 2 seconds for a reply. Returns an error if SC is not running.",
    {},
    async () => {
      const reqId = randomUUID();
      try {
        // Register listener BEFORE sending ping to avoid race condition
        const pongPromise = oscServer.awaitStatePong(reqId);
        oscClient.send("/coralline/ping/state", [], reqId);
        const pong = await pongPromise;

        const loopNames = pong.loop_names
          ? pong.loop_names.split(",").filter(Boolean)
          : [];

        const result = {
          running: pong.running === 1,
          active_loop_count: pong.loops,
          active_loops: loopNames,
        };

        return {
          content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
      } catch (err) {
        return {
          content: [{ type: "text", text: `Error: ${(err as Error).message}` }],
          isError: true,
        };
      }
    }
  );

  server.tool(
    "get_audio",
    "Query real-time audio analysis from SuperCollider: RMS level, spectral centroid (brightness in Hz), " +
      "spectral flatness (0=tonal, 1=noisy), pitch frequency, and pitch detection confidence. " +
      "Sends a ping and waits up to 2 seconds for a reply. Returns an error if SC is not running.",
    {},
    async () => {
      const reqId = randomUUID();
      try {
        const pongPromise = oscServer.awaitAudioPong(reqId);
        oscClient.send("/coralline/ping/audio", [], reqId);
        const pong = await pongPromise;

        const result = {
          rms: pong.rms,
          centroid_hz: pong.centroid,
          flatness: pong.flatness,
          freq_hz: pong.freq,
          has_pitch: pong.hasFreq === 1,
        };

        return {
          content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
      } catch (err) {
        return {
          content: [{ type: "text", text: `Error: ${(err as Error).message}` }],
          isError: true,
        };
      }
    }
  );
}
