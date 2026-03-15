declare module "osc-min" {
  interface OscArg {
    type: string;
    value: string | number;
  }
  interface OscMessage {
    oscType: "message";
    address: string;
    args: OscArg[];
  }
  interface OscBundle {
    oscType: "bundle";
    timetag: number[];
    elements: (OscMessage | OscBundle)[];
  }
  export function fromBuffer(buf: Buffer): OscMessage | OscBundle;
  export function toBuffer(msg: {
    address: string;
    args: (string | number | { type: string; value: unknown })[];
  }): Buffer;
}
