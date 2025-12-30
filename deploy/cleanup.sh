#!/bin/bash
set -e

# Clean up Ripple-IM Kubernetes deployment and infrastructure

# Change to project root directory (parent of deploy/)
cd "$(dirname "$0")/.."

echo "Removing K8s resources..."
kubectl delete namespace ripple

echo "Disconnecting minikube from Docker network..."
docker network disconnect ripple-network minikube

echo "Stopping infrastructure..."
docker compose -f deploy/docker-compose.infra.yml down

echo "Cleanup complete!"
