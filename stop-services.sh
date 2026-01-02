#!/bin/bash

# Stop all Ripple-IM services
# Usage: ./stop-services.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_DIR="$SCRIPT_DIR/pids"

# Service list in reverse order (stop dependencies last)
SERVICES=(
    "ripple-upload-gateway"
    "ripple-api-gateway"
    "ripple-message-gateway"
    "ripple-push-server"
    "ripple-message-dispatcher"
    "ripple-message-api-server"
    "ripple-user-presence-server"
    "ripple-authorization-server"
    "ripple-snowflakeid-server"
)

stop_service() {
    local service=$1
    local pid_file="$PID_DIR/$service.pid"

    if [[ ! -f "$pid_file" ]]; then
        echo "  [SKIP] $service is not running (no PID file)"
        return 0
    fi

    local pid=$(cat "$pid_file")

    if ! kill -0 "$pid" 2>/dev/null; then
        echo "  [SKIP] $service is not running (PID: $pid not found)"
        rm -f "$pid_file"
        return 0
    fi

    echo "  [STOP] $service (PID: $pid)"
    kill "$pid" 2>/dev/null

    # Wait for graceful shutdown (up to 10 seconds)
    local count=0
    while kill -0 "$pid" 2>/dev/null && [[ $count -lt 10 ]]; do
        sleep 1
        ((count++))
    done

    # Force kill if still running
    if kill -0 "$pid" 2>/dev/null; then
        echo "  [FORCE] Force killing $service"
        kill -9 "$pid" 2>/dev/null
    fi

    rm -f "$pid_file"
    echo "  [OK] $service stopped"
}

echo "========================================="
echo "Stopping Ripple-IM Services"
echo "========================================="
echo ""

for service in "${SERVICES[@]}"; do
    stop_service "$service"
done

echo ""
echo "========================================="
echo "All services stopped!"
echo "========================================="
