import { createHash } from "node:crypto"
import { Barretenberg, UltraHonkBackend } from "@aztec/bb.js"
import { Noir } from "@noir-lang/noir_js"
import { RegistryClient } from "@zkpassport/registry"
import {
  Binary,
  PassportReader,
  extractTBS,
  getAgeCircuitInputs,
  getBitSize,
  getCscaForPassportAsync,
  getDiscloseCircuitInputs,
  getDSCCircuitInputs,
  getDSCSignatureAlgorithmHashAlgorithm,
  getECDSAInfo,
  getIDDataCircuitInputs,
  getIntegrityCheckCircuitInputs,
  getRSAInfo,
  getServiceScopeHash,
  getServiceSubscopeHash,
  getSodSignatureAlgorithmHashAlgorithm,
  getSodSignatureAlgorithmType,
  getTBSMaxLen,
} from "@zkpassport/utils"

export const KRETSVERSJON = "0.18.0"

export function pass(dg1, sod) {
  const leser = new PassportReader()
  leser.loadPassport(Binary.from(dg1), Binary.from(sod))
  return leser.getPassportViewModel()
}

function offentligSalt(salt) {
  const bytes = Buffer.from(salt.toString(16), "hex")
  return BigInt("0x" + createHash("sha256").update(bytes).digest("hex"))
}

function salter(salt) {
  const offentlig = offentligSalt(salt)
  return { dg1Salt: salt, dg2HashSalt: offentlig, expiryDateSalt: offentlig, privateNullifierSalt: salt }
}

function kurve(navn) {
  const familie = navn.includes("brainpool") ? "brainpool" : "nist"
  return `${familie}_${navn.replace("brainpoolP", "").replace("nist", "").replace("-", "").toLowerCase()}`
}

async function grunnkretser(p, sertifikater) {
  const tbsMaks = getTBSMaxLen(p)
  const csca = await getCscaForPassportAsync(p.sod.certificate, sertifikater)
  if (!csca) throw new Error("Fant ikke CSCA for passets DSC i sertifikatregisteret")
  const cscaNokkel = csca.public_key.type === "EC"
    ? `ecdsa_${kurve(csca.public_key.curve)}`
    : `rsa_${csca.signature_algorithm === "RSA-PSS" ? "pss" : "pkcs"}_${getBitSize(BigInt(csca.public_key.modulus))}`
  const dsc = `sig_check_dsc_tbs_${tbsMaks}_${cscaNokkel}_${getDSCSignatureAlgorithmHashAlgorithm(p)}`
  const tbs = extractTBS(p)
  if (!tbs) throw new Error("Fant ikke TBS i DSC")
  const skjema = p.sod.signerInfo.signatureAlgorithm.name.toLowerCase().includes("pss") ? "pss" : "pkcs"
  const dscNokkel = getSodSignatureAlgorithmType(p) === "ECDSA"
    ? `ecdsa_${kurve(getECDSAInfo(tbs.subjectPublicKeyInfo).curve)}`
    : `rsa_${skjema}_${getBitSize(getRSAInfo(tbs.subjectPublicKeyInfo).modulus)}`
  const idData = `sig_check_id_data_tbs_${tbsMaks}_${dscNokkel}_${getSodSignatureAlgorithmHashAlgorithm(p)}`
  const sa = p.sod.signerInfo.digestAlgorithm.toLowerCase().replace("-", "")
  const dg = p.sod.encapContentInfo.eContent.hashAlgorithm.toLowerCase().replace("-", "")
  return { dsc, idData, integritet: `data_check_integrity_sa_${sa}_dg_${dg}` }
}

function maa(verdi, navn) {
  if (!verdi) throw new Error(`Kunne ikke bygge input til ${navn}`)
  return verdi
}

export function svar(p, sporring) {
  const r = {}
  if (sporring.age?.gte !== undefined) r.age = { gte: { expected: sporring.age.gte, result: true } }
  if (sporring.nationality?.disclose) r.nationality = { disclose: { result: p.nationality } }
  if (sporring.firstname?.disclose) r.firstname = { disclose: { result: p.firstName } }
  if (sporring.lastname?.disclose) r.lastname = { disclose: { result: p.lastName } }
  return r
}

export async function lagBevis({ dg1, sod, sporring, domene, test, salt, framdrift = () => {}, bb }) {
  const ukjent = Object.keys(sporring).filter((k) => !["age", "nationality", "firstname", "lastname"].includes(k))
  if (ukjent.length) throw new Error(`Støtter ikke ${ukjent.join(", ")} ennå`)
  const p = pass(dg1, sod)
  const register = new RegistryClient({ chainId: test ? 11155111 : 1 })
  framdrift("Henter sertifikater og kretser")
  const [sertifikater, manifest] = await Promise.all([
    register.getCertificates(),
    register.getCircuitManifest(undefined, { version: KRETSVERSJON }),
  ])
  const navn = await grunnkretser(p, sertifikater.certificates)
  const hemmelig = salt ?? BigInt("0x" + createHash("sha256").update(Buffer.from(dg1)).update(Buffer.from(sod)).digest("hex").slice(0, 62))
  const s = salter(hemmelig)
  const felles = [s, 0n, getServiceScopeHash(domene), getServiceSubscopeHash(undefined), Math.floor(Date.now() / 1000)]

  const steg = [
    { name: navn.dsc, inputs: maa(await getDSCCircuitInputs(p, hemmelig, sertifikater), navn.dsc) },
    { name: navn.idData, inputs: maa(await getIDDataCircuitInputs(p, hemmelig, hemmelig), navn.idData) },
    { name: navn.integritet, inputs: maa(await getIntegrityCheckCircuitInputs(p, hemmelig, s), navn.integritet) },
  ]
  if (sporring.age) {
    const inputs = maa(await getAgeCircuitInputs(p, sporring, ...felles), "compare_age")
    steg.push({ name: "compare_age", inputs, committedInputs: { minAge: inputs.min_age_required, maxAge: inputs.max_age_required } })
  }
  if (sporring.nationality || sporring.firstname || sporring.lastname) {
    const inputs = maa(await getDiscloseCircuitInputs(p, sporring, ...felles), "disclose_bytes")
    const dg = inputs.salted_dg1.value
    steg.push({
      name: "disclose_bytes",
      inputs,
      committedInputs: { disclosedBytes: dg.slice(5).map((b, i) => b * inputs.disclose_mask[i]), discloseMask: inputs.disclose_mask },
    })
  }

  framdrift("Laster ned kretser")
  const kretser = await Promise.all(steg.map((st) => register.getPackagedCircuit(st.name, manifest)))
  const eier = !bb
  bb ??= await Barretenberg.new()
  const bevis = []
  try {
    for (const [i, st] of steg.entries()) {
      framdrift(`Lager bevis ${i + 1} av ${steg.length}`)
      const krets = kretser[i]
      const noir = new Noir({ bytecode: krets.bytecode, abi: krets.abi, debug_symbols: "", file_map: {} })
      const { witness } = await noir.execute(st.inputs)
      const { proof, publicInputs } = await new UltraHonkBackend(krets.bytecode, bb).generateProof(witness)
      bevis.push({
        proof: publicInputs.map((x) => x.replace(/^0x/, "").padStart(64, "0")).join("") + Buffer.from(proof).toString("hex"),
        vkeyHash: krets.vkey_hash,
        version: manifest.version,
        name: krets.name,
        index: i,
        total: steg.length,
        ...(st.committedInputs ? { committedInputs: { [krets.name]: st.committedInputs } } : {}),
      })
    }
  } finally {
    if (eier) await bb.destroy()
  }
  return { bevis, resultat: svar(p, sporring) }
}
