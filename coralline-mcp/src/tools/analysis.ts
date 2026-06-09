import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { randomUUID } from "crypto";
import { z } from "zod";
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
        oscClient.send(
          "/coralline/ping/state",
          [reqId, oscServer.replyPort ?? 0],
          reqId
        );
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
    "Summarize what SuperCollider has sounded like over the last few seconds: level stats (mean/max/min RMS), " +
      "spectral centroid (brightness in Hz), spectral flatness (0=tonal, 1=noisy), median pitch with stability, " +
      "onset count/rate (rhythmic density), active_ratio (fraction of the window with sound vs silence), and " +
      "rms/centroid time-series showing the shape of the window (building, decaying, pulsing). " +
      "Use after changing a loop or playing a phrase to hear the result. " +
      "Returns an error if SC is not running.",
    {
      window: z
        .number()
        .min(0)
        .max(30)
        .optional()
        .describe(
          "How many seconds back to summarize (default 4). " +
            "0 = instantaneous single-frame snapshot (rarely useful — it may land between notes)."
        ),
    },
    async ({ window }) => {
      const reqId = randomUUID();
      const win = window ?? 4;
      try {
        const pongPromise = oscServer.awaitAudioPong(reqId);
        oscClient.send(
          "/coralline/ping/audio",
          [reqId, oscServer.replyPort ?? 0, win],
          reqId
        );
        const pong = await pongPromise;

        // SC falls back to a snapshot when its history ring is empty
        // (e.g. analyzer just started), so handle both shapes regardless
        // of the window we asked for.
        const result =
          pong.kind === "window"
            ? {
                window_s: pong.window,
                span_s: Number(pong.span.toFixed(2)),
                active_ratio: Number(pong.active_ratio.toFixed(3)),
                rms: {
                  mean: pong.rms_mean,
                  max: pong.rms_max,
                  min: pong.rms_min,
                },
                centroid_hz_mean: Math.round(pong.centroid_mean),
                flatness_mean: Number(pong.flatness_mean.toFixed(3)),
                pitch_hz_median: Number(pong.freq_median.toFixed(1)),
                pitch_stability: Number(pong.pitch_stability.toFixed(3)),
                onset_count: pong.onset_count,
                onset_rate: Number(pong.onset_rate.toFixed(2)),
                rms_series: pong.rms_series,
                centroid_series: pong.centroid_series.map(Math.round),
              }
            : {
                snapshot: true,
                rms: pong.rms,
                centroid_hz: pong.centroid,
                flatness: pong.flatness,
                freq_hz: pong.freq,
                has_pitch: pong.hasFreq === 1,
                onset_rate: pong.onsetRate,
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
    "get_audio_clip",
    "Save the last N seconds of SuperCollider's master output to a WAV file and return its path. " +
      "Use for deep listening beyond get_audio's summary stats: pass the returned path to audio analysis " +
      "tools if available (e.g. audio-analyzer-rs full_analysis / spectral_features / rhythm_analysis) for " +
      "frequency-band balance, key, tempo, stereo field, and more. " +
      "Files land in the repo's recordings/ directory and are kept. Returns an error if SC is not running.",
    {
      duration: z
        .number()
        .min(1)
        .max(60)
        .optional()
        .describe("Seconds of recent audio to capture (default 8, max 60)."),
    },
    async ({ duration }) => {
      const reqId = randomUUID();
      try {
        const pongPromise = oscServer.awaitClipPong(reqId);
        oscClient.send(
          "/coralline/ping/clip",
          [reqId, oscServer.replyPort ?? 0, duration ?? 8],
          reqId
        );
        const pong = await pongPromise;

        if (pong.error) {
          return {
            content: [{ type: "text", text: `Error: ${pong.error}` }],
            isError: true,
          };
        }

        const result = {
          path: pong.path,
          duration_s: Number(pong.duration.toFixed(2)),
          sample_rate: pong.sample_rate,
          hint:
            "Stereo WAV of the most recent master output. " +
            "For deep analysis, pass this path to audio analysis tools (frequency bands, key, tempo, stereo field).",
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
