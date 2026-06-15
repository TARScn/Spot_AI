package com.tars.spotai.service;

import com.tars.spotai.config.MinioProperties;
import com.tars.spotai.dto.FileUploadDTO;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Stores uploaded files in MinIO and returns public object URLs.
 */
@Service
public class FileStorageService {
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public FileStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public FileUploadDTO upload(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String objectName = buildObjectName(file.getOriginalFilename(), directory);
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : DEFAULT_CONTENT_TYPE;
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1L)
                    .contentType(contentType)
                    .build());
            return new FileUploadDTO(objectName, buildPublicUrl(objectName), contentType, file.getSize());
        } catch (Exception e) {
            throw new IllegalStateException("文件上传失败", e);
        }
    }

    public void delete(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(normalizeObjectName(objectName))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("文件删除失败", e);
        }
    }

    private String buildObjectName(String originalFilename, String directory) {
        String extension = resolveExtension(originalFilename);
        String cleanDirectory = normalizeDirectory(directory);
        return cleanDirectory + "/" + LocalDate.now().format(DATE_PATH) + "/" + UUID.randomUUID() + extension;
    }

    private String resolveExtension(String originalFilename) {
        String filename = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex).toLowerCase(Locale.ROOT);
    }

    private String normalizeDirectory(String directory) {
        String value = StringUtils.hasText(directory) ? directory : "common";
        value = value.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
        return StringUtils.hasText(value) ? value : "common";
    }

    private String normalizeObjectName(String objectName) {
        String value = objectName.trim();
        String bucketPrefix = "/" + properties.getBucket() + "/";
        int bucketIndex = value.indexOf(bucketPrefix);
        if (bucketIndex >= 0) {
            return value.substring(bucketIndex + bucketPrefix.length());
        }
        return value.replace('\\', '/').replaceAll("^/+", "");
    }

    private String buildPublicUrl(String objectName) {
        return properties.getExternalEndpoint().replaceAll("/+$", "")
                + "/" + properties.getBucket()
                + "/" + objectName;
    }
}
