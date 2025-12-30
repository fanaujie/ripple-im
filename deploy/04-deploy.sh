#!/bin/bash
set -e

# Deploy Ripple-IM services to Kubernetes

# Change to project root directory (parent of deploy/)
cd "$(dirname "$0")/.."

echo "Creating namespace and applying configs..."
kubectl apply -f deploy/namespace.yaml
kubectl apply -f deploy/configmaps/
kubectl apply -f deploy/secrets/

echo "Deploying all services..."
kubectl apply -f deploy/deployments/

echo "Enabling ingress addon..."
minikube addons enable ingress

echo "Waiting for ingress-nginx-controller to be ready..."
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

# Wait for the admission webhook to be fully operational
# The controller pod may be "ready" but the webhook service needs time to start accepting connections
echo "Waiting for ingress-nginx admission webhook to be ready..."
for i in {1..30}; do
    if kubectl get endpoints ingress-nginx-controller-admission -n ingress-nginx -o jsonpath='{.subsets[0].addresses[0].ip}' 2>/dev/null | grep -q .; then
        echo "Admission webhook endpoint is ready"
        break
    fi
    echo "  Waiting for admission webhook endpoint... ($i/30)"
    sleep 2
done
# Additional delay to ensure the webhook is fully operational
sleep 5

# On macOS, patch ingress-nginx to LoadBalancer for minikube tunnel
if [[ "$(uname)" == "Darwin" ]]; then
    echo "Patching ingress-nginx-controller to LoadBalancer (required for macOS)..."
    kubectl patch svc ingress-nginx-controller -n ingress-nginx \
      -p '{"spec": {"type": "LoadBalancer"}}'
fi

echo "Applying ingress rules..."
kubectl apply -f deploy/ingress.yaml

echo ""
echo "=========================================="
echo "  Deployment complete!"
echo "=========================================="
echo ""