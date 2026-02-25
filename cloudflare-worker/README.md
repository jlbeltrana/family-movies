# Family Movies — Cloudflare Worker

Worker que sirve vídeo HLS desde R2 solo cuando la petición incluye un JWT válido en `Authorization: Bearer <token>`.

## Requisitos

- Cuenta Cloudflare con R2 y Workers.
- Node.js 18+.

## Configuración

1. Crea el bucket R2 en el dashboard (ej. `family-movies-media`) y deja el nombre en `wrangler.toml` como está (o ajusta `bucket_name`).
2. Configura el secret compartido con el backend:
   ```bash
   npm install
   npx wrangler secret put JWT_SECRET
   ```
3. Despliega:
   ```bash
   npm run deploy
   ```

## Documentación

- Formato del JWT y pruebas: [../docs/phase1-worker-jwt.md](../docs/phase1-worker-jwt.md).

## Comandos

- `npm run dev` — desarrollo local con `wrangler dev` (necesitas R2 remoto; no hay emulación local de R2 en dev).
- `npm run deploy` — desplegar a Cloudflare.
