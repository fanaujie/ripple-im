#!/bin/bash
set -e

# Start all Ripple-IM services in the correct order
# Usage: ./start-services.sh [--with-agent]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
PID_DIR="$SCRIPT_DIR/pids"
AGENT_DIR="$SCRIPT_DIR/agents"
AGENT_JAR="$AGENT_DIR/opentelemetry-javaagent.jar"
AGENT_URL="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar"
ENABLE_AGENT=false

# Parse arguments
if [[ "$1" == "--with-agent" ]]; then
    ENABLE_AGENT=true
    echo "[INFO] OpenTelemetry Agent enabled via --with-agent flag."
fi

# Create directories if they don't exist
mkdir -p "$LOG_DIR" "$PID_DIR"

# Ensure OTel Agent exists (only if enabled)
if [[ "$ENABLE_AGENT" == "true" && ! -f "$AGENT_JAR" ]]; then
    echo "  [INFO] OTel Agent not found. Downloading..."
    mkdir -p "$AGENT_DIR"
    if command -v curl >/dev/null 2>&1; then
        curl -L -o "$AGENT_JAR" "$AGENT_URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$AGENT_JAR" "$AGENT_URL"
    else
        echo "  [WARN] Neither curl nor wget found. Cannot download OTel Agent."
    fi
    
    if [[ -f "$AGENT_JAR" ]]; then
        echo "  [INFO] OTel Agent downloaded successfully."
    else
        echo "  [WARN] Failed to download OTel Agent. Services will start without instrumentation."
    fi
fi

# Service list in startup order
SERVICES=(
    "ripple-snowflakeid-server"
    "ripple-authorization-server"
    "ripple-user-presence-server"
    "ripple-message-api-server"
    "ripple-message-dispatcher"
    "ripple-async-storage-updater"
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

    # Build Java command
    local java_cmd="java"
    if [[ "$ENABLE_AGENT" == "true" && -f "$AGENT_JAR" ]]; then
        echo "  [INFO] OTel Agent found, enabling instrumentation."
        java_cmd="$java_cmd -javaagent:$AGENT_JAR"
        java_cmd="$java_cmd -Dotel.service.name=$service"
        java_cmd="$java_cmd -Dotel.exporter.otlp.endpoint=http://localhost:4318"
        java_cmd="$java_cmd -Dotel.exporter.otlp.protocol=http/protobuf"
        java_cmd="$java_cmd -Dotel.metrics.exporter=otlp"
        java_cmd="$java_cmd -Dotel.logs.exporter=none"
        java_cmd="$java_cmd -Dotel.traces.exporter=none"
        java_cmd="$java_cmd -Dotel.resource.attributes=service.instance.id=$(hostname)"
        # Enable HTTP/gRPC server metrics (experimental)
        java_cmd="$java_cmd -Dotel.instrumentation.http.server.emit-experimental-metrics=true"
        java_cmd="$java_cmd -Dotel.instrumentation.rpc.server.emit-experimental-metrics=true"
    elif [[ "$ENABLE_AGENT" == "true" ]]; then
        echo "  [WARN] OTel Agent enabled but not found at $AGENT_JAR, starting without instrumentation."
    else
        echo "  [INFO] OTel Agent disabled."
    fi

    echo "  [START] $service"
    # Override Docker network hostnames for local execution
    export BROKER_SERVER="localhost:9094"
    export REDIS_HOST="localhost"
    export CASSANDRA_CONTACT_POINTS="localhost:9042"
    export MONGODB_URI="mongodb://localhost:27017"
    export MONGODB_DATABASE="ripple"
    export ZOOKEEPER_ADDRESS="localhost:2181"
    export SNOWFLAKEID_SERVER_HOST="localhost"
    export USER_PRESENCE_SERVICE_ADDRESS="localhost:10101"
    export MESSAGE_API_SERVER_ADDRESS="localhost:10102"
    export MINIO_ENDPOINT="http://localhost:9000"

    nohup $java_cmd -jar "$jar_path" > "$log_file" 2>&1 &
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
