package com.fanaujie.ripple.uploadgateway.utils;

import com.fanaujie.ripple.uploadgateway.exception.FileSizeExceededException;
import com.fanaujie.ripple.uploadgateway.exception.InvalidFileExtensionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilsTest {

    private FileUtils fileUtils;

    // Max extension length of 10, max file size of 5MB
    private static final int MAX_EXTENSION_LENGTH = 10;
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

    @BeforeEach
    void setUp() {
        fileUtils = new FileUtils(MAX_EXTENSION_LENGTH, MAX_FILE_SIZE_BYTES);
    }

    // ==================== extractExtension Tests ====================

    @Test
    void extractExtension_NormalFilename_ReturnsExtension() throws InvalidFileExtensionException {
        String result = fileUtils.extractExtension("test.jpg");
        assertEquals(".jpg", result);
    }

    @Test
    void extractExtension_MultipleDotsInFilename_ReturnsLastExtension() throws InvalidFileExtensionException {
        String result = fileUtils.extractExtension("my.file.name.png");
        assertEquals(".png", result);
    }

    @Test
    void extractExtension_NoExtension_ReturnsEmpty() throws InvalidFileExtensionException {
        String result = fileUtils.extractExtension("filename");
        assertEquals("", result);
    }

    @Test
    void extractExtension_DotAtStart_ReturnsEmpty() throws InvalidFileExtensionException {
        String result = fileUtils.extractExtension(".hidden");
        assertEquals("", result);
    }

    @Test
    void extractExtension_DotAtEnd_ReturnsEmpty() throws InvalidFileExtensionException {
        String result = fileUtils.extractExtension("filename.");
        assertEquals("", result);
    }

    @Test
    void extractExtension_NullFilename_ReturnsEmpty() throws InvalidFileExtensionException {
        String result = fileUtils.extractExtension(null);
        assertEquals("", result);
    }

    @Test
    void extractExtension_BlankFilename_ReturnsEmpty() throws InvalidFileExtensionException {
        String result = fileUtils.extractExtension("   ");
        assertEquals("", result);
    }

    @Test
    void extractExtension_ExtensionTooLong_ThrowsException() {
        assertThrows(InvalidFileExtensionException.class, () -> {
            fileUtils.extractExtension("file.verylongextension");
        });
    }

    @Test
    void extractExtension_PathTraversal_Sanitized() throws InvalidFileExtensionException {
        String result = fileUtils.extractExtension("../../../etc/passwd");
        // After sanitization: "etcpasswd" which has no valid extension
        assertEquals("", result);
    }

    @Test
    void extractExtension_PathTraversalWithExtension_Sanitized() throws InvalidFileExtensionException {
        String result = fileUtils.extractExtension("../../file.jpg");
        // After sanitization: "file.jpg"
        assertEquals(".jpg", result);
    }

    @Test
    void extractExtension_BackslashPath_Sanitized() throws InvalidFileExtensionException {
        String result = fileUtils.extractExtension("..\\..\\file.png");
        // After sanitization: "file.png"
        assertEquals(".png", result);
    }

    // ==================== sanitizeFilename Tests ====================

    @Test
    void sanitizeFilename_NormalFilename_Unchanged() {
        String result = fileUtils.sanitizeFilename("normal_file.jpg");
        assertEquals("normal_file.jpg", result);
    }

    @Test
    void sanitizeFilename_PathTraversalDotDot_Removed() {
        String result = fileUtils.sanitizeFilename("../../../etc/passwd");
        assertEquals("etcpasswd", result);
    }

    @Test
    void sanitizeFilename_ForwardSlash_Removed() {
        String result = fileUtils.sanitizeFilename("path/to/file.jpg");
        assertEquals("pathtofile.jpg", result);
    }

    @Test
    void sanitizeFilename_BackSlash_Removed() {
        String result = fileUtils.sanitizeFilename("path\\to\\file.jpg");
        assertEquals("pathtofile.jpg", result);
    }

    @Test
    void sanitizeFilename_MixedPathCharacters_AllRemoved() {
        String result = fileUtils.sanitizeFilename("..\\../path/to\\..file.txt");
        assertEquals("pathtofile.txt", result);
    }

    @Test
    void sanitizeFilename_NullInput_ReturnsEmpty() {
        String result = fileUtils.sanitizeFilename(null);
        assertEquals("", result);
    }

    @Test
    void sanitizeFilename_BlankInput_ReturnsEmpty() {
        String result = fileUtils.sanitizeFilename("   ");
        assertEquals("", result);
    }

    @Test
    void sanitizeFilename_OnlyPathCharacters_ReturnsEmpty() {
        String result = fileUtils.sanitizeFilename("../..///\\\\");
        assertEquals("", result);
    }

    @Test
    void sanitizeFilename_WhitespaceTrimmed() {
        String result = fileUtils.sanitizeFilename("  file.jpg  ");
        assertEquals("file.jpg", result);
    }

    // ==================== validateFileSize Tests ====================

    @Test
    void validateFileSize_WithinLimit_NoException() {
        assertDoesNotThrow(() -> {
            fileUtils.validateFileSize(1024); // 1KB
        });
    }

    @Test
    void validateFileSize_ExactlyAtLimit_NoException() {
        assertDoesNotThrow(() -> {
            fileUtils.validateFileSize(MAX_FILE_SIZE_BYTES);
        });
    }

    @Test
    void validateFileSize_ExceedsLimit_ThrowsException() {
        FileSizeExceededException exception = assertThrows(FileSizeExceededException.class, () -> {
            fileUtils.validateFileSize(MAX_FILE_SIZE_BYTES + 1);
        });

        assertTrue(exception.getMessage().contains("exceeds maximum allowed size"));
    }

    @Test
    void validateFileSize_ZeroSize_NoException() {
        assertDoesNotThrow(() -> {
            fileUtils.validateFileSize(0);
        });
    }

    @Test
    void validateFileSize_NegativeSize_NoException() {
        // Negative size doesn't exceed limit, so no exception
        assertDoesNotThrow(() -> {
            fileUtils.validateFileSize(-1);
        });
    }
}
