#!/usr/bin/env bash
# =============================================================================
# stress.sh - Genera trafico de prestamos hacia prestamos-service.
#
# Uso:
#   bash loadtesting/stress.sh [-u URL] [-n CANTIDAD] [-p LIBRO] [-d SEGUNDOS] [-c SOCIO]
#
# Opciones (valores por defecto):
#   -u  URL base de prestamos-service      (http://localhost:8082)
#   -n  numero de solicitudes de prestamo  (50)
#   -p  id del libro a solicitar           (L001)
#   -d  pausa entre solicitudes en seg     (0.5)
#   -c  nombre del socio solicitante       (socio-stress)
#
# Ejemplos por casuistica:
#   Casuistica 2 (servicio lento): bash loadtesting/stress.sh -n 40 -d 0.5
#   Casuistica 3 (copias agotadas): bash loadtesting/stress.sh -n 15 -p L003 -d 0.5
# =============================================================================
set -u

URL="http://localhost:8082"
N=50
LIBRO="L001"
DELAY="0.5"
SOCIO="socio-stress"

usage() { sed -n '2,19p' "$0"; exit "${1:-0}"; }

while getopts "u:n:p:d:c:h" opt; do
  case "$opt" in
    u) URL="$OPTARG" ;;
    n) N="$OPTARG" ;;
    p) LIBRO="$OPTARG" ;;
    d) DELAY="$OPTARG" ;;
    c) SOCIO="$OPTARG" ;;
    h) usage 0 ;;
    *) usage 1 ;;
  esac
done

command -v curl >/dev/null 2>&1 || { echo "Falta curl"; exit 1; }

BODY="{\"socio\":\"$SOCIO\",\"libroId\":\"$LIBRO\"}"

echo "Enviando $N solicitudes de prestamo a $URL/prestamos (libro=$LIBRO, socio=$SOCIO, pausa=${DELAY}s)"
echo "--------------------------------------------------------------"

ok=0; sin_copias=0; no_disponible=0; otros=0
inicio=$SECONDS

for i in $(seq 1 "$N"); do
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 -X POST "$URL/prestamos" \
          -H 'Content-Type: application/json' -d "$BODY" || true)"
  case "$code" in
    201) ok=$((ok + 1));                       etiqueta="CONFIRMADO" ;;
    409) sin_copias=$((sin_copias + 1));       etiqueta="SIN COPIAS" ;;
    503) no_disponible=$((no_disponible + 1)); etiqueta="LIBROS NO RESPONDE" ;;
    *)   otros=$((otros + 1));                 etiqueta="OTRO ($code)" ;;
  esac
  echo "[$i/$N] HTTP $code  $etiqueta"
  sleep "$DELAY"
done

echo "--------------------------------------------------------------"
echo "Resumen en $((SECONDS - inicio)) s:"
echo "  Confirmados (201)          : $ok"
echo "  Sin copias (409)           : $sin_copias"
echo "  Libros no responde (503)   : $no_disponible"
echo "  Otros / sin conexion       : $otros"
