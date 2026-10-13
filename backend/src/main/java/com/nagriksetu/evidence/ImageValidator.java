package com.nagriksetu.evidence;

import com.nagriksetu.common.ApiException;
import com.nagriksetu.config.NagrikProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates uploaded evidence images. The declared Content-Type and extension are not trusted:
 * the file's leading bytes must match a supported image format (JPEG, PNG or WebP).
 */
@Component
public class ImageValidator {

    public record ValidatedImage(MultipartFile file, String contentType, String extension, String safeFilename) {
    }

    private static final Set<String> DECLARED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/pjpeg", "image/png", "image/webp", "application/octet-stream");

    private final NagrikProperties.Evidence limits;

    public ImageValidator(NagrikProperties properties) {
        this.limits = properties.evidence();
    }

    public List<ValidatedImage> validateAll(List<MultipartFile> files, boolean required) {
        List<MultipartFile> present = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (required && present.isEmpty()) {
            throw ApiException.badRequest("PHOTO_REQUIRED", "Please attach at least one photo");
        }
        if (files != null && files.stream().anyMatch(f -> f != null && f.isEmpty() && f.getOriginalFilename() != null
                && !f.getOriginalFilename().isBlank())) {
            throw ApiException.badRequest("EMPTY_FILE", "One of the selected files is empty");
        }
        if (present.size() > limits.maxFilesPerRequest()) {
            throw ApiException.badRequest("TOO_MANY_FILES",
                    "You can attach up to " + limits.maxFilesPerRequest() + " photos at a time");
        }
        return present.stream().map(this::validate).toList();
    }

    public ValidatedImage validate(MultipartFile file) {
        String name = sanitiseFilename(file.getOriginalFilename());
        if (file.getSize() > limits.maxFileSizeBytes()) {
            throw ApiException.badRequest("FILE_TOO_LARGE",
                    name + " is larger than " + (limits.maxFileSizeBytes() / (1024 * 1024)) + " MB");
        }
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!declared.isEmpty() && !DECLARED_TYPES.contains(declared)) {
            throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE", name + " is not a JPEG, PNG or WebP image");
        }
        byte[] header = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw ApiException.badRequest("INVALID_UPLOAD", "Could not read " + name);
        }
        String detected = detect(header, read);
        if (detected == null) {
            throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE", name + " is not a valid JPEG, PNG or WebP image");
        }
        String extension = switch (detected) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "webp";
        };
        return new ValidatedImage(file, detected, extension, withExtension(name, extension));
    }

    static String detect(byte[] h, int len) {
        if (len >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (len >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return "image/png";
        }
        if (len >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    /** Keeps only a safe display name: no directories, control characters or unusual symbols. */
    public static String sanitiseFilename(String original) {
        if (original == null) {
            return "photo";
        }
        String base = original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_").replaceAll("\\.{2,}", ".").trim();
        base = base.replaceAll("^[.\\s]+", "");
        if (base.isEmpty()) {
            return "photo";
        }
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }

    private static String withExtension(String name, String extension) {
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        return stem + "." + extension;
    }
}
