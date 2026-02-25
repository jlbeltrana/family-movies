#!/usr/bin/env bash
# Convierte un archivo de vídeo (MP4 o MKV) a HLS para subir a R2.
# Uso: ./convert-to-hls.sh archivo.mp4 [carpeta_salida] [slug]
# Requiere: FFmpeg en el PATH
#
# Ejemplo: ./convert-to-hls.sh ~/Videos/pelicula.mkv
#          ./convert-to-hls.sh pelicula.mp4 ./salida mi-pelicula

set -e
INPUT="$1"
OUTPUT_DIR="${2:-}"
SLUG="${3:-}"

if [ -z "$INPUT" ] || [ ! -f "$INPUT" ]; then
  echo "Uso: $0 <archivo.mp4|archivo.mkv> [carpeta_salida] [slug]"
  exit 1
fi
ext="${INPUT##*.}"
ext="${ext,,}"
case "$ext" in
  mp4|mkv) ;;
  *) echo "Solo se admiten .mp4 y .mkv"; exit 1 ;;
esac

base=$(basename "$INPUT" ".$ext")
base=$(echo "$base" | sed 's/[^a-zA-Z0-9\-_]/-/g' | sed 's/-\+/-/g' | sed 's/^-//;s/-$//')
[ -z "$base" ] && base="video"

[ -z "$SLUG" ] && SLUG="$base"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
[ -z "$OUTPUT_DIR" ] && OUTPUT_DIR="$SCRIPT_DIR/output/$SLUG"
mkdir -p "$OUTPUT_DIR"
MANIFEST="$OUTPUT_DIR/master.m3u8"

echo "Entrada:  $INPUT"
echo "Salida:   $OUTPUT_DIR"
echo "Ruta R2:  movies/$SLUG/master.m3u8"
echo "Convirtiendo..."
ffmpeg -i "$INPUT" -c copy -f hls -hls_time 6 -hls_list_size 0 -hls_segment_filename "$OUTPUT_DIR/segment%03d.ts" "$MANIFEST"
echo "Listo. Archivos en: $OUTPUT_DIR"
echo "Sube el contenido de $OUTPUT_DIR a R2 bajo movies/$SLUG/"
