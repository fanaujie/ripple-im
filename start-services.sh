#!/bin/bash
set -e

# Start all Ripple-IM services in the correct order
# Usage: ./start-services.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
PID_DIR="$SCRIPT_DIR/pids"

# Create directories if they don't exist
mkdir -p "$LOG_DIR" "$PID_DIR"

# Service list in startup order
SERVICES=(
    "ripple-snowflakeid-server"
    "ripple-authorization-server"
    "ripple-user-presence-server"
    "ripple-message-api-server"
    "ripple-message-dispatcher"
    "ripple-push-server"
    "ripple-message-gateway"
    "ripple-api-gateway"
    "ripple-upload-gateway"
)

start_service() {
    local service=$1
    local jar_path="$SCRIPT_DIR/$service/target/$service-1.0-SNAPSHOT.jar"
    local pid_file="$PID_DIR/$service.pid"
    local log_file="$LOG_DIR/$service.log"

    if [[ -f "$pid_file" ]]; then
        local existing_pid=$(cat "$pid_file")
        if kill -0 "$existing_pid" 2>/dev/null; then
            echo "  [SKIP] $service is already running (PID: $existing_pid)"
            return 0
        else
            rm -f "$pid_file"
        fi
    fi

    if [[ ! -f "$jar_path" ]]; then
        echo "  [ERROR] JAR not found: $jar_path"
        echo "  Please run 'mvn clean install -DskipTests' first"
        return 1
    fi

    echo "  [START] $service"
    nohup java -jar "$jar_path" > "$log_file" 2>&1 &
    local pid=$!
    echo "$pid" > "$pid_file"
    echo "  [OK] $service started (PID: $pid)"

    # Wait a moment for the service to initialize
    sleep 2
}

echo "========================================="
echo "Starting Ripple-IM Services"
echo "========================================="
echo ""
echo "Log directory: $LOG_DIR"
echo "PID directory: $PID_DIR"
echo ""

for service in "${SERVICES[@]}"; do
    start_service "$service"
done

echo ""
echo "========================================="
echo "All services started!"
echo "========================================="
echo ""
echo "To check logs: tail -f $LOG_DIR/<service-name>.log"
echo "To stop services: ./stop-services.sh"
