#!/usr/bin/env bash
# =============================================================================
# teardown.sh - Elimina los recursos del proyecto biblioteca-monitoreo.
#
# Uso:
#   bash scripts/teardown.sh             # borra apps + monitoreo, conserva Minikube
#   bash scripts/teardown.sh --cluster   # ademas elimina el cluster Minikube perfil biblioteca
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROFILE="biblioteca"
PID_FILE="$ROOT/scripts/.portforward.pids"
DELETE_CLUSTER=false

for arg in "$@"; do
  case "$arg" in
    --cluster|-c) DELETE_CLUSTER=true ;;
    -h|--help)
      sed -n '2,9p' "$0"
      exit 0 ;;
    *)
      echo "Argumento desconocido: $arg"
      echo "Uso: bash scripts/teardown.sh [--cluster]"
      exit 1 ;;
  esac
done

log() { echo -e "\n\033[1;34m==> $*\033[0m"; }
ok()  { echo -e "\033[1;32m[OK]\033[0m $*"; }

# -----------------------------------------------------------------------------
log "Cerrando port-forwards"
if [ -f "$PID_FILE" ]; then
  while read -r pid; do
    [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
  done < "$PID_FILE"
  rm -f "$PID_FILE"
fi
ok "Port-forwards cerrados"

# -----------------------------------------------------------------------------
if command -v kubectl >/dev/null 2>&1 && kubectl cluster-info --context "$PROFILE" >/dev/null 2>&1; then
  log "Eliminando recursos de Kubernetes en contexto $PROFILE"
  kubectl --context "$PROFILE" delete -f k8s/monitoring/alertas.yaml        --ignore-not-found
  kubectl --context "$PROFILE" delete -f k8s/monitoring/servicemonitors.yaml --ignore-not-found
  kubectl --context "$PROFILE" delete configmap biblioteca-dashboard -n monitoring --ignore-not-found
  kubectl --context "$PROFILE" delete -f k8s/app/prestamos.yaml              --ignore-not-found
  kubectl --context "$PROFILE" delete -f k8s/app/libros.yaml                 --ignore-not-found
  kubectl --context "$PROFILE" delete namespace biblioteca                   --ignore-not-found

  if command -v helm >/dev/null 2>&1; then
    helm --kube-context "$PROFILE" uninstall monitoring -n monitoring 2>/dev/null || true
  fi
  kubectl --context "$PROFILE" delete namespace monitoring --ignore-not-found
  ok "Recursos de Kubernetes eliminados"
else
  echo "No hay cluster accesible en el perfil $PROFILE, se omite la limpieza de Kubernetes."
fi

# -----------------------------------------------------------------------------
if [ "$DELETE_CLUSTER" = true ]; then
  log "Eliminando exclusivamente el cluster Minikube perfil '$PROFILE'"
  minikube -p "$PROFILE" delete
  ok "Cluster $PROFILE eliminado"
else
  echo
  echo "El cluster Minikube ($PROFILE) se conserva. Usa --cluster para eliminarlo tambien."
fi
