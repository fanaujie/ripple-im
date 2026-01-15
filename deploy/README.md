# Ripple-IM Deployment

This directory contains deployment configurations for Ripple-IM services.

## Directory Structure

```
deploy/
├── docker-compose.infra.yml    # Infrastructure services (Cassandra, Kafka, Redis, etc.)
├── docker-compose.services.yml # Application services (for Docker Compose deployment)
├── configmaps/                 # Kubernetes ConfigMaps
├── deployments/                # Kubernetes Deployments
├── secrets/                    # Kubernetes Secrets
├── namespace.yaml              # Kubernetes Namespace
├── ingress.yaml                # Kubernetes Ingress
└── *.sh                        # Deployment scripts
```

## Architecture

This setup uses a **hybrid deployment**:

- **Infrastructure** (Docker Compose): Cassandra, Kafka, Redis, MySQL, Zookeeper, MinIO
- **Application Services** (Kubernetes/Minikube): All Ripple microservices
- **Network**: Both share `ripple-network`, allowing K8s pods to access infra by container name

## Prerequisites

- Docker & Docker Compose
- Minikube
- kubectl

## Quick Start (Minikube + Docker Compose)

### 1. Start Infrastructure

```bash
./deploy/01-start-infra.sh
```

### 2. Start Minikube & Connect to Docker Network

```bash
./deploy/02-connect-minikube.sh
```

### 3. Build Docker Images

**Option A: Build in Minikube (Recommended)**

```bash
# Build for Cassandra (Default)
./deploy/03a-build-images.sh

# OR Build for MongoDB
./deploy/03a-build-images.sh mongodb
```

**Option B: Load existing images**

```bash
./deploy/03b-load-images.sh
```

### 4. Deploy to Kubernetes

```bash
./deploy/04-deploy.sh
```

### 5. Verify Deployment

```bash
./deploy/05-verify.sh

# Or manually
kubectl get pods -n ripple
```

### 6. Access the Application (macOS)

On macOS with Docker driver, you need `minikube tunnel` to access the cluster.

**Why?**

- Minikube runs inside Docker's virtual network (192.168.49.0/24)
- This network is isolated from macOS host
- `minikube tunnel` creates port forwarding from localhost to the cluster

**Start the tunnel (requires sudo for port 80):**

```bash
sudo minikube tunnel
```

Keep this terminal open. You should see:

```
✅  Tunnel successfully started
🏃  Starting tunnel for service ingress-nginx-controller.
```

**Access the application:**

| Endpoint    | URL                     |
|-------------|-------------------------|
| Sign up     | http://localhost/signup |
| Login       | http://localhost/login  |
| API Gateway | http://localhost/api/*  |
| WebSocket   | ws://localhost/ws       |

### 7. Access the Application (Linux)

On Linux, Docker runs natively, so you can access via minikube IP directly:

```bash
curl http://$(minikube ip)/login
```

## Cleanup

```bash
./deploy/cleanup.sh
```

## Docker Compose Only Deployment

For simpler deployment without Kubernetes:

```bash
# Start infrastructure
docker compose -f deploy/docker-compose.infra.yml up -d

# Start application services
docker compose -f deploy/docker-compose.services.yml up -d

# Access via localhost:<port> for each service
```

## Troubleshooting

### Cannot connect to localhost after minikube tunnel

**Check 1: Is tunnel running with sudo?**

Port 80 requires root privileges:

```bash
sudo minikube tunnel
```

**Check 2: Is ingress-nginx-controller LoadBalancer type?**

```bash
kubectl get svc -n ingress-nginx ingress-nginx-controller
```

Should show `TYPE: LoadBalancer` and `EXTERNAL-IP: 127.0.0.1`.

If it shows `NodePort`, patch it:

```bash
kubectl patch svc ingress-nginx-controller -n ingress-nginx -p '{"spec": {"type": "LoadBalancer"}}'
```

**Why LoadBalancer?**

```
NodePort:
  - Exposes service on random high port (e.g., 31406)
  - Only accessible via <node-ip>:<nodeport>
  - minikube tunnel ignores NodePort services

LoadBalancer:
  - Requests an external IP
  - minikube tunnel assigns 127.0.0.1 as EXTERNAL-IP
  - Creates port forwarding to cluster
```

### Pods stuck in Pending/CrashLoopBackOff

Check pod status and logs:

```bash
kubectl describe pod -n ripple <pod-name>
kubectl logs -n ripple <pod-name>
```

Common issues:

- Infrastructure not running: `docker compose -f deploy/docker-compose.infra.yml ps`
- Minikube not connected to network: `docker network inspect ripple-network`
- Image not found: Rebuild with `./deploy/03a-build-images.sh`

### Cannot resolve infrastructure hostnames (cassandra, redis, etc.)

Ensure minikube is connected to ripple-network:

```bash
docker network connect ripple-network minikube
```

Verify from inside a pod:

```bash
kubectl exec -n ripple deploy/ripple-api-gateway -- getent hosts cassandra
```
