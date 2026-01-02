# Ripple-IM

A high-performance, scalable instant messaging system built with microservices architecture.

## Features

- **Real-time Messaging**: WebSocket-based real-time communication with low latency
- **Direct & Group Chat**: Support for one-on-one and group conversations
- **OAuth2 Authentication**: Secure authentication with JWT tokens and Google OAuth2 support
- **Scalable Architecture**: Microservices design with gRPC inter-service communication
- **Message Persistence**: Reliable message storage with Apache Cassandra
- **High Availability**: Kafka-based message queuing for reliable delivery
- **User Presence**: Real-time online status tracking
- **File Upload**: Avatar and attachment support with MinIO object storage
- **Distributed ID Generation**: Snowflake algorithm for globally unique IDs

## Client Application

The demo desktop client for Ripple-IM is available at: [ripple-im-app](https://github.com/fanaujie/ripple-im-app)

## Architecture Overview

![System Architecture](images/architecture-overview.svg)

Ripple-IM follows a microservices architecture pattern with the following layers:

- **Gateway Layer**: API Gateway, Message Gateway, Upload Gateway
- **Core Services Layer**: Message processing, dispatching, and push notifications
- **Infrastructure Layer**: Databases, caches, message queues, and service discovery

## Message Flow

![Message Flow](images/message-flow.svg)

1. Client sends message via HTTP POST to API Gateway
2. API Gateway forwards request to Message API Server (gRPC)
3. Message API generates Snowflake ID and publishes to Kafka
4. Message Dispatcher consumes, persists to Cassandra, and updates Redis cache
5. Push notification published to Kafka
6. Push Server queries user online status and delivers via Message Gateway (gRPC)
7. Message Gateway pushes to recipient via WebSocket
8. Recipient client receives message through WebSocket connection

## Modules

| Module                           | Description                                                       | Port          | Protocol  |
|----------------------------------|-------------------------------------------------------------------|---------------|-----------|
| **ripple-api-gateway**           | REST API entry point for user, conversation, and group management | 10002         | HTTP      |
| **ripple-authorization-server**  | OAuth2 authentication and JWT token management                    | 10001         | HTTP      |
| **ripple-message-gateway**       | WebSocket server for real-time communication                      | 10200 / 10103 | WS / gRPC |
| **ripple-message-api-server**    | Core message processing and routing logic                         | 10102         | gRPC      |
| **ripple-message-dispatcher**    | Kafka consumer for message routing and persistence                | -             | Kafka     |
| **ripple-push-server**           | Push notification service for online users                        | -             | Kafka     |
| **ripple-upload-gateway**        | File upload service with MinIO integration                        | 10003         | HTTP      |
| **ripple-user-presence-server**  | User online status management                                     | 10101         | gRPC      |
| **ripple-snowflakeid-server**    | Distributed unique ID generation                                  | 10100         | Netty     |
| **ripple-async-storage-updater** | Kafka consumer for async storage synchronization                  | -             | Kafka     |
| **ripple-storage**               | Data persistence layer (Cassandra)                                | -             | Library   |
| **ripple-cache**                 | Redis caching layer                                               | -             | Library   |
| **ripple-communication**         | gRPC and Kafka communication utilities                            | -             | Library   |
| **ripple-protobuf**              | Protocol Buffer message definitions                               | -             | Library   |

## Technology Stack

### Core

| Category  | Technology              |
|-----------|-------------------------|
| Language  | Java                    |
| Framework | Spring Boot             |
| Security  | Spring Security, OAuth2 |

### Communication

| Category      | Technology       |
|---------------|------------------|
| RPC           | gRPC             |
| Serialization | Protocol Buffers |
| Message Queue | Apache Kafka     |
| Network       | Netty            |

### Storage

| Category       | Technology       |
|----------------|------------------|
| Main Database  | Apache Cassandra |
| Auth Database  | MySQL            |
| Cache          | Redis            |
| Object Storage | MinIO            |

### Service Discovery

| Technology       |
|------------------|
| Apache ZooKeeper |

## Prerequisites

- **Java 17** or higher
- **Maven 3.6+**
- **Docker & Docker Compose** (for infrastructure services)

### Infrastructure Services

| Service          | Default Port |
|------------------|--------------|
| Apache Cassandra | 9042         |
| MySQL            | 3306         |
| Redis            | 6379         |
| Apache Kafka     | 9092         |
| Apache ZooKeeper | 2181         |
| MinIO            | 9000         |

## Getting Started

### Prerequisites

**Configure Google OAuth** (Required for Authorization Server):

> **Warning**: The default client-id and client-secret in the code are placeholders and **will not work**. You must
> replace them with your own valid Google OAuth credentials.

### Option 1: Local Development with Docker Compose

The project uses split Docker Compose files for flexibility:

| File                                 | Contents                                                          | Use Case           |
|--------------------------------------|-------------------------------------------------------------------|--------------------|
| `deploy/docker-compose.infra.yml`    | Infrastructure (MySQL, Cassandra, Redis, Kafka, Zookeeper, MinIO) | Always needed      |
| `deploy/docker-compose.services.yml` | All 10 application services                                       | Full stack testing |

#### Start Infrastructure Only (for IDE debugging)

```bash
# Start infrastructure services
docker compose -f deploy/docker-compose.infra.yml up -d

# Run individual services from IDE with these environment variables:
# KAFKA_BROKER: localhost:9094 (external listener)
# Other services: localhost:<port>
```

#### Start Full Stack

```bash
# Start everything
docker compose -f deploy/docker-compose.infra.yml -f deploy/docker-compose.services.yml up -d

# Or start specific services
docker compose -f deploy/docker-compose.services.yml up -d ripple-api-gateway
```

### Option 2: Kubernetes Deployment

For production or staging environments, deploy to Kubernetes with external managed infrastructure.

See [Deployment Guide](deploy/README.md) for detailed instructions.

**Quick Start (Minikube):**

```bash
# Use the deployment scripts
./deploy/01-start-infra.sh
./deploy/02-connect-minikube.sh
./deploy/03a-build-images.sh
./deploy/04-deploy.sh
```

### Option 3: Manual JAR Execution

#### Build the Project

```bash
# Build all modules
mvn clean install -DskipTests
```

#### Start Services

```bash
# Start all services (in correct order)
./start-services.sh
```

The script will:
- Start all 9 services in the correct dependency order
- Save PIDs to `pids/` directory for management
- Save logs to `logs/` directory

#### Stop Services

```bash
# Stop all services
./stop-services.sh
```

#### View Logs

```bash
# View logs for a specific service
tail -f logs/<service-name>.log

# Example: view API Gateway logs
tail -f logs/ripple-api-gateway.log
```

## API Documentation

After starting the gateway services, you can access the interactive Swagger UI documentation:

| Service        | Swagger UI URL                         |
|----------------|----------------------------------------|
| API Gateway    | http://localhost:10002/swagger-ui.html |
| Upload Gateway | http://localhost:10003/swagger-ui.html |

## WebSocket Protocol

### Connection

```
ws://localhost:10200/ws
Headers:
  Authorization: Bearer <JWT_TOKEN>
  X-Device-Id: <DEVICE_ID>
```

### Message Format (Protocol Buffers)

```protobuf
message WsMessage {
  oneof message_type {
    HeartbeatRequest heartbeat_request = 1;
    HeartbeatResponse heartbeat_response = 2;
    PushMessageRequest push_message_request = 3;
  }
}

message HeartbeatRequest {
  string user_id = 1;
  int64 timestamp = 2;
}

message PushMessageRequest {
  int64 message_id = 1;
  int64 sender_id = 2;
  string conversation_id = 3;
  string text = 4;
  int64 timestamp = 5;
}
```

## Database Schema

| Database  | Schema File                                                                                                              |
|-----------|--------------------------------------------------------------------------------------------------------------------------|
| Cassandra | [cassandra-ddl/ripple.cql](cassandra-ddl/ripple.cql)                                                                     |
| MySQL     | [ripple-authorization-server/.../V1__init.sql](ripple-authorization-server/src/main/resources/db/migration/V1__init.sql) |

## License

MIT License
