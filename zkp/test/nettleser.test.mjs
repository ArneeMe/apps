import assert from "node:assert/strict"
import { test } from "node:test"
import { existsSync } from "node:fs"
import { createRequire } from "node:module"
import { createMockPassport } from "mock-utils/mock-passports"
import { start } from "./server.mjs"

const hex = (b) => Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("")

test("bevis lages i nettleseren slik appen gjør, og godtas av SDK-et", { timeout: 15 * 60_000, skip: !existsSync(new URL("../dist/index.html", import.meta.url)) && "bygg dist først" }, async () => {
  const { chromium } = await import("playwright")
  const mock = createMockPassport({ firstName: "Kari", lastName: "Nordmann", nationality: "NOR", dateOfBirth: new Date("2001-05-17") })
  const dg1 = hex(mock.dataGroups.find((d) => d.groupNumber === 1).value)
  const sod = hex(mock.sod.bytes.toUInt8Array())
  const sporring = { age: { gte: 18 }, nationality: { disclose: true } }

  const server = await start(new URL("../dist", import.meta.url).pathname)
  const nettleser = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {})
  try {
    const side = await nettleser.newPage()
    side.on("console", (m) => console.log("nettleser:", m.text()))
    await side.goto(`http://localhost:${server.address().port}/`)
    await side.waitForFunction(() => typeof window.lagBevisFraApp === "function")
    assert.equal(await side.evaluate(() => window.isolert), true)
    const start = Date.now()
    const r = await side.evaluate(([a, b, c]) => window.lagBevisFraApp(a, b, c, "arnee.me", true), [dg1, sod, JSON.stringify(sporring)])
    console.log("tid i nettleser:", Math.round((Date.now() - start) / 1000), "s,", r.bevis.map((b) => b.name).join(", "))
    const { ZKPassport } = createRequire(import.meta.url)("@zkpassport/sdk")
    const svar = await new ZKPassport("arnee.me").verify({ proofs: r.bevis, originalQuery: sporring, queryResult: r.resultat, devMode: true })
    console.log(JSON.stringify({ verified: svar.verified }))
    assert.equal(svar.verified, true)
  } finally {
    await nettleser.close()
    server.close()
  }
})
