package com.fanaujie.ripple.botexecutor.model;

/**
 * Represents the result of a webhook call.
 */
public class WebhookResponse {
    private final boolean success;
    private final String content;
    private final boolean isStreaming;
    private final int statusCode;
    private final String errorMessage;

    private WebhookResponse(boolean success, String content, boolean isStreaming, int statusCode, String errorMessage) {
        this.success = success;
        this.content = content;
        this.isStreaming = isStreaming;
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
    }

    public static WebhookResponse success(String content) {
        return new WebhookResponse(true, content, false, 200, null);
    }

    public static WebhookResponse streaming() {
        return new WebhookResponse(true, null, true, 200, null);
    }

    public static WebhookResponse failure(int statusCode, String errorMessage) {
        return new WebhookResponse(false, null, false, statusCode, errorMessage);
    }

    public boolean isSuccess() { return success; }
    public String getContent() { return content; }
    public boolean isStreaming() { return isStreaming; }
    public int getStatusCode() { return statusCode; }
    public String getErrorMessage() { return errorMessage; }
}
