#!/usr/bin/env node
/**
 * Descarga un HLS desde una URL pública y lo deja en test-hls/ como master.m3u8 + segment0.ts, segment1.ts, ...
 * Así no necesitas FFmpeg para tener un HLS de prueba.
 *
 * Uso: node scripts/download-test-hls.js "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
 *       (o cualquier URL de un .m3u8 público)
 */

import https from "https";
import http from "http";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const testHlsDir = path.join(__dirname, "..", "test-hls");

function fetch(url) {
  return new Promise((resolve, reject) => {
    const client = url.startsWith("https") ? https : http;
    client
      .get(url, (res) => {
        if (res.statusCode === 301 || res.statusCode === 302) {
          return fetch(res.headers.location).then(resolve).catch(reject);
        }
        const chunks = [];
        res.on("data", (chunk) => chunks.push(chunk));
        res.on("end", () => resolve(Buffer.concat(chunks)));
        res.on("error", reject);
      })
      .on("error", reject);
  });
}

function resolveUrl(baseUrl, relative) {
  if (relative.startsWith("http://") || relative.startsWith("https://")) {
    return relative;
  }
  const base = baseUrl.replace(/\/[^/]*$/, "/");
  return new URL(relative, base).href;
}

function parseM3u8(text, baseUrl) {
  const lines = text.split(/\r?\n/);
  const segments = [];
  const subPlaylists = [];
  for (let i = 0; i < lines.length; i++) {
    // Media playlist: #EXTINF seguido de URI .ts
    if (lines[i].startsWith("#EXTINF:")) {
      const next = (lines[i + 1] || "").trim();
      if (next && !next.startsWith("#")) {
        if (next.endsWith(".ts")) {
          segments.push(next);
        } else if (next.endsWith(".m3u8")) {
          subPlaylists.push(resolveUrl(baseUrl, next));
        }
      }
      i++;
    }
    // Master playlist: #EXT-X-STREAM-INF seguido de URI .m3u8
    if (lines[i].startsWith("#EXT-X-STREAM-INF:") || lines[i].startsWith("#EXT-X-MEDIA:")) {
      const next = (lines[i + 1] || "").trim();
      if (next && !next.startsWith("#") && next.endsWith(".m3u8")) {
        subPlaylists.push(resolveUrl(baseUrl, next));
      }
      i++;
    }
  }
  return { segments, subPlaylists };
}

async function main() {
  const m3u8Url = process.argv[2];
  if (!m3u8Url || !m3u8Url.includes(".m3u8")) {
    console.error("Uso: node scripts/download-test-hls.js <URL del .m3u8>");
    console.error("Ejemplo: node scripts/download-test-hls.js https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8");
    process.exit(1);
  }

  if (!fs.existsSync(testHlsDir)) {
    fs.mkdirSync(testHlsDir, { recursive: true });
  }

  console.log("Descargando manifest...", m3u8Url);
  let baseUrl = m3u8Url;
  let m3u8Body = await fetch(baseUrl);
  let m3u8Text = m3u8Body.toString();
  let parsed = parseM3u8(m3u8Text, baseUrl);

  // Si es master playlist (enlaza a otros .m3u8), descargar el primero
  if (parsed.segments.length === 0 && parsed.subPlaylists.length > 0) {
    baseUrl = parsed.subPlaylists[0];
    console.log("Es master playlist. Descargando variante...", baseUrl);
    m3u8Body = await fetch(baseUrl);
    m3u8Text = m3u8Body.toString();
    parsed = parseM3u8(m3u8Text, baseUrl);
  }

  if (parsed.segments.length === 0) {
    console.error("No se encontraron segmentos .ts en el manifest.");
    process.exit(1);
  }

  const segmentUrls = parsed.segments;

  console.log("Encontrados", segmentUrls.length, "segmentos. Descargando...");

  const newLines = [];
  let inSegment = false;
  let segmentIndex = 0;

  for (const line of m3u8Text.split(/\r?\n/)) {
    if (line.startsWith("#EXTINF:")) {
      inSegment = true;
      newLines.push(line);
      continue;
    }
    if (inSegment && line && !line.startsWith("#")) {
      const segmentUrl = resolveUrl(baseUrl, line);
      const fileName = `segment${segmentIndex}.ts`;
      const filePath = path.join(testHlsDir, fileName);
      process.stdout.write(`  ${fileName} ... `);
      const data = await fetch(segmentUrl);
      fs.writeFileSync(filePath, data);
      console.log(data.length, "bytes");
      newLines.push(fileName);
      segmentIndex++;
      inSegment = false;
      continue;
    }
    newLines.push(line);
  }

  const newM3u8 = newLines.join("\n");
  const masterPath = path.join(testHlsDir, "master.m3u8");
  fs.writeFileSync(masterPath, newM3u8);

  console.log("Listo. Guardado en", testHlsDir);
  console.log("  master.m3u8");
  for (let i = 0; i < segmentIndex; i++) console.log("  segment" + i + ".ts");
  console.log("\nSiguiente paso: sube a R2 con .\\scripts\\upload-test-hls.ps1");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
