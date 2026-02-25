#!/usr/bin/env node
/**
 * Genera un JWT de prueba para el Worker (HS256).
 * Uso: JWT_SECRET=tu-secret node scripts/generate-test-token.js [prefix]
 * Ejemplo: JWT_SECRET=mi-secreto node scripts/generate-test-token.js "movies/test/"
 */
import crypto from "crypto";

function base64url(buf) {
  return Buffer.from(buf)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

function signHS256(payload, secret) {
  const signature = crypto.createHmac("sha256", secret).update(payload).digest();
  return base64url(signature);
}

const secret = process.env.JWT_SECRET;
if (!secret) {
  console.error("Define JWT_SECRET (el mismo que configuraste en wrangler secret put JWT_SECRET)");
  process.exit(1);
}

const prefix = process.argv[2] || "movies/test/";
const exp = Math.floor(Date.now() / 1000) + 7200; // 2 horas
const header = { alg: "HS256", typ: "JWT" };
const payload = { exp, prefix };

const headerB64 = base64url(Buffer.from(JSON.stringify(header)));
const payloadB64 = base64url(Buffer.from(JSON.stringify(payload)));
const signingInput = `${headerB64}.${payloadB64}`;
const signatureB64 = signHS256(signingInput, secret);
const token = `${signingInput}.${signatureB64}`;

console.log(token);
console.error("\n(Prefix:", prefix, "| Expira en 2 h)");
