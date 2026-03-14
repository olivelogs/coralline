import { appendFileSync, existsSync, mkdirSync, renameSync, statSync } from "fs";
import { join } from "path";
import { LOG_DIR } from "./paths.js";
import type { LogEntry } from "./types.js";

const LOG_FILE = join(LOG_DIR, "osc.jsonl");
const LOG_OLD = join(LOG_DIR, "osc.old.jsonl");
const MAX_BYTES = 1_000_000; // 1MB

export class Logger {
  constructor() {
    mkdirSync(LOG_DIR, { recursive: true });
  }

  log(entry: LogEntry): void {
    this.rotateIfNeeded();
    const line = JSON.stringify(entry) + "\n";
    appendFileSync(LOG_FILE, line, "utf-8");
  }

  private rotateIfNeeded(): void {
    if (!existsSync(LOG_FILE)) return;
    try {
      const { size } = statSync(LOG_FILE);
      if (size >= MAX_BYTES) {
        renameSync(LOG_FILE, LOG_OLD);
      }
    } catch {
      // ignore stat errors
    }
  }
}
