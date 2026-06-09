import { dirname, resolve } from "path";

// Use the actual running script path as anchor — reliable even when cwd is /
// (Claude Desktop sets cwd to /)
const DIST_DIR = dirname(process.argv[1]!);
export const PROJ_ROOT = resolve(DIST_DIR, "..");        // coralline-mcp/
export const REPO_ROOT = resolve(PROJ_ROOT, "..");        // monorepo root
export const LOG_DIR = resolve(PROJ_ROOT, "logs");
export const DATA_DIR = resolve(REPO_ROOT, "quark", "Coralline", "Data");
