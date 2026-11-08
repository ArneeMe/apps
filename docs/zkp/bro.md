# ZKPassport SDK <-> mobile app protocol (target: `@zkpassport/sdk` 0.14.2)

Spec for an independent Android (Kotlin) app that stands in for the ZKPassport mobile app when it talks to a website that uses `@zkpassport/sdk@0.14.2`. The target site is arnee.me (`/home/user/arneeme-nett/src/components/zkp/Verifier.tsx`).

## 0. Sources and versions

| Package | Version (from `package-lock.json` in arneeme-nett) | How I read it |
|---|---|---|
| `@zkpassport/sdk` | 0.14.2 (published 2026-05-07) | `npm pack`; original TS recovered from `dist/esm/index.js.map` (`src/index.ts`, `src/public-input-checker.ts`, `src/constants.ts`) |
| `@obsidion/bridge` | **0.11.2** (the SDK depends on `^0.11.2`; 0.12.x does not satisfy that range) | `npm pack`; the package ships `src/` |
| `@zkpassport/utils` | 0.36.0 | `npm pack`; TS recovered from source maps |
| `@zkpassport/registry` | 0.14.0 | `npm pack`; TS recovered from source maps |
| github.com/zkpassport/zkpassport-packages | HEAD 579b631 (2026-11-06, SDK 0.19.3) | `git clone --depth 1`. It is newer than 0.14.2 but has useful references: `packages/e2e/src/headless-app.ts` and `prove.ts` (a headless "mock mobile app"), `packages/bridge/server/bridge.ts` (a relay; its README says it is not the deployed one), and `rust/crates/zkpassport-bridge/src/lib.rs` (a Rust client for the bridge, wire-compatible with obsidion bridge 0.12) |
| github.com/zkpassport/circuits | HEAD 327d19e (0.22.0), history fetched back to 2026-03 | Noir circuits |

Paths below are relative to `/tmp/claude-0/-home-user/3619032b-4690-5e54-b3d9-4f6160dd8a04/scratchpad/`:
- SDK source: `research-sdk/src/src/{index.ts,public-input-checker.ts,constants.ts}`
- Bridge source: `research-sdk/x/obsidion-bridge-0.11.2/package/src/*.ts`
- Utils source: `research-sdk/utils-src/src/**`
- Registry source: `research-sdk/registry-src/src/{client.ts,constants.ts}`
- GitHub clones: `gh/zkpassport_zkpassport-packages`, `gh/zkpassport_circuits`

Labels: **[verified]** means read in the 0.14.2/0.11.2 code. **[newer-repo]** means seen only in the newer GitHub code, so it may differ from 0.14.2. **[unsure]** means inferred.

I could not reach the CDN hosts (`circuits2.zkpassport.id`, `certificates.zkpassport.id`). The sandbox proxy blocks them (HTTP 403). Nothing below comes from live CDN data.

---

## 1. Request URL / QR (`.done()`)

### 1.1 Format [verified]
`ZKPassport._getUrl()` in `src/index.ts` builds the URL. The QR code and the "open app" link use the same string:

```
https://zkpassport.id/r?d=<domain>&t=<topic>&c=<b64 query>&s=<b64 service>&p=<pubkey>&m=<mode>&v=<sdkVersion>&dt=<unix ts>&dev=<0|1>[&nt=<nullifierType>][&oprf_k=<keyId>]
```

| Param | Meaning | Exact derivation |
|---|---|---|
| `d` | Website domain | `normalizeDomain(window.location.hostname)` when the constructor gets no domain. Strips protocol, path, port, query and hash, then lowercases. For arnee.me: `arnee.me`. The scope hash in proofs must be computed from this exact string (see 4.3). |
| `t` | Topic / request id / bridge id | `bridge.connection.getBridgeId()`. This is the website's **compressed secp256k1 public key, lowercase hex, 66 chars** (`bytesToHex(keyPair.publicKey)` in `Bridge.create`). |
| `c` | Query | `Buffer.from(JSON.stringify(query)).toString("base64")`. Standard base64 **with `+`, `/`, `=`, and NOT URL-encoded**. |
| `s` | Service | Base64 of `JSON.stringify({name, logo, purpose, scope, projectID, cloudProverUrl, bridgeUrl})`. `undefined` keys are dropped. Not URL-encoded. |
| `p` | Website pubkey | `bridge.getPublicKey()`, the same value as `t` |
| `m` | Proof mode | `"fast"` (default) \| `"compressed"` \| `"compressed-evm"` |
| `v` | SDK version | `"0.14.2"` (`VERSION` in `src/constants.ts`) |
| `dt` | Timestamp | `floor(Date.now()/1000) - validity`. `validity` defaults to `DEFAULT_VALIDITY = 604800` (7 days). The SDK never reads it back. What the app does with it is [unsure]; probably a lower bound on the proof date. |
| `dev` | Dev mode | `"1"` if `request({devMode:true})`, else `"0"` |
| `nt` | Nullifier type | Only present if `uniqueIdentifierType` was set: `0` NON_SALTED, `1` SALTED. `oprfKeyId` forces 1. SALTED requires `facematch('strict')`. |
| `oprf_k` | OPRF key id | Only present if `oprfKeyId` was set |

**Parsing gotcha:** `c` and `s` are raw base64 inside a query string. A standard decoder (`Uri.getQueryParameter`, `URLSearchParams`) turns `+` into a space. Either parse the raw query manually, or replace `' '` with `'+'` before base64-decoding. The official popup does the same: `gh/.../verify-popup/src/r/request-link.ts` has `serviceParam.replace(/ /g, "+")`.

The mobile app learns the request **only** from this URL. The website never sends the query over the bridge.

### 1.2 Query JSON (`c`) [verified]
Each builder call merges into `topicToConfig[topic][key]`. Key order follows call order.

| Builder call | JSON fragment |
|---|---|
| `.gte('age', 16)` | `{"age":{"gte":16}}` (age must be 1..99) |
| `.gt/.lte/.lt('age', n)` | `{"age":{"gt":n}}` and so on |
| `.range('age', a, b)` | `{"age":{"range":[a,b]}}` |
| `.gte('birthdate', Date)` | `{"birthdate":{"gte":"2000-01-01T00:00:00.000Z"}}` (a Date serializes to an ISO string) |
| `.disclose('nationality')` | `{"nationality":{"disclose":true}}` |
| `.disclose('firstname')`, `.disclose('lastname')` | `{"firstname":{"disclose":true},"lastname":{"disclose":true}}` |
| `.eq('nationality', 'Norway')` | `{"nationality":{"eq":"NOR"}}`. Country names become alpha-3. `"Zero Knowledge Republic"` becomes `"ZKR"`. |
| `.in/.out('nationality', [...])` | `{"nationality":{"in":["NOR","SWE"]}}` (alpha-3) |
| `.bind('user_address', '0x..')` | `{"bind":{"user_address":"0x.."}}` (also `chain` and `custom_data`) |
| `.sanctions()` | `{"sanctions":{"countries":"all","lists":"all","strict":false}}` |
| `.facematch('regular')` | `{"facematch":{"mode":"regular"}}` |

Disclosable keys: `birthdate, expiry_date, nationality, firstname, lastname, fullname, document_number, document_type, issuing_country, gender`.

### 1.3 Concrete example (arnee.me, from Verifier.tsx)
```
https://zkpassport.id/r?d=arnee.me&t=02ab…(66 hex)&c=eyJhZ2UiOnsiZ3RlIjoxNn19&s=eyJuYW1lIjoiYXJuZWUubWUiLCJsb2dvIjoiaHR0cHM6Ly9hcm5lZS5tZS9pY29ucy8xNnBsdXMtMzIuc3ZnIiwicHVycG9zZSI6IkFnZSB2ZXJpZmljYXRpb24gKDE2KykifQ==&p=02ab…&m=fast&v=0.14.2&dt=1762000000&dev=1
```
- `c` decodes to `{"age":{"gte":16}}`
- `s` decodes to `{"name":"arnee.me","logo":"https://arnee.me/icons/16plus-32.svg","purpose":"Age verification (16+)"}`
- The site creates **two** requests: one with `dev=0` (the "Ekte pass" QR) and one with `dev=1` (the "Dev / test" QR). Each has its own key pair and bridge.

Other examples:
- `{"age":{"gte":18},"nationality":{"disclose":true},"firstname":{"disclose":true},"lastname":{"disclose":true}}` gives `eyJhZ2UiOnsiZ3RlIjoxOH0sIm5hdGlvbmFsaXR5Ijp7ImRpc2Nsb3NlIjp0cnVlfSwiZmlyc3RuYW1lIjp7ImRpc2Nsb3NlIjp0cnVlfSwibGFzdG5hbWUiOnsiZGlzY2xvc2UiOnRydWV9fQ==`
- `{"nationality":{"eq":"NOR"}}` gives `eyJuYXRpb25hbGl0eSI6eyJlcSI6Ik5PUiJ9fQ==`

### 1.4 devMode [verified]
`dev=1` only tells the app it is a test request. On the SDK side, `devMode`:
1. Points every registry lookup at **Sepolia (chainId 11155111)** instead of Ethereum mainnet (chainId 1).
2. Accepts mock nullifier types 2 and 3. These come from passports with issuing country `ZKR`.

See section 4.

### 1.5 Deep linking on Android
- `https://zkpassport.id/.well-known/assetlinks.json` (`gh/.../verify-popup/public/.well-known/assetlinks.json`) verifies App Links only for package `app.zkpassport.zkpassport` with signing cert `8A:85:67:2C:…:AF:02`. An independent app cannot become the verified handler. Options:
  - Scan the QR in-app (simplest).
  - Declare an unverified `https://zkpassport.id/r` intent filter. The user must then enable it manually under "Open by default".
- **[newer-repo]** The newer zkpassport.id/r page opens the app via the custom scheme `zkpassport:///r?<same query>` (`gh/.../zkpassport-ui/src/app-link.ts`). Any app can register that scheme. If the official app is installed too, Android shows a chooser.

---

## 2. Bridge (`@obsidion/bridge` 0.11.2)

### 2.1 Roles and endpoints [verified]
- The website is the **creator**. It connects to `wss://bridge.zkpassport.id?id=<creatorPubHex>&v=1` (`DEFAULT_WS_ENDPOINT`, `PROTOCOL_VERSION="1"`, `_getWsConnectionUrl()` in `bridge-connection.ts`). `bridgeUrl` in the service JSON (`s`) can override the endpoint. If `s.bridgeUrl` is present, use it.
- The app is the **joiner**. It connects to the same room: `wss://bridge.zkpassport.id?id=<creatorPubHex>&v=1&moc=<urlencoded base64 handshake>`.
- The relay is dumb. It groups connections by `id` and relays every JSON frame to the other peers in the room. It stamps `origin` (the sender's HTTP `Origin` header, or `"none"`) onto every relayed frame, caches frames that lack `nocache:true` (7-day TTL) for replay, and decodes and relays `moc` as if the client had sent it on connect.
  - This behaviour comes from `gh/.../packages/bridge/server/bridge.ts` **[newer-repo]**. That README says it is not the deployed server, but it implements the same wire protocol.
- **[newer-repo]** Optional `&ooc`: the server sends `{"jsonrpc":"2.0","method":"ooc","params":{"origin":"https://arnee.me"}}` on connect. The Rust client appends `&ooc`. Whether the production server supports it is [unsure]. Extra params should be harmless.
- OkHttp sends no `Origin` header by default. The website does not check the joiner's origin, so this is fine.

### 2.2 Keys and crypto [verified: `encryption.ts`, `crypto.ts`, `json-rpc.ts`]
- **Curve:** secp256k1 (`@noble/secp256k1` v2). Public keys are **33-byte compressed, lowercase hex**.
- **ECDH:** `getSharedSecret(priv, remotePub)` returns noble's *compressed shared point* (33 bytes: `02|03 || X`). The code then takes `.slice(0, 32)`.
  - **AES key = parity byte || first 31 bytes of X.** There is no KDF and no hashing.
  - Java's `KeyAgreement("ECDH")` returns only X, so compute the point yourself (BouncyCastle `ECPoint.multiply(...).normalize().getEncoded(true)`) and take bytes [0..32).
- **Cipher:** AES-256-GCM with a 128-bit tag. The output is `ciphertext || tag`, with no AAD.
- **Nonce:** `sha256(utf8(bridgeId))[0..12]`, where `bridgeId` = the creator pubkey hex string (the `t`/`p` param). **The same key and nonce are used for every message in both directions** (a nonce-reuse weakness, but it is the protocol).
  - In Java, create a new `Cipher` for every message. Re-initialising one `Cipher` with the same key and IV for encryption throws.
- Test vector, from `rust/crates/zkpassport-bridge/src/lib.rs` (the comment says it was generated with the TS code; I did not run it):
  - creatorPub = `0256b328b30c8bf5839e24058747879408bdb36241dc9c2e7c619faa12b2920967`
  - joiner priv = 32×`0x07`
  - AES key = `03f989420656df216794c7c591c40c2f9b7fb249e9ead3452217d9ec93217f94`
  - AES-GCM("hello") = `72db6df8651ea177554399a55f9294837d6680b1de`
  - nonce = sha256(bridgeId)[0..12] = `3fcbd1225cefafef96e2821d` (computed by me)

### 2.3 Handshake [verified]
1. The app generates a fresh secp256k1 key pair, computes the key from `p`, and computes `greeting = hex(AESGCM("hello"))` (42 hex chars).
2. Handshake frame (plain JSON, not encrypted):
   ```json
   {"jsonrpc":"2.0","id":"<32 hex random>","method":"handshake","params":{"pubkey":"<joiner compressed pub hex>","greeting":"<hex>"}}
   ```
   Send it as `moc` = `encodeURIComponent(base64(JSON))` in the connect URL (what `Bridge.join` does). Sending it as a normal frame after `onopen` should also work, because the creator handles `method:"handshake"` from any peer frame.
3. The creator (website) runs `handleHandshake`. It computes the shared secret, decrypts the greeting with nonce = sha256(its own pubkey hex), and checks it equals `"hello"`. Then:
   - It replies with an encrypted `hello`: an `encryptedMessage` whose inner plaintext is `{"method":"hello","params":{}}`. In 0.11.2 the params are `null`, so the message is **not** chunked or compressed.
   - It fires `onSecureChannelEstablished`, which the SDK turns into `onRequestReceived`.
4. The app decrypts the reply and checks `inner.method == "hello"`. The channel is then established. Check `frame.origin` (stamped by the server) against `"https://" + d`. The JS joiner ignores encrypted frames whose origin does not match (`parseOriginHeader`, `handleWebSocketMessage`).
5. **Only handshake once.** Once established, the creator rejects any further handshake: `this.remotePublicKey !== joinerPublicKey` compares Uint8Arrays by reference, so it is always true. The creator then sends `{"method":"error","params":{"message":"Secure channel already established, ignoring handshake"}}`. On reconnect, connect **without** `moc`.

### 2.4 Frame format [verified: `json-rpc.ts`]
Every frame the app sends (except handshake, ping and pong) is an outer frame:
```json
{"jsonrpc":"2.0","id":"<32 hex chars, random, unique>","method":"encryptedMessage","params":{"payload":"<base64(AESGCM(innerJsonUtf8))>"}}
```
- `id` is required. Peers drop frames with no id or a duplicate id (`seenMessageIds`).
- **Inner plaintext when params is non-null** (`getEncryptedJsonPayload`). This is the normal path. Even `{}` is non-null, so `sendMessage("accept")` takes it.
  1. `compressed = base64(zlibDeflate(utf8(JSON.stringify(params))))`. pako `deflate` produces **zlib (RFC 1950, header 78 9C)**, not raw deflate and not gzip. Java's `Deflater()` default is correct.
  2. Split `compressed` into 16384-char slices (`CHUNK_SIZE = 16*1024`). Generate one chunk id (32 hex chars).
  3. For each slice `i`, the inner plaintext is:
     ```json
     {"method":"<method>","params":"<slice i>","chunk":{"id":"<chunkId>","index":i,"length":N}}
     ```
     Encrypt it and wrap it in its own outer frame with a fresh outer `id`.
  4. An outer frame larger than 32768 chars is refused by the JS sender (`MAX_PAYLOAD_SIZE`, the AWS API Gateway limit). 16384 base64 chars encrypt to about 22 KB of payload base64, so this fits. The JS sender waits 1 ms between chunks.
- **Inner plaintext when params is null** (only `hello` in practice): `{"method":"<method>","params":{}}`.
- **Receiver logic** (website side, `handleEncryptedMessage`):
  - If there is no `chunk`, or `chunk.length == 1`: if `params` is a non-empty string, it is base64-decoded and inflated. Otherwise it is used as is.
  - If `chunk.length > 1`: chunks are collected by `chunk.id`, joined in index order, then base64-decoded, inflated and JSON-parsed. The result is `{method, params}`.
  - The inflate error check compares to the string `"incorrect header check"`. Any other inflate error throws, so use zlib format.
- **Keep-alive:** the creator sends `{"method":"ping","params":{},"nocache":true}` every 30 s. These are plain frames relayed through the bridge. Answer with `{"method":"pong","params":{},"nocache":true}`. A peer also answers your pings.
- **Reconnect:** after reconnecting, a client may send `{"method":"replay","params":{"timestamp":<ms>}}`. The server resends cached frames, then `{"status":"replay_complete","count":N}` [newer-repo server]. The website does this automatically using its last-message time minus 1 s.

### 2.5 Application messages the app must send [verified: `handleEncryptedMessage` in `src/index.ts`]
Each one is `sendMessage(method, params)` with the framing from 2.4. The inner JSON after reassembly is shown.

| method | params | SDK effect |
|---|---|---|
| `accept` | `{}` | User accepted. Fires `onGeneratingProof`. Optional. |
| `reject` | `{}` | Fires `onReject` (Verifier.tsx shows "Forespørselen ble avslått." for the dev request) |
| `proof` | `ProofResult` (section 3) | Pushes to the proof list, fires `onProofGenerated`. Runs `handleResult` once a `done` exists and `proofs.length + failedCount === params.total`. |
| `done` | `QueryResult` (section 3.4) | Stores the result (dates converted with `formatQueryResultDates`). Runs `handleResult` once `proofs.length > 0` and `proofs.length + failed === proofs[0].total`. |
| `error` | `{"error":"<string>"}` | Fires `onError(error)`. Special strings: `"This ID is not supported yet"` triggers `handleResult` if `done` already arrived (proofs empty, so `verified:false`). Any string starting with `"Cannot generate proof"` increments the failed count (the final result is then `verified:false`). The headless app also sends `"Sanctions check failed"`. |

Order used by the official app [newer-repo e2e headless app, comment "The app sends all proofs after proving (AccessRequestView.tsx), then done"]:
```
connect(moc handshake) → ← hello → accept → proof(index 0..N-1, total N) ×N → done(QueryResult)
```
The SDK tolerates `done` before the proofs. `total` must be identical on every proof. `index` is not used by 0.14.2. Never send `done` with zero proofs (except after the "not supported" error): the request then never resolves.

---

## 3. Proof payloads

### 3.1 `ProofResult` [verified: `utils-src/src/types/index.ts`]
```ts
{ proof: string, vkeyHash: string, version: "x.y.z", name: string,
  committedInputs?: { [circuitName]: CommittedInputs }, index: number, total: number }
```
- `proof`: **hex, no `0x`**. It is all public inputs, each left-padded to 32 bytes, concatenated, followed by the raw UltraHonk proof bytes:
  `publicInputs.map(x => x.replace(/^0x/,"").padStart(64,"0")).join("") + hex(proofBytes)` (`proveCircuit` in `gh/.../e2e/src/prove.ts`).
  - The SDK splits it with `getProofData(hex, getNumberOfPublicInputs(name))`: 32-byte fields, the first *k* are public inputs, the rest is the proof.
- `name`: exact circuit name from the circuit manifest, e.g. `sig_check_dsc_tbs_700_rsa_pkcs_4096_sha256`, `sig_check_id_data_tbs_700_rsa_pkcs_2048_sha256`, `data_check_integrity_sa_sha256_dg_sha256`, `compare_age`, `disclose_bytes`. The SDK looks the vkey up **by this name**.
- `version`: circuit manifest version. The SDK uses `proofs[0].version` to fetch the manifest.
- `vkeyHash`: `packagedCircuit.vkey_hash`. Not checked by 0.14.2's verify, but send it.
- `committedInputs`: only on disclosure proofs, keyed by circuit name.

Public-input counts (`getNumberOfPublicInputs`, utils 0.36.0):

| Circuit | # | Layout |
|---|---|---|
| `sig_check_dsc_*` | 2 | [0] certificate registry merkle root, [1] comm_out |
| `sig_check_id_data_*` | 2 | [0] comm_in, [1] comm_out |
| `data_check_integrity_*` | 2 | [0] comm_in, [1] comm_out |
| disclosure circuits (`compare_age`, `disclose_bytes`, `inclusion_check_*`, `exclusion_check_*`, `compare_birthdate`, `compare_expiry`, `bind`, `exclusion_check_sanctions`, `facematch`) | 8 | [0] comm_in, [1] current_date (unix seconds), [2] service_scope, [3] service_subscope, [4] param_commitment, [5] nullifier_type, [6] nullifier, [7] oprf_pk_hash |
| `outer_count_N` / `outer_evm_count_N` | 8 + (N-3) | compressed mode only |

The layout matches `src/noir/bin/compare/age/*/src/main.nr`: public params in declaration order (`comm_in`, `current_date`, `service_scope`, `service_subscope`), then the return tuple `(param_commitment, nullifier_type, nullifier, oprf_pk_hash)`.

### 3.2 Proofs per request (fast mode) [verified via checker + circuit-matcher]
There are always 3 base proofs (DSC to CSCA, SOD signature by DSC, DG integrity), plus one proof per disclosure circuit:
- `.gte('age',16)` only: **4 proofs**: `sig_check_dsc_*`, `sig_check_id_data_*`, `data_check_integrity_*`, `compare_age`
- `disclose nationality`, `disclose firstname/lastname/fullname`, and **`eq` on any of these** all go into **one** `disclose_bytes` proof. `getDiscloseCircuitInputs` sets mask bits for every field with `disclose` or `eq`.
  - Age + nationality + names: **5 proofs** (3 base + `compare_age` + `disclose_bytes`). The e2e test checks exactly `[dsc, id_data, integrity, compare_age, disclose_bytes]` for `gte age 18 + disclose nationality`.
  - **Note:** `eq` reveals the masked MRZ bytes to the verifier. It is not private.
- `in` uses `inclusion_check_nationality` (or `_issuing_country`). `out` uses `exclusion_check_*`. Birthdate and expiry ranges use `compare_birthdate` / `compare_expiry`. Other circuits: `bind`, `exclusion_check_sanctions`, `facematch`.
- Order on the wire does not matter. The SDK sorts by name prefix in this order: `sig_check_dsc, sig_check_id_data, data_check_integrity, disclose_bytes, compare_age, compare_birthdate, compare_expiry, exclusion_check_nationality, inclusion_check_nationality, exclusion_check_issuing_country, inclusion_check_issuing_country, bind, exclusion_check_sanctions, facematch`. Send them in that order anyway.
- Compressed modes (`m=compressed|compressed-evm`): a single `outer_count_<3+n>` proof, `index:0,total:1`, with all subproofs' `committedInputs` merged. The official app uses a cloud prover for this (`cloudProverUrl`). The site uses `fast`.

### 3.3 committedInputs examples [verified: checker + e2e]
```json
// compare_age, for gte 16 (maxAge 0 means "no upper bound"; gt n means minAge n+1; lt n means maxAge n-1)
{"compare_age":{"minAge":16,"maxAge":0}}
// disclose_bytes: both arrays have length 90 (MRZ without the 5-byte DG1 header)
{"disclose_bytes":{"discloseMask":[0,0,…1,1,1,…],"disclosedBytes":[…]}}
//   disclosedBytes[i] = dg1[5+i] * discloseMask[i]
// inclusion/exclusion
{"inclusion_check_nationality":{"countries":["NOR","SWE"]}}
```

Full age proof message example (shape only):
```json
{"proof":"<64-hex comm_in><64-hex current_date><64-hex scope><64-hex subscope><64-hex param_commitment><64-hex nullifier_type><64-hex nullifier><64-hex oprf_pk_hash><proof bytes hex…>",
 "vkeyHash":"0x…","version":"0.18.0","name":"compare_age",
 "committedInputs":{"compare_age":{"minAge":16,"maxAge":0}},"index":3,"total":4}
```

MRZ disclose ranges (TD3 passport / TD1 ID card), from `DisclosedData.fromDisclosedBytes`:

| Field | Passport | ID card |
|---|---|---|
| doc type | [0,2) | [0,2) |
| issuing country | [2,5) | [2,5) |
| name | [5,44) | [60,90) |
| doc number | [44,53) | [5,14) |
| nationality | [54,57) | [45,48) |
| birthdate | [57,63) | [30,36) |
| gender | [64,65) | [37,38) |
| expiry | [65,71) | [38,44) |

First and last name use the sub-ranges split by `<<` (`getFirstNameRange` / `getLastNameRange` in `utils-src/src/passport/getters.ts`).

### 3.4 `QueryResult` sent with `done` [verified: checker]
Mirror the query. `expected` must equal the original query value exactly (the checker compares it with the original query).
```json
{"age":{"gte":{"expected":16,"result":true}}}
{"nationality":{"disclose":{"result":"NOR"}}}
{"nationality":{"eq":{"expected":"NOR","result":true}}}
{"firstname":{"disclose":{"result":"JOHN MILLER"}},"lastname":{"disclose":{"result":"SMITH"}}}
{"birthdate":{"disclose":{"result":"1995-11-12T00:00:00.000Z"}}}
```
- Names are compared with `formatName(x).toLowerCase()`: `<` becomes a space, diacritics are folded, non-letters are removed.
- Nationality is compared to the raw 3 MRZ bytes, so Germany would be `D<<`. What the official app reports for Germany is [unsure].

### 3.5 Building the proofs (what the app must actually do)
Use ZKPassport's **own published circuits**. The SDK fetches the vkey by `name` from their manifest, so you cannot use your own circuits.

Per circuit, the steps are:
1. Fetch the manifest: `https://circuits2.zkpassport.id/{mainnet|testnet}/by-version/<ver>/manifest.json`.
2. Fetch the packaged circuit: `https://circuits2.zkpassport.id/{net}/by-hash/<vkeyhash>.json`. It contains `bytecode` (ACIR), `abi`, `vkey`, `vkey_hash` and `bb_version`.
3. Generate the witness with an ACVM. Build inputs exactly like `@zkpassport/utils` `circuit-matcher.ts`: `getDSCCircuitInputs`, `getIDDataCircuitInputs`, `getIntegrityCheckCircuitInputs`, `getAgeCircuitInputs`, `getDiscloseCircuitInputs`.
4. Prove with **Barretenberg UltraHonk of the matching bb version**.

Inputs and certificates:
- Certificates (CSCA list and merkle tree): `https://certificates.zkpassport.id/{mainnet|testnet}/<root>.json`. Get the root with `latestRoot(bytes32)` on the RootRegistry (section 4.2).
- Salts follow the mobile-app convention (copied in e2e `prove.ts`): `dg1Salt = privateNullifierSalt = salt` and `dg2HashSalt = expiryDateSalt = sha256(saltBytes)`.
- `service_scope = getScopeHash(d)` and `service_subscope = getScopeHash(s.scope)` (0 if no scope).
- `current_date = floor(now/1000)`.
- `nullifier_secret = 0` and `oprf_proof = zero` for non-salted mode.

Reference code you can port:
- `gh/zkpassport_zkpassport-packages/rust/crates/zkpassport-core`: a Rust port of the base and age input generation for the iOS App Clip and an Android JNI binding. Its README says base and age inputs are differential-tested against the TS.
- `rust/crates/zkpassport-bridge`: the bridge protocol in Rust.

Proof-system version compatibility is the main pitfall:
- SDK 0.14.2 verifies with `@aztec/bb.js 4.2.0-aztecnr-rc.2` (`UltraHonkVerifierBackend`, default non-EVM proof).
- The circuits repo used exactly that bb.js for circuit versions **0.18.0 (May 2026) and the "version 19 manifest" (June 2026)**. It moved to bb 5.0.0 at 0.20.0 (2026-07-21) and to bb 6.0.0 at 0.22.0.
- 0.20.0 also added nullifier type `4` (NONE). utils 0.36.0's `getNullifierTypeFromDisclosureProof` throws on 4.
- **So prove with circuit version 0.18.x or 0.19.x and bb 4.2.0-aztecnr-rc.2 (Noir 1.0.0-beta.20).** [unsure: inferred from git history; I could not list the CDN versions.]
- The newer SDK chooses bb v5 for circuits < 0.22.0 and v6 for 0.22.0+ (`bb-verifier.ts`). This shows the official app sends whatever circuit version it chooses, and verifiers must match it.

---

## 4. How the SDK verifies (`handleResult`, then `verify()`, then the `onResult` callback)

`handleResult` calls `verify({proofs, originalQuery, queryResult, validity, scope, devMode, oprfKeyId})`. If any `Cannot generate proof` error arrived, it forces `verified:false`. Everything runs **in the browser**. There is no ZKPassport server call apart from the CDN and RPC fetches below.

### 4.1 Public-input checks (`PublicInputChecker.checkPublicInputs`)
Fast mode, proofs sorted as in 3.2:
1. `sig_check_dsc`:
   - `commitmentOut = pi[1]`.
   - The certificate root `pi[0]` must satisfy `RegistryClient.isCertificateRootValid(root)` **on-chain** (4.2).
2. `sig_check_id_data`: `pi[0]` must equal the previous commitmentOut. Then `commitmentOut = pi[1]`.
3. `data_check_integrity`: `pi[0]` must equal commitmentOut. Then `commitmentOut = pi[1]`.
4. Each disclosure proof:
   - `pi[0]` must equal the integrity comm_out.
   - `pi[4]` must equal the parameter commitment recomputed from `committedInputs`. For age: `poseidon2([1, 2, minAge, maxAge])`. For disclose: `poseidon2([0, 4, packBits(mask), ...packBytes(disclosedBytes,31)])`.
   - Scope:
     - `pi[2]` must equal `getScopeHash(this.domain)` = big-endian integer of `sha256(utf8(domain))[0..31]`. For `arnee.me` that is `0x2706de002e287f099517b8abe9c7f08d0e057cda313ed95662400c86fdc6a8`.
     - If `scope` was set, `pi[3]` must equal `getScopeHash(scope)`.
   - Date: with `today` = local midnight, `today - pi[1]*1000` must be `< validity*1000` (7 days). A current_date in the future is not rejected.
   - The `committedInputs` and `queryResult` must agree:
     - age: `minAge === age.gte.expected`, and `maxAge` must be 0 unless lt, lte, eq or range was requested.
     - disclose: the decoded bytes must match each `disclose.result` and `eq.expected`.
     - Each `expected` must equal the value in `originalQuery`.
     - A `disclose` that was not in `originalQuery` is rejected.
   - `uniqueIdentifier = pi[6]` and `uniqueIdentifierType = pi[5]` (from the last disclosure proof processed).
5. If the nullifier type is SALTED (1) or SALTED_MOCK (3), `pi[7]` must equal the hash of the OPRF public key fetched for `oprfKeyId`.
6. Outer (compressed) proofs:
   - Certificate root valid on-chain, **circuit registry root** valid on-chain, and the date checked.
   - The number of `committedInputs` keys must equal the number of param commitments.
   - Scope and subscope checked.
   - Each committed input's commitment must appear in the outer proof's param commitments.

Mock rejection (`verify`): if the nullifier type is `NON_SALTED_MOCK (2)` or `SALTED_MOCK (3)` and `!devMode`, the result is `verified=false`. The circuits set the mock types whenever the issuing country bytes are `ZKR` (`src/noir/lib/commitment/scoped-nullifier/src/lib.nr`).

### 4.2 Registries, RPC, vkeys [verified: `registry-src/src/client.ts`, `constants.ts`]
- chainId is `devMode ? 11155111 (Sepolia) : 1 (Ethereum mainnet)`.
- RPC URLs (hard-coded Alchemy key):
  - mainnet: `https://eth-mainnet.g.alchemy.com/v2/in6UjcATST36yyKuk83yb1yukKs65u8G`
  - Sepolia: `https://eth-sepolia.g.alchemy.com/v2/in6UjcATST36yyKuk83yb1yukKs65u8G`
- RootRegistry contract (both chains): `0x1D0000020038d6E40E1d98e09fA1bb3A7DAA8B70`.
- Root check: `eth_call isRootValid(bytes32 registryId, bytes32 root, uint256 ts)`. The selector is `0x2aae4296`. `registryId` is `1` for certificates and `2` for circuits. `ts` = now. A return value of 1 means valid.
  - Latest root: `latestRoot(bytes32)` with selector `0xc3bc16e8`.
  - The certificate root in the DSC proof must therefore be a root the registry considers valid *now*. Use the latest packaged certificates.
- Verification keys:
  - **Not bundled.** The manifest is fetched from `https://circuits2.zkpassport.id/{mainnet|testnet}/by-version/<proofs[0].version>/manifest.json`.
  - The manifest is validated only *internally*: the root recomputed from the circuit hashes must equal `manifest.root`. In fast mode it is **not** checked against the chain.
  - Each circuit is fetched from `https://circuits2.zkpassport.id/{net}/by-hash/<manifest.circuits[name].hash>.json`. The SDK checks `poseidon2(vkey fields) == hash`, skipped for `outer_evm_*`.
- Proof check: `UltraHonkVerifierBackend.verifyProof({proof, publicInputs, verificationKey})` from bb.js 4.2.0-aztecnr-rc.2.
- `outer_evm_*` proofs are verified by `eth_call` to the ZKPassportVerifier contract (`getSolidityVerifierDetails()`) instead.

### 4.3 devMode summary
| | `dev=0` | `dev=1` |
|---|---|---|
| Certificate and circuit registry, CDN | Ethereum mainnet, `/mainnet` | Sepolia, `/testnet` |
| Mock (ZKR) nullifier types 2 and 3 | rejected | accepted |
| Other checks | same | same (scope, date, commitments and bb verification still run) |

- The Sepolia certificate registry contains the **ZKR mock CSCAs**. The e2e test verifies a `createMockPassport()` ZKR passport against "Sepolia's registries" with `devMode:true`.
- The **mock DSC private keys are public**: `gh/.../zkpassport-utils/src/mock-passports/dscs.json`, for rsa-2048/3072/4096, rsa-pss-4096 and ecdsa-p256/384/521.
- So in dev mode anyone can create a passport with any birthdate or name (issuing country ZKR) that passes verification.
- I have not checked whether testnet roots contain real CSCAs as well [unsure]. Any proof whose issuing country is not ZKR gets a non-mock nullifier and would also be accepted in devMode if the Sepolia root covers its CSCA.

---

## 5. Blockers and things that would stop an independent app

**Not blockers in 0.14.2** [verified]:
- No app attestation, signed app identity or API key in the bridge or the fast-mode path.
- The bridge has no authentication.
- The SDK makes no server-side call to ZKPassport other than the public CDN, the IPFS gateway and the Alchemy RPC. The newer SDK's `dashboard-api.zkpassport.id` lookup is absent from 0.14.2: zero occurrences in `dist`.

**Hard blockers, only for specific query types:**
- **`.facematch()`.** The proof must commit to an Apple App Attest or Google key-attestation root and an app id hash. The SDK hard-codes:
  - `appIdHash` ∈ {`0x1fa7…179b` (iOS `YL5MS3Z639.app.zkpassport.zkpassport`), `0x24d9…615d` (Android `app.zkpassport.zkpassport`)}
  - `environment == "production"`
  - root key hash ∈ Apple/Google roots (`src/constants.ts`)

  Another app's attestation would carry a different package name or signing cert. Facematch is therefore **impossible** for an independent app.
- **Salted nullifiers (`nt=1` / `oprfKeyId`).** These require strict facematch plus an OPRF evaluation from `oprf.zkpassport.id` (module `face-match`, authenticated). Effectively blocked.

**Practical hurdles (not cryptographic gates):**
- You must reproduce ZKPassport's circuit inputs bit-exactly and prove their Noir circuits with the matching Barretenberg version on Android:
  - ACVM witness generation plus native bb (C++) for arm64.
  - Large circuits (RSA-4096 DSC checks) need a lot of memory and time.
  - The official app uses native provers; compressed mode uses a cloud prover.
- Circuit version and bb version must match the website's SDK (section 3.5). A site upgrading to a newer SDK changes the verifier. Read `v` from the URL and choose a circuit version accordingly.
- The certificate root must be currently valid on-chain, so refresh the certificate package.
- Your passport's CSCA must be in ZKPassport's registry, and the matching `sig_check_dsc_tbs_<len>_<alg>_<size>_<hash>` circuit must exist.
- App Links for zkpassport.id belong to the official package (1.5).

---

## 6. Observations about the arnee.me integration (from code reading, not tested)
1. **Dev QR equals a bypass.** `onResultDev` → `handleVerified(verified)` shows "Verifisert · over 16". With the public ZKR DSC keys, anyone with a script can pass the dev check with an invented birthdate. The page explains the dev QR as a test mode, but it reaches the same success state.
2. **The SDK does not check that every queried condition has a proof.**
   - `checkPublicInputs` only checks age when a `compare_age` proof is present. Nothing compares the set of proof names with `originalQuery`.
   - A sender could probably send only the 3 base proofs plus `done` with `{"age":{"gte":{"expected":16,"result":true}}}` and get `verified:true`. A real passport of any age would do in `dev=0`.
   - In that case `uniqueIdentifier` would be `undefined`, because only disclosure proofs set it.
   - Verifier.tsx also ignores `result.age.gte.result` and `uniqueIdentifier`.
   - Mitigation: collect the names via `onProofGenerated` and require a `compare_age` proof, `uniqueIdentifier !== undefined`, and `result.age?.gte?.result === true && result.age.gte.expected === 16`. Better: re-run `verify()` server-side with `createQuery()` and those checks.
   - [unsure: traced statically, not executed]
3. Verification is only client-side, so the "verified" state is purely cosmetic for the server.
