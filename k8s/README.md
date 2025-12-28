# Ripple-IM Kubernetes Manifests

This directory contains Kubernetes manifests for deploying Ripple-IM services.

## Architecture

This setup uses a **hybrid deployment**:

- **Infrastructure** (Cassandra, Kafka, Redis, MySQL, Zookeeper, MinIO): Docker Compose
- **Application Services**: Kubernetes (Minikube)

Minikube connects to the Docker Compose network to access infrastructure services by container name.

## Prerequisites

- Docker & Docker Compose
- Minikube
- kubectl

## Quick Start (Minikube + Docker Compose)

### 1. Start Infrastructure (Docker Compose)

```bash
# Start infrastructure services
docker compose -f docker-compose.infra.yml up -d

# Verify infrastructure is running
docker compose ps
```

### 2. Connect Minikube to Docker Network

```bash
# Start minikube (if not running)
minikube start

# IMPORTANT: Connect minikube to the Docker Compose network
# This allows K8s pods to resolve container names (cassandra, kafka, redis, etc.)
docker network connect ripple-network minikube

# Verify connection
docker network inspect ripple-network --format '{{range .Containers}}{{.Name}} {{end}}'
# Should show: minikube cassandra kafka redis mysql zookeeper minio
```

### 3. Build and Load Docker Images

**Option A: Build images using Minikube's Docker daemon (Recommended)**

Build images directly inside Minikube's Docker environment to avoid image transfer overhead.

```bash
eval $(minikube docker-env)

docker info | grep -i "Name:"
# Should output: Name: minikube

for module in ripple-api-gateway ripple-authorization-server ripple-upload-gateway \
              ripple-message-gateway ripple-snowflakeid-server ripple-user-presence-server \
              ripple-message-api-server ripple-message-dispatcher ripple-push-server \
              ripple-async-storage-updater; do
    echo "Building $module..."
    docker build -t $module:latest -f $module/Dockerfile .
done

docker images | grep ripple

eval $(minikube docker-env -u)

# Note: Images built this way exist inside Minikube and don't require 'minikube image load'
```

**Option B: Use existing Docker Compose images**

If you've already built images using Docker Compose, load them into Minikube.

```bash
for module in ripple-api-gateway ripple-authorization-server ripple-upload-gateway \
              ripple-message-gateway ripple-snowflakeid-server ripple-user-presence-server \
              ripple-message-api-server ripple-message-dispatcher ripple-push-server \
              ripple-async-storage-updater; do
    docker tag ripple-im-$module:latest $module:latest
    minikube image load $module:latest
done
```

### 4. Deploy to Kubernetes

```bash
# Create namespace and apply configs
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/secrets/

# Deploy all services
kubectl apply -f k8s/deployments/

# (Optional) Enable ingress
minikube addons enable ingress
kubectl apply -f k8s/ingress.yaml
echo "$(minikube ip) ripple.local" | sudo tee -a /etc/hosts
```

### 5. Verify Deployment

```bash
# Check pod status
kubectl get pods -n ripple

# Check logs
kubectl logs -n ripple deploy/ripple-message-dispatcher

# Test DNS resolution from pod
kubectl exec -n ripple deploy/ripple-user-presence-server -- getent hosts cassandra redis kafka
```

## Cleanup

```bash
# Remove K8s resources
kubectl delete namespace ripple

# Disconnect minikube from Docker network
docker network disconnect ripple-network minikube

# Stop infrastructure
docker compose down
```
