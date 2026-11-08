import { lagBevis } from "../src/bevis.mjs"

function fraHex(h) {
  return Uint8Array.from(h.match(/../g) ?? [], (x) => parseInt(x, 16))
}

const app = globalThis.Pass

window.lagBevisFraApp = async (dg1, sod, sporring, domene, test) => {
  try {
    const start = performance.now()
    const r = await lagBevis({
      dg1: fraHex(dg1),
      sod: fraHex(sod),
      sporring: JSON.parse(sporring),
      domene,
      test,
      framdrift: (t) => app?.framdrift(`${t} (${Math.round((performance.now() - start) / 1000)} s)`),
    })
    app?.ferdig(JSON.stringify(r))
    return r
  } catch (e) {
    const melding = String(e?.message ?? e)
    app?.feil(melding)
    throw e
  }
}

window.isolert = globalThis.crossOriginIsolated === true
app?.klar(String(window.isolert))
