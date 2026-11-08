# ZKPassport-compatible proofs on Android: implementation guide

AI-written research notes (Claude). Researched 2026-11-08 from public zkpassport repos and npm packages. Nothing here was compiled or run on a phone.

All file references are relative to this folder, where the sources were cloned or unpacked:

`R = /tmp/claude-0/-home-user/3619032b-4690-5e54-b3d9-4f6160dd8a04/scratchpad/research-circuits`

| Short name | Path under R | What it is |
|---|---|---|
| sdk-0.14.2 | `npm/src-sdk-0.14.2/src/` | TS source of `@zkpassport/sdk@0.14.2`, extracted from its source maps |
| utils-0.36.0 | `npm/src-utils-0.36.0/src/` | TS source of `@zkpassport/utils@0.36.0` (the exact version SDK 0.14.2 pins) |
| registry-0.14.0 | `npm/src-registry-0.14.0/src/` | TS source of `@zkpassport/registry@0.14.0` (pinned by SDK 0.14.2) |
| circuits-0.18 | `circuits-0.18/` | `zkpassport/circuits` at commit 32147ef (2026-05-07), circuit version 0.18.0 |
| circuits | `circuits/` | `zkpassport/circuits` HEAD (0.22.0, Noir 1.0.0, bb 6.0.0) with history since 2026-01 |
| packages | `zkpassport-packages/` | monorepo HEAD: sdk 0.19.3, utils 0.40.0-beta.3, registry, e2e tests, Rust core |
| noir_rs | `noir_rs/` | `zkpassport/noir_rs` (Rust prover crate) |
| noir_android | `noir_android/` | `madztheo/noir_android` (Kotlin + JNI wrapper around noir_rs) |
| bridge | `npm/bridge/package/src/` | `@obsidion/bridge@0.11.2`, the transport between app and website |

Network note: `*.zkpassport.id` and `crs.aztec.network` were blocked from this sandbox. So artifact sizes below come from manifests in the repos, not from downloads.

## 0. The short version

- SDK 0.14.2 accepts two kinds of submission. **Fast mode** (the default, `m=fast` in the request URL) sends 3 base proofs plus 1 proof per disclosure, each verified on its own. **Compressed mode** sends one recursive `outer_count_N` proof. Do fast mode. It needs no recursion and no cloud prover.
- Proof system: Barretenberg **UltraHonk, Poseidon2 transcript, ZK on** (bb target `noir-recursive`). SDK 0.14.2 verifies with `@aztec/bb.js 4.2.0-aztecnr-rc.2`.
- So you must prove with **bb 4.2.0-aztecnr-rc.2 + Noir 1.0.0-beta.20**, against circuit version **0.18.0 or 0.19.0** (the only versions packaged with that bb). Circuit 0.20.0 moved to bb 5.0.0, which SDK 0.14.2 probably cannot verify (unverified, but the proof format changes between bb majors).
- Native prover: `noir_rs` tag **`v1.0.0-beta.20-1`** (pins `barretenberg-rs =4.2.0-aztecnr-rc.2`). It solves the witness itself from ACIR bytecode plus a flat initial witness map. A ready Android build exists: `noir_android` tag **`v1.0.0-beta.20-2`** (prebuilt `libnoir_java.so`, arm64-v8a, 29.7 MB).
- Circuits are not bundled anywhere. They are downloaded per version from `https://circuits2.zkpassport.id/{testnet|mainnet}/...`. devMode uses `testnet` (Sepolia).
- Mock passports: `createMockPassport()` in `@zkpassport/utils/mock-passports` (published from 0.40.0-beta.0). It ships ZKR DSC certificates **with their private keys**, chaining to ZKR CSCAs that are in the Sepolia certificate registry. Proofs from ZKR passports carry a mock nullifier type that the SDK accepts only with `devMode: true`.
- Input building (Poseidon2 commitments, Merkle path, RSA reduction params, ECDSA low-s) is the bulk of the work. ZKPassport is porting it to Rust (`packages/rust/crates/zkpassport-core`). Linking that crate and noir_rs into one `.so` is the cleanest way to meet the "one native library" rule.

## 1. Circuits per query, names, versions and proof chain

### 1.1 Proof chain in fast mode

The SDK sorts the received proofs and walks them in this order, checking that each proof's input commitment equals the previous proof's output commitment:

`sdk-0.14.2/public-input-checker.ts` lines 2172-2195 (order list) and 2693-2760 (commitment checks).

```
sig_check_dsc_*            pub inputs: [certificate_registry_root, comm_out]
   | comm_out == comm_in
sig_check_id_data_*        pub inputs: [comm_in, comm_out]
   | comm_out == comm_in
data_check_integrity_*     pub inputs: [comm_in, comm_out]
   | comm_out == comm_in (same value for every disclosure proof)
disclosure proofs          pub inputs: 8 fields (below)
```

Public-input counts: `utils-0.36.0/circuits/index.ts` `getNumberOfPublicInputs()` (dsc 2, id_data 2, integrity 2, every disclosure circuit 8, outer `8 + N - 3`). Index helpers: `circuits/dsc.ts`, `circuits/id-data.ts`, `circuits/integrity.ts`.

The 8 public inputs of every standard disclosure circuit, in order (see also `circuits-0.18/src/noir/bin/compare/age/standard/src/main.nr`):

| idx | value | checked by SDK |
|---|---|---|
| 0 | `comm_in` | equals integrity `comm_out` |
| 1 | `current_date` (unix seconds) | not older than `validity` (default 7 days) |
| 2 | `service_scope` = sha256(domain)[0..31] as int | equals `getServiceScopeHash(domain)` |
| 3 | `service_subscope` = sha256(scope)[0..31], or 0 | equals scope hash if a scope was set |
| 4 | `param_commitment` | recomputed from `committedInputs` |
| 5 | nullifier type (0 non-salted, 1 salted, 2 non-salted mock, 3 salted mock) | mock types rejected unless devMode |
| 6 | scoped nullifier | becomes `uniqueIdentifier` |
| 7 | OPRF public key hash (0 when unsalted) | only checked for salted |

The DSC proof's `certificate_registry_root` is checked on-chain with `RootRegistry.isRootValid(1, root, now)` on Sepolia (devMode) or Ethereum mainnet (`public-input-checker.ts` 1838-1874).

### 1.2 Which circuits your queries need

| Query (website) | Circuit(s) | `committedInputs` key and shape |
|---|---|---|
| always | `sig_check_dsc_tbs_{700,1000,1200,1600}_{csca key}_{dsc sig hash}` | none |
| always | `sig_check_id_data_tbs_{...}_{dsc key}_{sod sig hash}` | none |
| always | `data_check_integrity_sa_{sha*}_dg_{sha*}` | none |
| `.gte("age", 16)` / `.gte("age", 18)` | `compare_age` | `compare_age: { minAge, maxAge }` (maxAge 0 = unbounded) |
| `.disclose("nationality")`, `.disclose("firstname")`, `.disclose("lastname")` | one `disclose_bytes` proof for all disclosed fields together | `disclose_bytes: { discloseMask: number[90], disclosedBytes: number[90] }` |
| `.in("nationality", [...])` (if you ever need it) | `inclusion_check_nationality` | country list, padded to 200 |

So "age >= 18 + nationality + first/last name" is 5 proofs: dsc, id_data, integrity, compare_age, disclose_bytes. This exact set (age 18 + nationality on a mock passport) is the e2e fast-mode test: `packages/packages/e2e/tests/fast-mode.test.ts` lines 13-19.

Circuit name examples for the default mock passport (RSA-2048 SHA-256): `sig_check_dsc_tbs_700_rsa_pkcs_2048_sha256`, `sig_check_id_data_tbs_700_rsa_pkcs_2048_sha256`, `data_check_integrity_sa_sha256_dg_sha256`, `compare_age`, `disclose_bytes`.

How the app derives the three base names (copied from the closed app): `packages/packages/e2e/src/prove.ts` `getBaseCircuitNames()` lines 419-451. Rust port: `packages/rust/crates/zkpassport-core/src/select.rs`. Rules:
- `tbs_N`: smallest of 700/1000/1200/1600 that fits the DSC TBS length (`utils-0.36.0/circuit-matcher.ts` `getTBSMaxLen`).
- CSCA key: `rsa_pkcs_<bits>`, `rsa_pss_<bits>`, or `ecdsa_nist_p256` / `ecdsa_brainpool_256r1` etc.
- Hash suffix: from the DSC certificate signature algorithm (dsc circuit) and the SOD signer algorithm (id_data circuit).
- Old manifests (< 0.5.0) used `data_check_integrity_shaX`. Not relevant for 0.18/0.19.

Version 0.18.0 has 790 circuits: 284 dsc variants, 282 id_data, 25 integrity, plus disclosure, facematch and outer (`circuits-0.18/src/ts/tests/fixtures/circuit-manifest.json`).

### 1.3 Versions the SDK expects

- Each `ProofResult` carries `version` (for example `"0.18.0"`). The SDK fetches `circuits2.zkpassport.id/{testnet|mainnet}/by-version/<version>/manifest.json`, takes `circuits[name].hash`, downloads `by-hash/<hash>.json`, validates `vkey_hash`, and verifies with that vkey (`sdk-0.14.2/index.ts` lines 672-735; URLs in `registry-0.14.0/constants.ts`).
- In fast mode the circuit registry root is not checked on-chain. Any version present on circuits2 for that chain is accepted, as long as bb.js 4.2 can verify it.
- Toolchain per circuit version, from `circuits` history (`git log -- package.json`):

| circuits version | date | Noir | bb | notes |
|---|---|---|---|---|
| 0.18.0 | 2026-05-07 | 1.0.0-beta.20 | 4.2.0-aztecnr-rc.2 | matches SDK 0.14.2 / utils 0.36.0 release day |
| 0.19.0 | 2026-06-18 | 1.0.0-beta.20 | 4.2.0-aztecnr-rc.2 | ecdsa/rsa lib bumps, outer fix |
| 0.20.0 | 2026-07-21 | 1.0.0-beta.22 | 5.0.0 | not for SDK 0.14.x (likely) |
| 0.21.x / 0.22.0 | 2026-10/11 | beta.22 / 1.0.0 | 5.0.0 / 6.0.0 | adds NONE nullifier type |

Recommendation: target 0.18.0. Check that `https://circuits2.zkpassport.id/testnet/by-version/0.18.0/manifest.json` exists before you build on it (I could not reach the host).

### 1.4 What the app sends over the bridge

Each proof is a `ProofResult` (`utils-0.36.0/types/index.ts` line 388):

```json
{ "proof": "<hex, no 0x: public inputs (32 B each) then proof bytes>",
  "vkeyHash": "0x...", "version": "0.18.0", "name": "compare_age",
  "committedInputs": { "compare_age": { "minAge": 18, "maxAge": 0 } },
  "index": 3, "total": 5 }
```

Message sequence (from `packages/packages/e2e/src/headless-app.ts` lines 37-100, which mimics the closed app): `accept`, then `proof` once per proof, then `done` with the query result, for example `{ age: { gte: { expected: 18, result: true } }, nationality: { disclose: { result: "ZKR" } } }` (`prove.ts` `getQueryResult`). The SDK ends with `verify()` and fires `onResult`.

Bridge: `wss://bridge.zkpassport.id`, secp256k1 ECDH, AES-256-GCM (`bridge/encryption.ts`, `bridge/bridge-connection.ts`). The protocol facts are written down in `packages/rust/crates/zkpassport-bridge/src/lib.rs` header (key = first 32 bytes of the compressed shared point, nonce = sha256(bridge id)[0..12], deflate + base64 chunks). That crate says it is wire-compatible with bridge v0.12. SDK 0.14.2 uses `^0.11.2`. Compatibility between 0.11 and 0.12 is unverified.

Request URL fields (`sdk-0.14.2/index.ts` `_getUrl`): `d` domain, `t` topic, `c` base64 query JSON, `s` base64 service JSON, `p` pubkey, `m` mode, `v` SDK version, `dt` earliest allowed timestamp, `dev` 1 or 0, optional `nt`, `oprf_k`.

## 2. Building circuit inputs from passport data

### 2.1 Data you need from the chip

- **DG1** (`61 5B 5F 1F 58` + 88-char TD3 MRZ for passports).
- **SOD** (EF.SOD, DER CMS). It contains eContent (LDS security object with DG hashes), signedAttrs, the signer signature, and the **DSC certificate**.
- DG2 is only needed for its hash, which is already in the SOD. The mock passport has no DG2.
- The **CSCA** is not on the chip. It comes from the certificate registry (2.3).

`PassportViewModel` (`utils-0.36.0/types/index.ts`) is the structure all builders take. `SOD.fromDER()` (`utils-0.36.0/passport/sod.ts`) parses the SOD.

### 2.2 Builder functions (utils 0.36.0, `circuit-matcher.ts`)

| Circuit | Function | Line | Main inputs it produces |
|---|---|---|---|
| dsc | `getDSCCircuitInputs(passport, salt, packagedCerts)` | 500 | `certificate_registry_root`, `certificate_tree_index`, `certificate_tree_hash_path` (height 16), tags, `country`, `csc_expiry`, `csc_fingerprint`, revocation and masterlist roots, `salt`, `tbs_certificate` (padded to tbs_N), `dsc_signature`, CSCA key (`csc_pubkey` + `csc_pubkey_redc_param` + `exponent` + `pss_salt_len` for RSA, or `csc_pubkey_x/y` for EC) |
| id_data | `getIDDataCircuitInputs(passport, saltIn, saltOut)` | 627 | `dg1` (95 B), `signed_attributes` (256 B), `e_content` (700 B), `comm_in` = Poseidon2(salt, country, padded TBS), DSC public key + redc param, `sod_signature`, `tbs_certificate` |
| integrity | `getIntegrityCheckCircuitInputs(passport, saltIn, salts)` | 704 | salted dg1, salted private nullifier, expiry and dg2 salts, `signed_attributes`, `e_content`, `comm_in` |
| compare_age | `getAgeCircuitInputs(passport, query, salts, nullifierSecret=0, scope, subscope, nowSeconds)` | 904 | salted values, `current_date`, `comm_in`, `min_age_required`, `max_age_required`, `nullifier_secret`, `oprf_proof` (zero proof) |
| disclose_bytes | `getDiscloseCircuitInputs(...)` same args | 785 | salted values, `disclose_mask` (90), `comm_in`, scopes, nullifier secret |
| nationality inclusion | `getNationalityInclusionCircuitInputs(...)` | 986 | as above + `country_list` (200 x `str<3>`) |

Shared helpers: `getIDDataInputs` (line 357), `calculatePrivateNullifier`, `hashSaltCountryTbs`, `hashSaltCountrySignedAttrDg1EContentPrivateNullifier`, `hashSaltDg1Dg2HashPrivateNullifier` (`utils-0.36.0/circuits/index.ts` lines 14-105), `processECDSASignature` (low-s, fixed width), `redcLimbsFromBytes` (`barrett-reduction.ts`), sizes in `constants/index.ts` (DG1 95, eContent 700, signedAttrs 256). `OPRF_ZERO_PROOF` is in `oprf/constants.ts`.

Salt handling, copied from the closed app into `packages/packages/e2e/src/prove.ts` lines 399-414:

```ts
salts = { dg1Salt: salt, privateNullifierSalt: salt,
          dg2HashSalt: sha256(saltBytes), expiryDateSalt: sha256(saltBytes) }
dsc:       getDSCCircuitInputs(passport, salt, certs)
id_data:   getIDDataCircuitInputs(passport, salt, salt)
integrity: getIntegrityCheckCircuitInputs(passport, salt, salts)
age etc.:  get*CircuitInputs(passport, query, salts, 0n, getServiceScopeHash(domain),
                             getServiceSubscopeHash(scope), Math.floor(Date.now()/1000))
```

The full proving loop, including the exact `committedInputs` the app records, is `prove.ts` `proveSubproofs()` lines 62-185. Use it as the reference for your Kotlin orchestration.

`disclosedBytes` for `disclose_bytes` is `salted_dg1.value.slice(5)` multiplied element-wise by the mask (`prove.ts` lines 140-151). Mask ranges for firstname, lastname and nationality come from `getFirstNameRange` etc. in `utils-0.36.0/passport/getters.ts` (via `disclose.ts`).

### 2.3 Certificate registry and the CSCA list

- Packaged certificates file (schema v1): `https://certificates.zkpassport.id/{testnet|mainnet}/<root>.json` (`registry-0.14.0/constants.ts` lines 17-40). It has `root`, `timestamp`, `certificates[]` (country, algorithm, public key, validity, fingerprint, SKI/AKI, tags), `revocations`, `masterlists`. Sample: `circuits-0.18/src/ts/tests/fixtures/csca-packaged-certs.json`.
- Latest root: `eth_call` to RootRegistry `0x1D0000020038d6E40E1d98e09fA1bb3A7DAA8B70` (same address on Ethereum, Base and Sepolia) with selector `0xc3bc16e8` (`latestRoot(bytes32)`) and registry id 1 left-padded to 32 bytes (`registry-0.14.0/client.ts` lines 152-167). The SDK verifies with `isRootValid(bytes32,bytes32,uint256)` = `0x2aae4296`.
- Merkle tree: leaves from `getCertificateLeafHash` (`utils-0.36.0/registry/index.ts` line 199), Poseidon2, height 16. `getDSCCircuitInputs` builds the proof over all leaves itself.
- CSCA matching: `getCscaForPassportAsync` (AKI/SKI match, then signature check across the country's CSCAs; `circuit-matcher.ts` line 267).
- Where the CSCAs come from: ZKPassport builds the list from national master lists (the older interpreter in `circuits/src/rust/masterlist-interpreter/certificates/` holds certificates named `*_de*.cer`, which suggests the German BSI master list) and publishes it to the registry. The current build pipeline is not in the public repos I found (uncertain).

## 3. Circuit artifacts

- Not bundled in any npm package. Fetched per version:
  - manifest: `https://circuits2.zkpassport.id/{testnet|mainnet}/by-version/<ver>/manifest.json` (also `by-root/<root>/manifest.json`)
  - circuit: `https://circuits2.zkpassport.id/{testnet|mainnet}/by-hash/<vkey_hash>.json`
  - IPFS fallback: `https://ipfs.zkpassport.id/ipfs/<cid>` (each manifest entry has `cid`)
  - Older, still in utils: `https://circuits.zkpassport.id/versions/<ver>/<name>.json.gz`, `/artifacts/<name>_<hash16>.json.gz`, `/hashes/<hash>.json.gz` (`utils-0.36.0/circuits/index.ts` lines 150-180).
- Packaged circuit JSON (written by `circuits-0.18/src/ts/scripts/circuit-packager.ts` lines 150-175): `name`, `noir_version`, `bb_version`, `size` (gate count), `abi`, `bytecode` (base64 gzipped ACIR, as from `nargo compile`), `vkey` (base64 of `bb write_vk -t noir-recursive` output), `vkey_hash`, `hash`.
- Manifest entry: `{ hash, cid, size }`, where `size` is the gate count. Gate counts in 0.18.0:

| circuit | gates | dyadic size |
|---|---|---|
| sig_check_dsc_tbs_700_rsa_pkcs_2048_sha256 (mock) | 128,266 | 2^17 |
| sig_check_id_data_tbs_700_rsa_pkcs_2048_sha256 (mock) | 107,499 | 2^17 |
| data_check_integrity_sa_sha256_dg_sha256 | 79,324 | 2^17 |
| compare_age | 80,084 | 2^17 |
| disclose_bytes | 73,737 | 2^17 |
| inclusion_check_nationality | 84,817 | 2^17 |
| sig_check_dsc_tbs_1000_rsa_pkcs_4096_sha512 (typical real CSCA) | 434,373 | 2^19 |
| largest dsc / id_data variants | 860,855 / 579,494 | 2^20 |
| outer_count_5 | 4,132,320 | 2^22 |

- File sizes of the packaged JSON: not measured (hosts blocked). Expect hundreds of KB to a few MB each; cache them by `vkey_hash`.
- SRS: noir_rs downloads BN254 G1 points from `https://crs.aztec.network/g1.dat` (64 bytes per point) unless you pass a local file (`noir_rs/src/backends/barretenberg/srs/netsrs.rs`). The closed app sets up one SRS of at least 500,000 points (`packages/rust/crates/zkpassport-core/src/plan.rs` line 11), that is about 32 MB for 2^19. bb only honours the first SRS setup per process, so set it up once with the largest size you will need (noir_rs README).

## 4. Proving backend and proof format

- Proof system: UltraHonk, oracle hash Poseidon2, ZK enabled. That is bb `-t noir-recursive` (`circuits-0.18/scripts/prove-honk.sh`, `circuits-0.18/src/ts/circuits.ts` lines 95-120) and noir_rs `settings_ultra_honk_poseidon2()` (`ipa_accumulation false, oracle_hash poseidon2, disable_zk false`). EVM variants (keccak, no ZK) are only for `compressed-evm` mode.
- Verifier side: `UltraHonkVerifierBackend.verifyProof({ proof, publicInputs, verificationKey })` from `@aztec/bb.js 4.2.0-aztecnr-rc.2` (`sdk-0.14.2/index.ts` lines 625-735).
- Rust crate: **`noir_rs`**, `https://github.com/zkpassport/noir_rs`, tag `v1.0.0-beta.20-1`, feature `barretenberg`. Dependencies at that tag: Noir crates `v1.0.0-beta.20`, `barretenberg-rs = "=4.2.0-aztecnr-rc.2"` with feature `ffi`.
- API at that tag (`git -C noir_rs show v1.0.0-beta.20-1:src/backends/barretenberg/prove.rs`):

```rust
setup_srs(circuit_size: u32, srs_path: Option<&str>) -> Result<u32, String>
setup_srs_from_bytecode(bytecode_b64: &str, srs_path: Option<&str>, recursive: bool)
prove_ultra_honk(bytecode_b64: &str, initial_witness: WitnessMap<FieldElement>,
                 verification_key: Vec<u8>, low_memory_mode: bool,
                 max_storage_usage: Option<u64>) -> Result<Vec<u8>, String>
get_ultra_honk_verification_key(bytecode_b64, low_memory_mode, max_storage_usage)
verify_ultra_honk(proof: Vec<u8>, vk: Vec<u8>) -> Result<bool, String>
witness::from_vec_str_to_witness_map(Vec<&str>)
```

- Witness generation: yes, `prove_ultra_honk` runs the ACVM itself (`execute(bytecode, initial_witness)` with the BN254 black-box solver). You give it the **initial witness**: every ABI parameter flattened in ABI order into field elements, keys 0..n-1. noir_rs does not do ABI encoding. `noir_android` does it in Kotlin: `Circuit.kt` `generateWitnessMap()` (handles field, integer, boolean, string, nested arrays, structs; about 150 lines, see `git -C noir_android show v1.0.0-beta.20-2:lib/src/main/java/com/noirandroid/lib/Circuit.kt`).
- Output layout: `[u32 BE count][public inputs, 32 B each][proof]`. Strip the first 4 bytes and hex-encode the rest to get the string the SDK expects in `ProofResult.proof`.
- Pass the packaged `vkey` (base64 to bytes; noir_android wants hex) as `verification_key` to skip the vk computation.
- Android: noir_rs README documents `cargo build --target aarch64-linux-android --features barretenberg` (and `x86_64-linux-android`), API level via `ANDROID_API_LEVEL` (default 34). NDK helper scripts: `noir_rs/scripts/android-ndk-cc.sh`, `android-ndk-ar.sh`.
- Existing Kotlin binding: **noir_android** by Theo Madzou (ZKPassport developer).
  - Tag `v1.0.0-beta.20-2` (2026-05-08) uses `noir_rs` tag `v1.0.0-beta.20-1`, so bb 4.2.0-aztecnr-rc.2. Right toolchain for SDK 0.14.x.
  - Prebuilt release assets: `libnoir_java_arm64-v8a.so` (29,748,864 bytes, confirmed via HTTP HEAD), `libnoir_java_x86_64.so`, and `libc++_arm64-v8a.so` / `libc++_x86_64.so`. The prebuilt bb links LLVM libc++ (`std::__1`), so it ships a separate `libc++.so` (`lib/build.gradle.kts` lines 125-165). That is two `.so` files. If your rule is literally one native library, build your own crate with libc++ linked statically. I did not check if bb 4.2's prebuilt static lib allows that.
  - JNI surface (`Noir.kt`): `prove(bytecode, witnessMap, vkHex, "ultra_honk", lowMemory, storageCap): String`, `verify`, `setup_srs`, `setup_srs_from_bytecode`, `execute`, `get_verification_key`.
  - Its Kotlin side uses Gson. Your project rules allow no libraries, so copy the witness flattening logic and parse JSON with `org.json`.
- Other mobile options: Swoir (iOS), mopro. Not needed here.
- Newer tags (`noir_rs v1.0.0-1`, `noir_android 1.0.0-1`) are Noir 1.0.0 + bb 6.0.0. Use them only if the website upgrades its SDK and you move to circuits 0.22.

### 4.1 Suggested single native library

One Rust `cdylib` (for example `libzkp.so`) with JNI exports, built for `aarch64-linux-android`:

1. `noir_rs` (tag `v1.0.0-beta.20-1`, feature `barretenberg`): SRS setup, prove.
2. Optional, `zkpassport-core` + `zkpassport-bridge` from `packages/rust/crates/` (path or git dependency): SOD parsing, circuit name selection, input generation for dsc, id_data, integrity, age, disclose, country checks; bridge crypto. See `packages/rust/README.md`. Caveats: it is a work in progress, targets current circuits (0.22), and lists RSA-2048/SHA-256 and ECDSA-P256 as the only vector-tested fixtures. The age and disclose `main.nr` signatures did not change between 0.18 and HEAD (`git diff 32147ef HEAD`), but 173 circuit files did, so test each circuit by executing its witness before proving.
3. JNI glue in the style of `noir_android/lib/src/main/java/noir_java/src/lib.rs`.

Without item 2 you port the builders to Kotlin. You then need Poseidon2 over BN254 (match `@zkpassport/poseidon2`, which uses the barretenberg parameters), SHA-1/2, ASN.1 parsing of SOD and certificates, and Barrett reduction parameters. Plan several days for that.

## 5. Mock passports and devMode

- Generator: `createMockPassport(options)` in `packages/packages/zkpassport-utils/src/mock-passports/index.ts`. Published as `@zkpassport/utils/mock-passports` from 0.40.0-beta.0 (checked in `npm/u040/package/dist/esm/mock-passports`). Not in 0.36.0.
  - Defaults: John Miller Smith, nationality ZKR, born 1995-11-12, expires 2035-01-01, document ZP1111111, signature `rsa-2048-sha256`.
  - Signatures: `rsa-2048-sha256`, `rsa-3072-sha384`, `rsa-4096-sha512`, `rsa-pss-4096-sha512`, `ecdsa-p256-sha256`, `ecdsa-p384-sha384`, `ecdsa-p521-sha512`.
  - MRZ: `P<ZKR...`, check digits are `_`. No photo or DG2.
  - It builds the SOD with `generateSod` and signs signedAttrs deterministically with `sign.ts` (pure JS RSA PKCS#1, PSS, ECDSA). Easy to port to Kotlin with `java.security`.
- Key material: `packages/packages/zkpassport-utils/src/mock-passports/dscs.json`. One entry per signature, each with `certificate` (base64 DER DSC) and `privateKey` (base64 DER PKCS#1 RSA or SEC1 EC key). The comment says these DSCs chain to the ZKR CSCAs in the testnet (Sepolia) certificate registry. The ZKR CSCA private keys are not public (`circuits/.gitignore` excludes `src/ts/tests/fixtures/zkr/keypairs`). ZKR CSCA and DSC certificates in PEM form: `circuits-0.18/src/ts/tests/fixtures/zkr/`.
- Older test keys (not ZKR, not in any registry): `circuits-0.18/src/ts/tests/fixtures/dsc-keypair-{rsa,ecdsa}.json` and the generator `src/ts/passport-generator.ts`. Use the ZKR ones.
- How devMode works:
  - Website: `zkPassport.request({ ..., devMode: true })` puts `dev=1` in the URL. In `verify()` the SDK then uses chain 11155111 (Sepolia) for the certificate root check and the testnet circuit manifest (`sdk-0.14.2/index.ts` line 675, `public-input-checker.ts` 1846).
  - Circuits: when the issuing country in DG1 is ZKR, `nullify()` sets the nullifier type to `NON_SALTED_MOCK` (2) or `SALTED_MOCK` (3) (`circuits-0.18/src/noir/lib/commitment/scoped-nullifier/src/lib.nr` lines 16-17, 93-106).
  - SDK: a mock nullifier type gives `verified = false` unless devMode is on (`index.ts` lines 657-672).
  - Your app should read `dev=1` and, in that case, fetch certs and the manifest from testnet and allow a mock passport.
- End-to-end reference that does exactly this (SDK request, bridge, mock passport, fast mode, devMode verify on a Sepolia fork): `packages/packages/e2e/tests/fast-mode.test.ts` + `src/headless-app.ts` + `src/prove.ts`. It runs against the current SDK and circuits, not 0.14.2/0.18.0.

## 6. Performance and the minimal path

### 6.1 Rough cost on a phone (estimates, not measured)

No benchmarks are published in these repos. Order-of-magnitude guesses for native bb UltraHonk on a recent arm64 phone, 8 threads:

- 2^17 circuits (integrity, age, disclose, mock dsc and id_data): about 1 to 4 s each, peak memory about 200 to 500 MB.
- 2^19 (a typical real-passport dsc with an RSA-4096 CSCA): about 5 to 15 s, peak memory about 1 to 2 GB. Use `low_memory_mode = true` with a `max_storage_usage` budget on phones with less RAM.
- Mock passport, 5 proofs: probably 10 to 20 s in total plus downloads.
- SRS: about 32 MB for 500k points. Download once and keep it in app storage.
- Measure on your target device before you promise anything.

### 6.2 Minimal path to a first devMode age proof

1. **Pin versions.** Circuits 0.18.0 (testnet), noir_rs `v1.0.0-beta.20-1`, or the prebuilt `noir_android v1.0.0-beta.20-2` `.so` for a first test.
2. **Get a reference vector on desktop first.** In Node with `@zkpassport/utils@0.36.0`, `@noir-lang/noir_js@1.0.0-beta.20`, `@aztec/bb.js@4.2.0-aztecnr-rc.2`: create a mock passport (copy `mock-passports/index.ts`, `sign.ts`, `dscs.json` from utils 0.40, since 0.36 lacks them), run the builders as in `e2e/src/prove.ts`, dump each circuit's input JSON and the resulting proof hex. These JSON files are your test fixtures for the Android side.
3. **Prove one circuit on Android.** Download `compare_age` from the testnet 0.18.0 manifest, flatten the dumped input JSON with the ABI, call `prove_ultra_honk`, strip 4 bytes, hex-encode. Check it with `verify_ultra_honk` on the device and with bb.js 4.2 on desktop.
4. **Prove all 5** (dsc, id_data, integrity, compare_age, disclose_bytes) from fixed fixture inputs. Then call the SDK's `ZKPassport.verify({ proofs, originalQuery, queryResult, devMode: true })` on desktop with a hand-written `queryResult`. The domain must match the one used for `service_scope`.
5. **Generate inputs on the device.** Either link `zkpassport-core` or port the builders. Compare against the fixtures byte for byte.
6. **Add the bridge** (WebSocket + secp256k1 ECDH + AES-GCM + deflate) and the request-URL parser. Send `accept`, each `proof`, then `done`.
7. **Real passports last.** Use NFC (BAC/PACE) to read DG1 and SOD, fetch the mainnet certificates, and expect larger dsc circuits.

## 7. Open questions and uncertainties

- Whether `circuits2.zkpassport.id/testnet/by-version/0.18.0/` and `0.19.0/` are still online. Not checkable from here.
- Whether bb.js 4.2.0-aztecnr-rc.2 can verify bb 5/6 proofs. Assumed not. Do not mix.
- Whether `zkpassport-bridge` (written for bridge v0.12) talks to SDK 0.14.2's bridge v0.11.2 without changes.
- Whether the ZKR CSCAs will stay in the Sepolia registry. They are there as of the current e2e tests.
- The packaged circuit file sizes and phone timings above are estimates.
- `zkpassport-core` input JSON for circuit 0.18: likely compatible for age and disclose, not verified for dsc, id_data and integrity.
- Linking libc++ statically into one `.so` with bb 4.2 is not verified.
