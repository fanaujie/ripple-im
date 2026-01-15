#!/bin/bash
set -e

# Build images directly inside Minikube's Docker environment
# This avoids image transfer overhead

# Change to project root directory (parent of deploy/)
cd "$(dirname "$0")/.."

# Check if minikube is running
if ! minikube status &>/dev/null; then
    echo "Error: minikube is not running. Please start it first:"
    echo "  minikube start"
    exit 1
fi

PROFILE=${1:-cassandra}
echo "Building images with profile: $PROFILE"

echo "Switching to Minikube's Docker daemon..."
eval $(minikube docker-env)

echo "Verifying Docker context..."
docker info | grep -i "Name:"
# Should output: Name: minikube

service_modules=(
    ripple-api-gateway
    ripple-authorization-server
    ripple-upload-gateway
    ripple-message-gateway
    ripple-snowflakeid-server
    ripple-user-presence-server
    ripple-message-api-server
    ripple-message-dispatcher
    ripple-push-server
    ripple-async-storage-updater
)

for module in "${service_modules[@]}"; do
    echo "Building $module..."
    docker build -t $module:latest -f $module/Dockerfile --build-arg PROFILE=$PROFILE .
done

echo "Listing built images..."
docker images | grep ripple

echo "Restoring Docker context..."
eval $(minikube docker-env -u)

echo "Done! Images are now available inside Minikube."
