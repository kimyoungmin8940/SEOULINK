package com.seoulink.backend.domain.review.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 리뷰 작성 화면에서 선택한 이미지 파일을 로컬 저장소에 업로드합니다.
 */
@RestController
@RequestMapping("/api/review-images")
public class ReviewImageUploadController {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Path UPLOAD_DIRECTORY = Path.of("uploads", "reviews");

    /**
     * 이미지 파일을 저장하고, 리뷰의 imageUrls에 넣을 공개 URL 목록을 반환합니다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReviewImageUploadResponse upload(
            @RequestParam("files") List<MultipartFile> files
    ) throws IOException {
        if (files.isEmpty() || files.size() > 8) {
            throw new IllegalArgumentException("사진은 1장 이상 8장 이하로 업로드할 수 있습니다.");
        }

        Files.createDirectories(UPLOAD_DIRECTORY);

        List<String> imageUrls = files.stream()
                .map(this::storeImage)
                .toList();

        return new ReviewImageUploadResponse(imageUrls);
    }

    private String storeImage(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("사진은 비어 있지 않은 5MB 이하 파일이어야 합니다.");
        }

        String extension = extensionFor(file.getContentType());
        String filename = UUID.randomUUID() + extension;
        Path target = UPLOAD_DIRECTORY.resolve(filename);

        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("사진 파일을 저장하지 못했습니다.", exception);
        }

        return "/uploads/reviews/" + filename;
    }

    private String extensionFor(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case MediaType.IMAGE_JPEG_VALUE -> ".jpg";
            case MediaType.IMAGE_PNG_VALUE -> ".png";
            case "image/webp" -> ".webp";
            case MediaType.IMAGE_GIF_VALUE -> ".gif";
            default -> throw new IllegalArgumentException("JPG, PNG, WEBP, GIF 파일만 업로드할 수 있습니다.");
        };
    }

    public record ReviewImageUploadResponse(List<String> imageUrls) {
    }
}
