import jwt from "@tsndr/cloudflare-worker-jwt";

/**
 * Worker que sirve contenido HLS desde R2 solo si la petición incluye
 * un JWT válido en Authorization: Bearer <token>.
 * El JWT debe contener: exp (expiración) y prefix (path permitido en R2, ej. "movies/mi-pelicula/").
 */
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/^\//, ""); // clave en R2 sin barra inicial

    if (!path) {
      return new Response("Not Found", { status: 404 });
    }

    const auth = request.headers.get("Authorization");
    if (!auth || !auth.startsWith("Bearer ")) {
      return new Response("Unauthorized", { status: 401, headers: { "WWW-Authenticate": "Bearer" } });
    }

    const token = auth.slice(7);
    const secret = env.JWT_SECRET;
    if (!secret) {
      return new Response("Server misconfiguration", { status: 500 });
    }

    let payload;
    try {
      const valid = await jwt.verify(token, secret);
      if (!valid) {
        return new Response("Invalid or expired token", { status: 401 });
      }
      payload = jwt.decode(token);
    } catch (e) {
      return new Response("Invalid token", { status: 401 });
    }

    // Si el JWT tiene "prefix", el path solicitado debe empezar por ese prefix (ej. "movies/mi-pelicula/")
    const prefix = payload.prefix;
    if (prefix) {
      const normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
      if (!path.startsWith(normalizedPrefix) && path !== prefix.replace(/\/$/, "")) {
        return new Response("Forbidden", { status: 403 });
      }
    }

    const object = await env.MEDIA.get(path);
    if (object === null) {
      return new Response("Not Found", { status: 404 });
    }

    const contentType = getContentType(path);
    const headers = new Headers();
    headers.set("Content-Type", contentType);
    headers.set("Cache-Control", "public, max-age=3600");
    if (object.etag) {
      headers.set("ETag", object.etag);
    }

    return new Response(object.body, { status: 200, headers });
  },
};

function getContentType(path) {
  if (path.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
  if (path.endsWith(".ts")) return "video/MP2T";
  if (path.endsWith(".mp4")) return "video/mp4";
  return "application/octet-stream";
}
