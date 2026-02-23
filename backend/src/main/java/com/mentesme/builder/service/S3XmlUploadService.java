package com.mentesme.builder.service;

import com.mentesme.builder.config.S3Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@ConditionalOnProperty(name = "builder.s3.enabled", havingValue = "true")
public class S3XmlUploadService {

    private static final Logger log = LoggerFactory.getLogger(S3XmlUploadService.class);

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public S3XmlUploadService(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    /**
     * Upload a single XML file to S3.
     */
    public void uploadXml(String key, String xmlContent) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .contentType("application/xml; charset=utf-8")
                .build();

        s3Client.putObject(request,
                RequestBody.fromBytes(xmlContent.getBytes(StandardCharsets.UTF_8)));

        log.info("Uploaded s3://{}/{}", s3Properties.getBucket(), key);
    }

    /**
     * Build S3 key matching Metro convention: {prefix}/{lang}/{type}_{slug}_{LANG}.xml
     * Example: test/nl/questionnaire_persoonlijk_leiderschap_NL.xml
     */
    public String buildKey(String prefix, String language, String assessmentName, String type) {
        String slug = toSlug(assessmentName);
        String langSuffix = language.toUpperCase();
        return prefix + "/" + language + "/" + type + "_" + slug + "_" + langSuffix + ".xml";
    }

    /**
     * Convert assessment name to a URL-safe slug: lowercase, spaces to underscores,
     * remove special characters, trim trailing underscores.
     */
    static String toSlug(String name) {
        if (name == null || name.isBlank()) return "unnamed";
        String slug = name.trim().toLowerCase()
                .replaceAll("[^a-z0-9\\s_-]", "")  // remove special chars
                .replaceAll("[\\s-]+", "_")          // spaces/dashes to underscores
                .replaceAll("_+", "_")               // collapse multiple underscores
                .replaceAll("^_|_$", "");            // trim leading/trailing underscores
        return slug.isEmpty() ? "unnamed" : slug;
    }

    /**
     * Build full S3 URL: https://{bucket}.s3.amazonaws.com/{key}
     * Metro expects the global endpoint (without region).
     */
    public String buildUrl(String key) {
        return "https://" + s3Properties.getBucket() + ".s3.amazonaws.com/" + key;
    }

    /**
     * Best-effort deletion of uploaded S3 objects for rollback.
     * Logs errors but does not throw — the primary exception must propagate.
     */
    public void deleteObjects(List<String> keys) {
        for (String key : keys) {
            try {
                log.warn("Rolling back S3 upload: deleting s3://{}/{}", s3Properties.getBucket(), key);
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(s3Properties.getBucket())
                        .key(key)
                        .build());
            } catch (Exception e) {
                log.error("Failed to delete S3 object {} during rollback: {}", key, e.getMessage());
            }
        }
    }
}
