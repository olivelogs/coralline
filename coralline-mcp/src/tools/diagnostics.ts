import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { execFile } from "node:child_process";
import { randomUUID } from "node:crypto";
import { promisify } from "node:util";
import { SCSYNTH_PORT, SC_PORT } from "../osc.js";
import type { OscClient, OscServer } from "../osc.js";

const execFileAsync = promisify(execFile);

// macOS-only: parses `lsof` UDP output to map which processes hold the
// SuperCollider ports, and flags the two failure modes from the roadmap —
// duplicate listeners on a port, and a socket shared across processes
// (the scsynth-inherited-sclang-FD signature).

type PortEntry = {
  command: string;
  pid: number;
  fd: string;
  device: string | null;
  name: string;
  /** true for a bound socket (no "->"), false for an outbound connection */
  listening: boolean;
  port: number | null;
};

function parseLsof(stdout: string): PortEntry[] {
  const entries: PortEntry[] = [];
  for (const line of stdout.split("\n")) {
    if (!line.trim() || line.startsWith("COMMAND")) continue;

    // command pid user fd ... — command is a single token (lsof truncates to 9 chars)
    const head = line.match(/^(\S+)\s+(\d+)\s+\S+\s+(\S+)\s+/);
    if (!head) continue;

    const device = line.match(/\b(0x[0-9a-fA-F]+)\b/)?.[1] ?? null;
    const name = line.trim().split(/\s+/).pop() ?? "";
    const listening = !name.includes("->");
    const port = listening ? Number(name.split(":").pop()) : null;

    entries.push({
      command: head[1]!,
      pid: Number(head[2]),
      fd: head[3]!,
      device,
      name,
      listening,
      port: Number.isFinite(port) ? port : null,
    });
  }
  return entries;
}

async function inspectPorts(): Promise<{ entries: PortEntry[]; error?: string }> {
  try {
    const { stdout } = await execFileAsync(
      "lsof",
      ["-nP", `-iUDP:${SCSYNTH_PORT}`, `-iUDP:${SC_PORT}`],
      { timeout: 4000 }
    );
    return { entries: parseLsof(stdout) };
  } catch (err) {
    const e = err as NodeJS.ErrnoException & { stdout?: string };
    if (e.code === "ENOENT") {
      return { entries: [], error: "lsof not found on PATH" };
    }
    // lsof exits non-zero (typically 1) with empty stdout when nothing matches —
    // that's "nothing bound", not a real error. Parse whatever it captured.
    if (typeof e.stdout === "string") {
      return { entries: parseLsof(e.stdout) };
    }
    return { entries: [], error: e.message };
  }
}

function describePortBlock(entries: PortEntry[], error?: string): { lines: string[]; warnings: string[] } {
  const lines: string[] = [];
  const warnings: string[] = [];

  if (error) {
    lines.push(`  (port inspection unavailable: ${error})`);
    return { lines, warnings };
  }
  if (entries.length === 0) {
    lines.push("  nothing bound on 57110 / 57120 — SuperCollider is not running");
    return { lines, warnings };
  }

  const portLabel: Record<number, string> = {
    [SCSYNTH_PORT]: "scsynth server",
    [SC_PORT]: "sclang langPort (SuperDirt / CorallineAgent)",
  };

  for (const port of [SC_PORT, SCSYNTH_PORT]) {
    const listeners = entries.filter((e) => e.listening && e.port === port);
    lines.push(`  ${port} — ${portLabel[port]}:`);
    if (listeners.length === 0) {
      lines.push("    (no listener)");
    }
    for (const e of listeners) {
      lines.push(`    ${e.command} pid ${e.pid} fd ${e.fd} socket ${e.device ?? "?"}`);
    }
    const pids = new Set(listeners.map((e) => e.pid));
    if (pids.size > 1) {
      warnings.push(
        `⚠️  ${port} has ${pids.size} distinct listener processes (${[...pids].join(", ")}) — duplicate/zombie SuperCollider.`
      );
    }
  }

  // The #1 signature: same kernel socket object held by >1 process (FD inheritance via fork)
  const byDevice = new Map<string, PortEntry[]>();
  for (const e of entries) {
    if (!e.device) continue;
    (byDevice.get(e.device) ?? byDevice.set(e.device, []).get(e.device)!).push(e);
  }
  for (const [device, group] of byDevice) {
    const pids = new Set(group.map((e) => e.pid));
    if (pids.size > 1) {
      const detail = group.map((e) => `${e.command} pid ${e.pid} fd ${e.fd}`).join(" + ");
      warnings.push(
        `⚠️  socket ${device} is shared across processes: ${detail} — FD inheritance (scsynth forked from sclang and inherited the langPort). This is the #1 message-dropping bug.`
      );
    }
  }

  return { lines, warnings };
}

export function registerDiagnosticsTool(
  server: McpServer,
  oscClient: OscClient,
  oscServer: OscServer
): void {
  server.tool(
    "get_diagnostics",
    "Inspect Coralline's plumbing: which processes hold the SuperCollider ports (57110/57120) " +
      "and the MCP's ephemeral reply port, whether the MCP↔SuperDirt path is live, and automatic flags " +
      "for duplicate listeners or a shared (inherited) socket. Use this when communication is patchy " +
      "or get_state/get_audio are failing. macOS-only (uses lsof).",
    {},
    async () => {
      const report: string[] = [];
      const warnings: string[] = [];

      // --- MCP side ---
      report.push("MCP side:");
      report.push(`  this process: pid ${process.pid}`);
      if (oscServer.available) {
        report.push(`  reply listener: bound on 127.0.0.1:${oscServer.replyPort} (ok)`);
      } else {
        report.push("  reply listener: NOT bound — couldn't open a UDP port");
        warnings.push(
          "⚠️  No reply port — get_state/get_audio unavailable in this client (FD exhaustion?). play/loop/fx still work."
        );
      }
      report.push("");

      // --- SC side ports ---
      const { entries, error } = await inspectPorts();
      report.push("SuperCollider ports:");
      const portBlock = describePortBlock(entries, error);
      report.push(...portBlock.lines);
      warnings.push(...portBlock.warnings);
      report.push("");

      // --- Liveness ---
      report.push("Liveness (MCP → SuperDirt → MCP):");
      if (!oscServer.available) {
        report.push("  skipped — reply port unavailable, can't receive a pong");
      } else {
        const reqId = randomUUID();
        try {
          const pongPromise = oscServer.awaitStatePong(reqId);
          oscClient.send(
            "/coralline/ping/state",
            [reqId, oscServer.replyPort ?? 0],
            reqId
          );
          const pong = await pongPromise;
          report.push(
            `  pong received — CorallineAgent ${pong.running === 1 ? "running" : "NOT running"}, ${pong.loops} active loop(s)`
          );
        } catch (err) {
          report.push(`  no pong — ${(err as Error).message}`);
          warnings.push("⚠️  No reply from SuperDirt. Either SC isn't running, or messages aren't reaching CorallineAgent (see port flags above).");
        }
      }

      // --- Summary ---
      report.push("");
      if (warnings.length === 0) {
        report.push("✅ No problems detected — clean single SuperCollider pair, reply path healthy.");
      } else {
        report.push("Flags:");
        report.push(...warnings.map((w) => `  ${w}`));
      }

      return {
        content: [{ type: "text", text: report.join("\n") }],
      };
    }
  );
}
