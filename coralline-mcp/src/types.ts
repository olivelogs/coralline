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
  // Clock fields (absent if the quark predates 0.1.9)
  tempo_bpm?: number;
  beats_per_bar?: number;
  bar?: number;
  beat_in_bar?: number;
  next_bar_in_s?: number;
}

// Instantaneous snapshot (window = 0)
export interface AudioSnapshotPong {
  kind: "snapshot";
  rms: number;
  centroid: number;
  flatness: number;
  freq: number;
  hasFreq: number;
  onsetRate: number;
}

// Windowed summary of the last N seconds (window > 0)
export interface AudioWindowPong {
  kind: "window";
  window: number;
  span: number;
  frames: number;
  active_ratio: number;
  rms_mean: number;
  rms_max: number;
  rms_min: number;
  centroid_mean: number;
  flatness_mean: number;
  freq_median: number;
  pitch_stability: number;
  onset_count: number;
  onset_rate: number;
  rms_series: number[];
  centroid_series: number[];
  // Perceived semantic estimate — the seven play dimensions, heard back
  // from the bus (absent if the quark predates 0.1.7)
  perceived?: Record<SemanticDimension, number>;
}

export type AudioPong = AudioSnapshotPong | AudioWindowPong;

export interface ClipPong {
  path: string;
  duration: number;
  sample_rate: number;
  error: string;
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
