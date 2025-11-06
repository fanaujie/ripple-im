package com.fanaujie.ripple.uploadgateway.service;

import com.fanaujie.ripple.uploadgateway.config.AvatarProperties;
import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadResponse;
import com.fanaujie.ripple.uploadgateway.exception.FileSizeExceededException;
import com.fanaujie.ripple.uploadgateway.exception.InvalidFileExtensionException;
import com.fanaujie.ripple.uploadgateway.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@Slf4j
public class AvatarFileValidationService {

    private final AvatarProperties avatarProperties;

    private final FileUtils avatarFileUtils;

    public AvatarFileValidationService(
            AvatarProperties avatarProperties,
            @Qualifier("avatarFileUtils") FileUtils avatarFileUtils) {
        this.avatarProperties = avatarProperties;
        this.avatarFileUtils = avatarFileUtils;
    }

    public AvatarUploadResponse validateAndReadImageWithDimensions(byte[] fileData) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(fileData));
        } catch (IOException e) {
            log.error("Error reading avatar file: ", e);
            return new AvatarUploadResponse(400, "Invalid image file", null);
        }

        int width = image.getWidth();
        int height = image.getHeight();
        if (width > avatarProperties.getMaxWidth() || height > avatarProperties.getMaxHeight()) {
            return new AvatarUploadResponse(
                    400, "Image dimensions exceed maximum allowed size", null);
        }

        return null;
    }

    public AvatarUploadResponse validateFileHash(String expectedHash, byte[] fileData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (!expectedHash
                    .trim()
                    .toLowerCase()
                    .equals(Hex.encodeHexString(digest.digest(fileData), true))) {
                return new AvatarUploadResponse(400, "File hash does not match", null);
            }
            return null;
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available: ", e);
            return new AvatarUploadResponse(500, "Hash algorithm not supported", null);
        } catch (Exception e) {
            log.error("Error generating file hash: ", e);
            return new AvatarUploadResponse(500, "Internal server error", null);
        }
    }

    public AvatarUploadResponse validateFileSize(MultipartFile file) {
        try {
            this.avatarFileUtils.validateFileSize(file.getSize());
            return null;
        } catch (FileSizeExceededException e) {
            return new AvatarUploadResponse(400, e.getMessage(), null);
        }
    }

    public String extractFileExtension(String fileName) throws InvalidFileExtensionException {
        return this.avatarFileUtils.extractExtension(fileName);
    }
}
