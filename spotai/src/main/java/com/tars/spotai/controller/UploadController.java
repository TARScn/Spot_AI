package com.tars.spotai.controller;

import com.tars.spotai.config.MinioProperties;
import com.tars.spotai.dto.FileUploadDTO;
import com.tars.spotai.dto.Result;
import com.tars.spotai.service.FileStorageService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for file upload operations backed by MinIO.
 */
@RestController
public class UploadController {
    private final FileStorageService fileStorageService;
    private final MinioProperties minioProperties;

    public UploadController(FileStorageService fileStorageService, MinioProperties minioProperties) {
        this.fileStorageService = fileStorageService;
        this.minioProperties = minioProperties;
    }

    @PostMapping("/upload/file")
    public Result<FileUploadDTO> uploadFile(@RequestParam("file") MultipartFile file,
                                            @RequestParam(defaultValue = "common") String directory) {
        return Result.ok(fileStorageService.upload(file, directory));
    }

    @PostMapping("/upload/blog")
    public Result<FileUploadDTO> uploadBlogImage(@RequestParam("file") MultipartFile file) {
        return Result.ok(fileStorageService.upload(file, minioProperties.getBlogPrefix()));
    }

    @DeleteMapping("/upload/file")
    public Result<Void> deleteFile(@RequestParam String name) {
        fileStorageService.delete(name);
        return Result.ok(null);
    }
}
