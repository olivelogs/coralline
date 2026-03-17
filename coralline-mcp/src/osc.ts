import { Client } from "node-osc";
import { createSocket, type Socket } from "node:dgram";
import { fromBuffer } from "osc-min";
import type { Logger } from "./logger.js";
import type { AudioPong, PendingPong, StatePong } from "./types.js";

const SC_HOST = "127.0.0.1";
const SC_PORT = 57120;
const LISTEN_PORT = 9601;
const PONG_TIMEOUT_MS = Number(process.env.CORALLINE_PONG_TIMEOUT_MS) || 5000;

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

    this.client.send(
      { address: path, args } as unknown as Parameters<Client["send"]>[0],
      (err) => {
        if (err) console.error("[osc] send error:", err);
      }
    );
  }

  close(): void {
    this.client.close();
  }
}

// ---- OSC Server (pong listener via dgram + osc-min) ----
// Uses exclusive binding to prevent zombie process port conflicts.

export class OscServer {
  private socket: Socket;
  private stateQueue: PendingPong<StatePong>[] = [];
  private audioPending: Map<string, PendingPong<AudioPong>> = new Map();

  constructor(private readonly logger: Logger) {
    this.socket = createSocket({ type: "udp4", reuseAddr: false });

    this.socket.on("message", (buf, rinfo) => {
      try {
        const parsed = fromBuffer(buf);
        if (parsed.oscType !== "message") return;

        const path = parsed.address;
        const args = parsed.args.map(
          (a: { value: string | number }) => a.value
        ) as (string | number)[];

        this.handleMessage(path, args, rinfo);
      } catch (err) {
        console.error("[osc] parse error:", (err as Error).message);
      }
    });

    this.socket.on("error", (err: Error & { code?: string }) => {
      if (err.code === "EADDRINUSE") {
        console.error(
          `[osc] FATAL: port ${LISTEN_PORT} already in use. ` +
            `Kill stale processes: lsof -i UDP:${LISTEN_PORT} -t | xargs kill`
        );
        process.exit(1);
      }
      console.error("[osc] server error:", err.message);
    });

    this.socket.bind(
      { port: LISTEN_PORT, address: "0.0.0.0", exclusive: true },
      () => {
        console.error(`[osc] listening on port ${LISTEN_PORT} (exclusive)`);
      }
    );
  }

  private handleMessage(
    path: string,
    args: (string | number)[],
    rinfo: { address: string; port: number }
  ): void {
    this.logger.log({
      ts: new Date().toISOString(),
      dir: "in",
      src_addr: rinfo.address,
      src_port: rinfo.port,
      dst_addr: "127.0.0.1",
      dst_port: LISTEN_PORT,
      path,
      args,
      req_id: null,
    });

    if (path === "/coralline/pong/state" && this.stateQueue.length > 0) {
      const pending = this.stateQueue.shift()!;
      clearTimeout(pending.timer);
      pending.resolve(this.parseStatePong(args));
    } else if (path === "/coralline/pong/audio") {
      const kv = this.parseKV(args);
      const reqId = kv["reqId"] as string | undefined;
      if (reqId && this.audioPending.has(reqId)) {
        const pending = this.audioPending.get(reqId)!;
        this.audioPending.delete(reqId);
        clearTimeout(pending.timer);
        pending.resolve(this.parseAudioPong(args));
      }
    }
  }

  awaitStatePong(reqId: string): Promise<StatePong> {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        const idx = this.stateQueue.findIndex((p) => p.timer === timer);
        if (idx !== -1) this.stateQueue.splice(idx, 1);
        reject(
          new Error(
            `Timed out waiting for /coralline/pong/state (${PONG_TIMEOUT_MS}ms). Is SuperCollider running?`
          )
        );
      }, PONG_TIMEOUT_MS);

      const origResolve = resolve;
      this.stateQueue.push({
        resolve: (val) => {
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
        this.audioPending.delete(reqId);
        reject(
          new Error(
            `Timed out waiting for /coralline/pong/audio (${PONG_TIMEOUT_MS}ms). Is SuperCollider running?`
          )
        );
      }, PONG_TIMEOUT_MS);

      const origResolve = resolve;
      this.audioPending.set(reqId, {
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
    this.socket.close();
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
      onsetRate: Number(kv["onsetRate"] ?? 0),
    };
  }
}
