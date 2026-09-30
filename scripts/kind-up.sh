#!/usr/bin/env bash
set -euo pipefail

cluster_name="hotel-reservation"
namespace="hotel-reservation"
db_password="${KIND_DB_PASSWORD:-}"
admin_key="${KIND_ADMIN_KEY:-}"

if [[ -z "$db_password" ]]; then
  if [[ -f .kind-db-password ]]; then db_password="$(<.kind-db-password)"; else db_password="$(openssl rand -hex 24)"; fi
fi
if [[ -z "$admin_key" ]]; then
  if [[ -f .kind-admin-key ]]; then admin_key="$(<.kind-admin-key)"; else admin_key="$(openssl rand -hex 24)"; fi
fi
umask 077
printf '%s\n' "$db_password" > .kind-db-password
printf '%s\n' "$admin_key" > .kind-admin-key

if ! kind get clusters | rg -q "^${cluster_name}$"; then
  kind create cluster --name "$cluster_name" --config k8s/kind-config.yaml --wait 120s
fi
kubectl config use-context "kind-${cluster_name}"
kubectl apply -f k8s/namespace.yaml
kubectl -n "$namespace" create secret generic hotel-secrets \
  --from-literal=postgres-password="$db_password" \
  --from-literal=admin-api-key="$admin_key" \
  --dry-run=client -o yaml | kubectl apply -f -

docker build --build-arg SERVICE=hotel-service -f services/Dockerfile -t hotel-system/hotel-service:local .
docker build --build-arg SERVICE=rate-service -f services/Dockerfile -t hotel-system/rate-service:local .
docker build --build-arg SERVICE=payment-service -f services/Dockerfile -t hotel-system/payment-service:local .
docker build --build-arg SERVICE=reservation-service -f services/Dockerfile -t hotel-system/reservation-service:local .
docker build -f frontend/Dockerfile -t hotel-system/web:local .

kind load docker-image hotel-system/hotel-service:local hotel-system/rate-service:local \
  hotel-system/payment-service:local hotel-system/reservation-service:local hotel-system/web:local \
  --name "$cluster_name"
kubectl apply -n "$namespace" -f k8s/postgres.yaml
kubectl -n "$namespace" rollout status statefulset/postgres --timeout=180s
kubectl apply -n "$namespace" -f k8s/apps.yaml
kubectl -n "$namespace" rollout restart deployment/hotel-service deployment/rate-service \
  deployment/payment-service deployment/reservation-service deployment/hotel-web
kubectl -n "$namespace" rollout status deployment/hotel-service --timeout=180s
kubectl -n "$namespace" rollout status deployment/rate-service --timeout=180s
kubectl -n "$namespace" rollout status deployment/payment-service --timeout=180s
kubectl -n "$namespace" rollout status deployment/reservation-service --timeout=180s
kubectl -n "$namespace" rollout status deployment/hotel-web --timeout=180s

printf 'Hotel reservation is available at http://localhost:8080\n'
printf 'Staff key saved in .kind-admin-key\n'
