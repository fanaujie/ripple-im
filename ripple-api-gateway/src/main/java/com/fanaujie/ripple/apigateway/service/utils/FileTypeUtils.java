package com.fanaujie.ripple.apigateway.service.utils;

import java.util.Set;

public class FileTypeUtils {

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg");

    public static boolean isImageFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return false;
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase();
        return IMAGE_EXTENSIONS.contains(extension);
    }

    public static String generateFilePreviewText(String senderId, String fileName) {
        String displayName = String.format("{{%s}}", senderId);
        if (isImageFile(fileName)) {
            return displayName + " sent an image";
        }
        return displayName + " sent a file";
    }
}
