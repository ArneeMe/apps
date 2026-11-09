import { writeFileSync, appendFileSync } from "node:fs"
import { createRequire } from "node:module"

const ut = process.argv[2] ?? "ut"
const krav = createRequire(new URL("../zkp/package.json", import.meta.url))
const { ZKPassport } = krav("@zkpassport/sdk")
const logg = (s) => appendFileSync(`${ut}/nettside.txt`, `${new Date().toISOString()} ${s}\n`)

const zk = new ZKPassport("arnee.me")
const b = await zk.request({ name: "arnee.me", logo: "https://arnee.me/icons/16plus-32.svg", purpose: "Emulatortest", devMode: true })
const { url, onRequestReceived, onGeneratingProof, onProofGenerated, onResult, onReject, onError } = b.gte("age", 18).disclose("nationality").done()
writeFileSync(`${ut}/lenke.txt`, url.replace("https://zkpassport.id/r?", "zkpassport:///r?"))
logg("forespørsel laget")
onRequestReceived(() => logg("appen koblet til"))
onGeneratingProof(() => logg("appen godtok"))
onProofGenerated((p) => logg(`bevis mottatt: ${p.name}`))
onReject(() => { logg("avslått"); process.exit(0) })
onError((e) => logg(`feil: ${JSON.stringify(e)}`))
onResult(({ verified, result }) => {
  logg(`RESULTAT verified=${verified} ${JSON.stringify(result)}`)
  process.exit(0)
})
setTimeout(() => { logg("tidsavbrudd"); process.exit(0) }, 15 * 60_000)
