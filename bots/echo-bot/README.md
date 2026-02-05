# Echo Bot

A simple bot that echoes user messages back. Used for testing the Ripple-IM bot webhook integration.

## Quick Start

```bash
# Install dependencies
pip install -r requirements.txt

# Run the bot
python main.py
```

The bot will start on `http://localhost:8000`.

## Endpoints

- `POST /webhook` - Webhook endpoint for receiving messages
- `GET /health` - Health check endpoint

## Register Bot

```bash
curl -X POST http://localhost:10002/api/admin/bots \
  -H "Content-Type: application/json" \
  -d '{
    "account": "echo-bot",
    "displayName": "Echo Bot",
    "webhookUrl": "http://localhost:8000/webhook",
    "responseMode": "STREAMING",
    "description": "A simple echo bot for testing"
  }'
```

## Test Webhook Locally

```bash
curl -X POST http://localhost:8000/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "event": "message",
    "message_id": 123456,
    "session_id": "test-session",
    "user": {"id": "user-1"},
    "message": {"text": "Hello!", "timestamp": 1234567890}
  }'
```

Expected response (SSE):
```
event: delta
data: {"content": "Echo: Hello!"}

event: done
data: {"full_text": "Echo: Hello!"}
```
