import { Client, Server } from "node-osc";
import type { Logger } from "./logger.js";
import type { AudioPong, PendingPong, StatePong } from "./types.js";

const SC_HOST = "127.0.0.1";
const SC_PORT = 57120;
const LISTEN_PORT = 9501;
const PONG_TIMEOUT_MS = 2000;

// ---- OSC Client (fire-and-forget sender) ----

export class OscClient {
  private client: Client;

  constructor(private readonly logger: Logger) {
    this.client = new Client(SC_HOST, SC_PORT);
  }

  send(
    path: string,
    args: (string | number)[],
    reqId: string | null = null
  ): void {
    this.logger.log({
      ts: new Date().toISOString(),
      dir: "out",
      src_addr: "127.0.0.1",
      src_port: 0,
      dst_addr: SC_HOST,
      dst_port: SC_PORT,
      path,
      args,
      req_id: reqId,
    });

    this.client.send({ address: path, args } as Parameters<Client["send"]>[0], (err) => {
      if (err) console.error("[osc] send error:", err);
    });
  }

  close(): void {
    this.client.close();
  }
}

// ---- OSC Server (pong listener) ----

export class OscServer {
  private server: Server;
  private stateQueue: PendingPong<StatePong>[] = [];
  private audioQueue: PendingPong<AudioPong>[] = [];

  constructor(private readonly logger: Logger) {
    this.server = new Server(LISTEN_PORT, "0.0.0.0", () => {
      console.error(`[osc] listening on port ${LISTEN_PORT}`);
    });

    this.server.on("message", (msg: unknown[]) => {
      this.handleMessage(msg as [string, ...unknown[]]);
    });

    this.server.on("error", (err: Error) => {
      console.error("[osc] server error:", err.message);
    });
  }

  private handleMessage(msg: [string, ...unknown[]]): void {
    const [path, ...rawArgs] = msg;
    const args = rawArgs as (string | number)[];

    this.logger.log({
      ts: new Date().toISOString(),
      dir: "in",
      src_addr: "127.0.0.1",
      src_port: SC_PORT,
      dst_addr: "127.0.0.1",
      dst_port: LISTEN_PORT,
      path,
      args,
      req_id: null, // filled in by awaitXxxPong resolution
    });

    if (path === "/coralline/pong/state" && this.stateQueue.length > 0) {
      const pending = this.stateQueue.shift()!;
      clearTimeout(pending.timer);
      pending.resolve(this.parseStatePong(args));
    } else if (path === "/coralline/pong/audio" && this.audioQueue.length > 0) {
      const pending = this.audioQueue.shift()!;
      clearTimeout(pending.timer);
      pending.resolve(this.parseAudioPong(args));
    }
  }

  awaitStatePong(reqId: string): Promise<StatePong> {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        const idx = this.stateQueue.findIndex((p) => p.timer === timer);
        if (idx !== -1) this.stateQueue.splice(idx, 1);
        reject(new Error("Timed out waiting for /coralline/pong/state (2s). Is SuperCollider running?"));
      }, PONG_TIMEOUT_MS);

      // Patch req_id onto log entry when pong arrives — done inline in handleMessage
      // For now store reqId in closure so we can associate it on resolve
      const origResolve = resolve;
      this.stateQueue.push({
        resolve: (val) => {
          // Re-log with req_id attached for traceability
          this.logger.log({
            ts: new Date().toISOString(),
            dir: "in",
            src_addr: "127.0.0.1",
            src_port: SC_PORT,
            dst_addr: "127.0.0.1",
            dst_port: LISTEN_PORT,
            path: "/coralline/pong/state [resolved]",
            args: [],
            req_id: reqId,
          });
          origResolve(val);
        },
        reject,
        timer,
      });
    });
  }

  awaitAudioPong(reqId: string): Promise<AudioPong> {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        const idx = this.audioQueue.findIndex((p) => p.timer === timer);
        if (idx !== -1) this.audioQueue.splice(idx, 1);
        reject(new Error("Timed out waiting for /coralline/pong/audio (2s). Is SuperCollider running?"));
      }, PONG_TIMEOUT_MS);

      const origResolve = resolve;
      this.audioQueue.push({
        resolve: (val) => {
          this.logger.log({
            ts: new Date().toISOString(),
            dir: "in",
            src_addr: "127.0.0.1",
            src_port: SC_PORT,
            dst_addr: "127.0.0.1",
            dst_port: LISTEN_PORT,
            path: "/coralline/pong/audio [resolved]",
            args: [],
            req_id: reqId,
          });
          origResolve(val);
        },
        reject,
        timer,
      });
    });
  }

  close(): void {
    this.server.close();
  }

  // ---- Pong parsers ----

  private parseKV(args: (string | number)[]): Record<string, unknown> {
    const result: Record<string, unknown> = {};
    for (let i = 0; i + 1 < args.length; i += 2) {
      result[String(args[i])] = args[i + 1];
    }
    return result;
  }

  private parseStatePong(args: (string | number)[]): StatePong {
    const kv = this.parseKV(args);
    return {
      loops: Number(kv["loops"] ?? 0),
      loop_names: String(kv["loop_names"] ?? ""),
      running: Number(kv["running"] ?? 0),
    };
  }

  private parseAudioPong(args: (string | number)[]): AudioPong {
    const kv = this.parseKV(args);
    return {
      rms: Number(kv["rms"] ?? 0),
      centroid: Number(kv["centroid"] ?? 0),
      flatness: Number(kv["flatness"] ?? 0),
      freq: Number(kv["freq"] ?? 0),
      hasFreq: Number(kv["hasFreq"] ?? 0),
    };
  }
}
