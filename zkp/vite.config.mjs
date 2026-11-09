import { resolve } from "node:path"

export default {
  root: "web",
  base: "./",
  build: {
    outDir: process.env.UT ? resolve(process.env.UT) : "../dist",
    emptyOutDir: true,
    target: "esnext",
    chunkSizeWarningLimit: 20000,
  },
  worker: { format: "es" },
  optimizeDeps: { exclude: ["@aztec/bb.js", "@noir-lang/noirc_abi", "@noir-lang/acvm_js", "@noir-lang/noir_js"] },
}
