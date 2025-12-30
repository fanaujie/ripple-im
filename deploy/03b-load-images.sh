#!/bin/bash
set -e

# Load existing Docker Compose images into Minikube
# Use this if you've already built images using Docker Compose

# Check if minikube is running
if ! minikube status &>/dev/null; then
    echo "Error: minikube is not running. Please start it first:"
    echo "  minikube start"
    exit 1
fi

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
    echo "Loading $module into Minikube..."
    docker tag $module:latest $module:latest
    minikube image load $module:latest
done

echo "Done! Images are now available inside Minikube."
