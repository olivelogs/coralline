// ---- Semantic dimensions ----

export type SemanticDimension =
  | "brightness"
  | "warmth"
  | "texture"
  | "movement"
  | "space"
  | "weight"
  | "attack";

export const SEMANTIC_DIMENSIONS: SemanticDimension[] = [
  "brightness",
  "warmth",
  "texture",
  "movement",
  "space",
  "weight",
  "attack",
];

// ---- refined_mappings.json shape ----

export interface CurveSpec {
  param: string;
  outLo: number;
  outHi: number;
  curve: "lin" | "exp" | "log" | number;
  weight: number;
  confidence: number;
}

export interface PitchControl {
  param: string;
  drift_semitones: number;
}

export interface SynthMapping {
  brightness?: CurveSpec[];
  warmth?: CurveSpec[];
  texture?: CurveSpec[];
  movement?: CurveSpec[];
  space?: CurveSpec[];
  weight?: CurveSpec[];
  attack?: CurveSpec[];
  pitch_controls?: PitchControl[];
  needs_review?: string[];
}

export type RefinedMappings = Record<string, SynthMapping>;

// ---- effects.json shape ----

export interface FxParam {
  param: string;
  description: string;
  range: string;
  notes?: string;
}

export interface FxCategory {
  category: string;
  params: FxParam[];
}

export interface FxCombo {
  name: string;
  params: string;
}

export interface EffectsData {
  categories: FxCategory[];
  quick_combos: FxCombo[];
}

// ---- OSC pong reply shapes ----

export interface StatePong {
  loops: number;
  loop_names: string;
  running: number;
}

export interface AudioPong {
  rms: number;
  centroid: number;
  flatness: number;
  freq: number;
  hasFreq: number;
}

// ---- Pending pong (FIFO queue entries) ----

export interface PendingPong<T> {
  resolve: (value: T) => void;
  reject: (reason: Error) => void;
  timer: ReturnType<typeof setTimeout>;
}

// ---- Log entry ----

export interface LogEntry {
  ts: string;
  dir: "in" | "out";
  src_addr: string;
  src_port: number;
  dst_addr: string;
  dst_port: number;
  path: string;
  args: (string | number)[];
  req_id: string | null;
}
