import assert from "node:assert/strict"
import { test } from "node:test"
import { createMockPassport } from "mock-utils/mock-passports"
import { createRequire } from "node:module"
import { lagBevis, pass } from "../src/bevis.mjs"

function bytes(p) {
  const dg1 = Uint8Array.from(p.dataGroups.find((d) => d.groupNumber === 1).value)
  const sod = p.sod.bytes?.toUInt8Array?.() ?? p.sod.toDER?.().toUInt8Array?.()
  return { dg1, sod }
}

test("testpass leses som i ZKPassport", () => {
  const mock = createMockPassport({ firstName: "Ola", lastName: "Nordmann", nationality: "NOR" })
  const { dg1, sod } = bytes(mock)
  assert.ok(sod, "fant ikke SOD-bytes på testpasset")
  const p = pass(dg1, sod)
  assert.equal(p.nationality, "NOR")
  assert.equal(p.mrz, mock.mrz)
})

test("bevis for alder og nasjonalitet godtas av SDK 0.14.2 i testmodus", { timeout: 15 * 60_000 }, async () => {
  const mock = createMockPassport({ firstName: "Ola", lastName: "Nordmann", nationality: "NOR", dateOfBirth: new Date("1990-01-01") })
  const { dg1, sod } = bytes(mock)
  const sporring = { age: { gte: 18 }, nationality: { disclose: true }, firstname: { disclose: true }, lastname: { disclose: true } }
  const start = Date.now()
  const { bevis, resultat } = await lagBevis({ dg1, sod, sporring, domene: "arnee.me", test: true, framdrift: (s) => console.log(s, Math.round((Date.now() - start) / 1000) + " s") })
  console.log("navn:", bevis.map((b) => b.name).join(", "), "tid:", Math.round((Date.now() - start) / 1000), "s")
  const { ZKPassport } = createRequire(import.meta.url)("@zkpassport/sdk")
  const zk = new ZKPassport("arnee.me")
  const svar = await zk.verify({ proofs: bevis, originalQuery: sporring, queryResult: resultat, devMode: true })
  console.log(JSON.stringify({ verified: svar.verified, feil: svar.queryResultErrors }))
  assert.equal(svar.verified, true)
})
