package com.fanaujie.ripple.uploadgateway.utils;

import com.fanaujie.ripple.uploadgateway.exception.FileSizeExceededException;
import com.fanaujie.ripple.uploadgateway.exception.InvalidFileExtensionException;

public class FileUtils {

    private final int maxExtensionLength;
    private final long maxFileSizeBytes;

    public FileUtils(int maxExtensionLength, long maxFileSizeBytes) {
        this.maxExtensionLength = maxExtensionLength;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public String extractExtension(String filename) throws InvalidFileExtensionException {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        String sanitized = sanitizeFilename(filename);
        int lastDot = sanitized.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == sanitized.length() - 1) {
            return "";
        }
        String extension = sanitized.substring(lastDot);
        if (extension.length() > maxExtensionLength) {
            throw new InvalidFileExtensionException(
                    String.format(
                            "File extension too long: %d characters (max: %d)",
                            extension.length(), maxExtensionLength));
        }
        return extension;
    }

    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        // Remove path traversal patterns and directory separators
        return filename.replaceAll("\\.\\.", "").replaceAll("[/\\\\]", "").trim();
    }

    public void validateFileSize(long fileSize) throws FileSizeExceededException {
        if (fileSize > maxFileSizeBytes) {
            throw new FileSizeExceededException(
                    String.format(
                            "File size %d bytes exceeds maximum allowed size %d bytes",
                            fileSize, maxFileSizeBytes));
        }
    }
}
