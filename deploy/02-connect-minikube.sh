#!/bin/bash
set -e

# Connect Minikube to Docker Compose network
# This allows K8s pods to resolve container names (cassandra, kafka, redis, etc.)

echo "Connecting minikube to the Docker Compose network..."
docker network connect ripple-network minikube

echo "Verifying connection..."
docker network inspect ripple-network --format '{{range .Containers}}{{.Name}} {{end}}'
echo ""
echo "Should show: minikube cassandra kafka redis mysql zookeeper minio"
