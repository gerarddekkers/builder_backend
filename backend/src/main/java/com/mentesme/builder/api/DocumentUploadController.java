package com.mentesme.builder.api;

import com.mentesme.builder.config.S3Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@ConditionalOnProperty(name = "builder.s3.enabled", havingValue = "true")
public class DocumentUploadController {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadController.class);

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final long MAX_SIZE = 25 * 1024 * 1024; // 25 MB

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public DocumentUploadController(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "journeyId", required = false) Long journeyId
    ) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Geen bestand geselecteerd"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Ongeldig bestandstype. Toegestaan: PDF, Word, Excel, PowerPoint, afbeeldingen"));
        }

        if (file.getSize() > MAX_SIZE) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bestand te groot (max 25 MB)"));
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }

        String journeyFolder = journeyId != null ? String.valueOf(journeyId) : "_draft";
        String fileName = UUID.randomUUID() + ext;
        String key = s3Properties.getPrefix() + "/learning-journeys/" + journeyFolder + "/docs/" + fileName;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .contentType(contentType)
                .acl(ObjectCannedACL.PUBLIC_READ)
                .cacheControl("public, max-age=31536000")
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

        String url = "https://" + s3Properties.getBucket() + ".s3.amazonaws.com/" + key;
        log.info("Document uploaded: {} ({} bytes) → {}", originalName, file.getSize(), url);

        return ResponseEntity.ok(Map.of(
                "url", url,
                "fileName", originalName != null ? originalName : fileName
        ));
    }
}
