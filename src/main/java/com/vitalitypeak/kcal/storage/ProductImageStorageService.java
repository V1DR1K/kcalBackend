package com.vitalitypeak.kcal.storage;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.vitalitypeak.kcal.common.BadRequestException;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class ProductImageStorageService {
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");
    private static final Set<String> ALLOWED_TYPES = EXTENSIONS.keySet();

    private final S3Client s3;
    private final StorageProperties properties;

    public ProductImageStorageService(S3Client s3, StorageProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    public StoredImage storeFoodImage(Long foodId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("La imagen es obligatoria.");
        }
        if (file.getSize() > properties.maxImageSizeBytes()) {
            throw new BadRequestException("La imagen no puede superar 5 MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BadRequestException("La imagen debe ser JPG, PNG o WebP.");
        }

        String objectKey = "foods/%d/%s.%s".formatted(foodId, UUID.randomUUID(), EXTENSIONS.get(contentType));
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(objectKey)
                            .contentType(contentType)
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException ex) {
            throw new BadRequestException("No se pudo leer la imagen.");
        }
        return new StoredImage(objectKey, publicUrl(objectKey));
    }

    public void deleteQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return;
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(objectKey).build());
        } catch (RuntimeException ignored) {
        }
    }

    private String publicUrl(String objectKey) {
        return properties.publicBaseUrl().replaceAll("/+$", "") + "/" + properties.bucket() + "/" + objectKey;
    }
}
