#!/bin/bash
set -e

# Verify Ripple-IM Kubernetes deployment

echo "Checking pod status..."
kubectl get pods -n ripple

echo ""
echo "Checking logs for message-dispatcher..."
kubectl logs -n ripple deploy/ripple-message-dispatcher --tail=20

echo ""
echo "Testing DNS resolution from pod..."
kubectl exec -n ripple deploy/ripple-user-presence-server -- getent hosts cassandra redis kafka
