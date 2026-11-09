import { Buffer } from "buffer"

globalThis.Buffer ??= Buffer
globalThis.process ??= { env: {} }

function deltMinne() {
  try {
    return typeof SharedArrayBuffer !== "undefined" && new WebAssembly.Memory({ initial: 1, maximum: 1, shared: true }).buffer instanceof SharedArrayBuffer
  } catch {
    return false
  }
}

if (!globalThis.crossOriginIsolated && deltMinne()) {
  Object.defineProperty(globalThis, "crossOriginIsolated", { value: true })
}
