import { Client } from "node-osc";
import { createSocket, type Socket } from "node:dgram";
import type { Logger } from "./logger.js";
import type { AudioPong, PendingPong, StatePong } from "./types.js";

const SC_HOST = "127.0.0.1";
export const SC_PORT = 57120; // sclang langPort — SuperDirt / CorallineAgent listen here
export const SCSYNTH_PORT = 57110; // scsynth server port
const PONG_TIMEOUT_MS = Number(process.env.CORALLINE_PONG_TIMEOUT_MS) || 5000;
const BIND_RETRIES = 3;

type DecodedOscMessage = {
  address: string;
  args: (string | number)[];
};

function align4(offset: number): number {
  return (offset + 3) & ~3;
}

function readOscString(buf: Buffer, offset: number): {
  value: string;
  nextOffset: number;
} {
  const end = buf.indexOf(0, offset);
  if (end === -1) {
    throw new Error("unterminated OSC string");
  }

  return {
    value: buf.toString("utf8", offset, end),
    nextOffset: align4(end + 1),
  };
}

function decodeOscMessage(buf: Buffer): DecodedOscMessage {
  const address = readOscString(buf, 0);
  const typeTag = readOscString(buf, address.nextOffset);

  if (!address.value.startsWith("/")) {
    throw new Error("invalid OSC address");
  }

  if (!typeTag.value.startsWith(",")) {
    throw new Error("invalid OSC type tag string");
  }

  let offset = typeTag.nextOffset;
  const args: (string | number)[] = [];

  for (const tag of typeTag.value.slice(1)) {
    switch (tag) {
      case "s": {
        const parsed = readOscString(buf, offset);
        args.push(parsed.value);
        offset = parsed.nextOffset;
        break;
      }
      case "i":
        args.push(buf.readInt32BE(offset));
        offset += 4;
        break;
      case "f":
        args.push(buf.readFloatBE(offset));
        offset += 4;
        break;
      case "d":
        args.push(buf.readDoubleBE(offset));
        offset += 8;
        break;
      case "T":
        args.push(1);
        break;
      case "F":
        args.push(0);
        break;
      default:
        throw new Error(`unsupported OSC type tag: ${tag}`);
    }
  }

  return { address: address.value, args };
}

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

// ---- OSC Server (pong listener via dgram) ----
// Uses a minimal local OSC decoder for Coralline's pong message shapes.
// Binds an ephemeral port so multiple clients never contend for one socket.

export class OscServer {
  private socket!: Socket;
  private statePending: Map<string, PendingPong<StatePong>> = new Map();
  private audioPending: Map<string, PendingPong<AudioPong>> = new Map();
  private _available = false;
  private _replyPort: number | null = null;

  /** Whether the reply listener bound successfully (false only if we ran out of FDs). */
  get available(): boolean {
    return this._available;
  }

  /** The ephemeral UDP port SuperCollider should send pongs to (null if unbound). */
  get replyPort(): number | null {
    return this._replyPort;
  }

  constructor(private readonly logger: Logger) {
    this.bindEphemeral(BIND_RETRIES);
  }

  // Bind to an OS-assigned ephemeral port (port 0). Unlike a fixed shared port,
  // this never collides — the kernel only ever hands back a free port — so
  // multiple clients (Code, chat, …) each get their own and never contend.
  // The only realistic failure is FD exhaustion, which we retry a few times
  // then degrade gracefully (play/loop/fx don't need a reply port).
  private bindEphemeral(attemptsLeft: number): void {
    const socket = createSocket({ type: "udp4" });
    this.socket = socket;

    socket.on("message", (buf, rinfo) => {
      try {
        const parsed = decodeOscMessage(buf);
        this.handleMessage(parsed.address, parsed.args, rinfo);
      } catch (err) {
        console.error(
          `[osc] parse error: ${(err as Error).message}; raw=${buf.toString("hex")}`
        );
      }
    });

    socket.on("error", (err: Error) => {
      if (!this._available && attemptsLeft > 0) {
        console.error(
          `[osc] reply port bind failed (${err.message}); retrying (${attemptsLeft} left)`
        );
        try {
          socket.close();
        } catch {
          // already closed
        }
        this.bindEphemeral(attemptsLeft - 1);
        return;
      }
      if (!this._available) {
        console.error(
          `[osc] could not open a reply port: ${err.message}. ` +
            `play/loop/synth/fx still work, but get_state and get_audio are unavailable.`
        );
        return;
      }
      console.error("[osc] server error:", err.message);
    });

    socket.bind({ port: 0, address: SC_HOST }, () => {
      this._available = true;
      this._replyPort = socket.address().port;
      console.error(`[osc] reply listener on ${SC_HOST}:${this._replyPort}`);
    });
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
      dst_port: this._replyPort ?? 0,
      path,
      args,
      req_id: null,
    });

    if (path === "/coralline/pong/state") {
      const kv = this.parseKV(args);
      const reqId = kv["reqId"] as string | undefined;
      if (reqId && this.statePending.has(reqId)) {
        const pending = this.statePending.get(reqId)!;
        this.statePending.delete(reqId);
        clearTimeout(pending.timer);
        pending.resolve(this.parseStatePong(args));
      }
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
    if (!this._available) {
      return Promise.reject(
        new Error(
          "No reply port available — couldn't open a local UDP socket for feedback. " +
            "play/loop/fx still work; get_state/get_audio do not. See get_diagnostics."
        )
      );
    }

    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.statePending.delete(reqId);
        reject(
          new Error(
            `Timed out waiting for /coralline/pong/state (${PONG_TIMEOUT_MS}ms). Is SuperCollider running?`
          )
        );
      }, PONG_TIMEOUT_MS);

      const origResolve = resolve;
      this.statePending.set(reqId, {
        resolve: (val) => {
          this.logger.log({
            ts: new Date().toISOString(),
            dir: "in",
            src_addr: "127.0.0.1",
            src_port: SC_PORT,
            dst_addr: "127.0.0.1",
            dst_port: this._replyPort ?? 0,
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
    if (!this._available) {
      return Promise.reject(
        new Error(
          "No reply port available — couldn't open a local UDP socket for feedback. " +
            "play/loop/fx still work; get_state/get_audio do not. See get_diagnostics."
        )
      );
    }

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
            dst_port: this._replyPort ?? 0,
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
