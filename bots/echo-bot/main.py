"""
Echo Bot - A simple bot that echoes user messages back.
Used for testing the Ripple-IM bot webhook integration.
"""

import asyncio
import json
from fastapi import FastAPI, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

app = FastAPI(title="Echo Bot")


class UserInfo(BaseModel):
    id: str


class MessageInfo(BaseModel):
    text: str
    timestamp: int


class WebhookRequest(BaseModel):
    event: str = "message"
    message_id: int
    session_id: str
    user: UserInfo
    message: MessageInfo


def sse_event(event_type: str, data: dict) -> str:
    """Format a Server-Sent Event."""
    return f"event: {event_type}\ndata: {json.dumps(data)}\n\n"


async def generate_echo_response(text: str):
    """Generate SSE events that echo the input text, streaming 1-2 chars at a time."""
    full_text = f"{text}"
    # Stream 1-2 characters at a time
    i = 0
    while i < len(full_text):
        chunk = full_text[i:i+2]
        yield sse_event("delta", {"content": chunk})
        await asyncio.sleep(0.1)  # Small delay for visible streaming effect
        i += 2
    # Send done event with the full text
    yield sse_event("done", {"full_text": full_text})


@app.post("/webhook")
async def webhook(request: WebhookRequest):
    """Handle webhook requests from Ripple-IM."""
    text = request.message.text
    return StreamingResponse(
        generate_echo_response(text),
        media_type="text/event-stream",
    )


@app.get("/health")
async def health():
    """Health check endpoint."""
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
