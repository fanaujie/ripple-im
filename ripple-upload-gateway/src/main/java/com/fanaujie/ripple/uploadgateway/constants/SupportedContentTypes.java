package com.fanaujie.ripple.uploadgateway.constants;

import lombok.Getter;

@Getter
public enum SupportedContentTypes {
    JPEG("image/jpeg"),
    PNG("image/png");

    private final String contentType;

    SupportedContentTypes(String contentType) {
        this.contentType = contentType;
    }

    public static SupportedContentTypes getContentType(String contentType) {
        for (SupportedContentTypes type : values()) {
            if (type.getContentType().equalsIgnoreCase(contentType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported content type: " + contentType);
    }

    public String getExtension() {
        return switch (this) {
            case JPEG -> "jpg";
            case PNG -> "png";
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported content type: " + this.contentType);
        };
    }

    public static boolean isSupported(String contentType) {
        for (SupportedContentTypes type : values()) {
            if (type.getContentType().equalsIgnoreCase(contentType)) {
                return true;
            }
        }
        return false;
    }
}
