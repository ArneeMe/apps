import { createServer } from "node:http"
import { readFile } from "node:fs/promises"
import { extname, join } from "node:path"

const typer = { ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript", ".wasm": "application/wasm" }

export function start(rot, port = 0) {
  const server = createServer(async (req, res) => {
    const sti = join(rot, decodeURIComponent(new URL(req.url, "http://x").pathname).replace(/\/$/, "/index.html"))
    try {
      const data = await readFile(sti)
      res.writeHead(200, {
        "Content-Type": typer[extname(sti)] ?? "application/octet-stream",
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Embedder-Policy": "require-corp",
      })
      res.end(data)
    } catch {
      res.writeHead(404)
      res.end()
    }
  })
  return new Promise((ok) => server.listen(port, () => ok(server)))
}
