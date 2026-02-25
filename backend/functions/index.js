import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineString, defineSecret } from "firebase-functions/params";
import { getFirestore } from "firebase-admin/firestore";
import { initializeApp } from "firebase-admin/app";
import jwt from "jsonwebtoken";

initializeApp();
const db = getFirestore();

const workerBaseUrl = defineString("WORKER_BASE_URL", {
  description: "URL base del Worker (ej. https://family-movies-worker.xxx.workers.dev)",
});
const jwtSecret = defineSecret("JWT_SECRET");

/**
 * Devuelve un JWT de reproducción para el Worker R2.
 * Solo usuarios en la allowlist (config/allowedEmails) pueden obtener token.
 * data.movieId: ID del documento en Firestore de la película (debe tener manifestPath).
 */
export const getPlayToken = onCall(
  {
    secrets: [jwtSecret],
    region: "europe-west1",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    }

    const { movieId } = request.data ?? {};
    if (!movieId || typeof movieId !== "string") {
      throw new HttpsError("invalid-argument", "Falta movieId.");
    }

    const email = request.auth.token?.email;
    if (!email) {
      throw new HttpsError(
        "permission-denied",
        "No se pudo verificar tu cuenta. Usa inicio con Google."
      );
    }

    const allowedSnap = await db.doc("config/allowedEmails").get();
    if (!allowedSnap.exists) {
      throw new HttpsError(
        "failed-precondition",
        "Configuración de allowlist no encontrada."
      );
    }
    const emails = allowedSnap.data()?.emails ?? {};
    if (!emails[email]) {
      throw new HttpsError(
        "permission-denied",
        "No tienes permiso para usar esta app."
      );
    }

    const movieSnap = await db.doc(`movies/${movieId}`).get();
    if (!movieSnap.exists) {
      throw new HttpsError("not-found", "Película no encontrada.");
    }
    const manifestPath = movieSnap.data()?.manifestPath;
    if (!manifestPath || typeof manifestPath !== "string") {
      throw new HttpsError(
        "failed-precondition",
        "La película no tiene manifestPath configurado."
      );
    }

    // prefix = directorio en R2 (ej. "movies/mi-pelicula/" para manifestPath "movies/mi-pelicula/master.m3u8")
    const prefix = manifestPath.replace(/\/?[^/]+\.m3u8$/i, "/");
    const secret = jwtSecret.value();
    if (!secret) {
      throw new HttpsError("internal", "JWT_SECRET no configurado.");
    }

    const token = jwt.sign(
      {
        exp: Math.floor(Date.now() / 1000) + 7200,
        prefix,
      },
      secret,
      { algorithm: "HS256" }
    );

    const baseUrl = workerBaseUrl.value();
    if (!baseUrl) {
      throw new HttpsError("internal", "WORKER_BASE_URL no configurado.");
    }

    return { token, baseUrl: baseUrl.replace(/\/$/, "") };
  }
);
