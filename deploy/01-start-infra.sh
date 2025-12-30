#!/bin/bash
set -e

# Start infrastructure services using Docker Compose

# Change to project root directory (parent of deploy/)
cd "$(dirname "$0")/.."

echo "Starting infrastructure services..."
docker compose -f deploy/docker-compose.infra.yml up -d

echo "Verifying infrastructure is running..."
docker compose -f deploy/docker-compose.infra.yml ps
