package com.nagriksetu.evidence;

import com.nagriksetu.config.NagrikProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Stores evidence in a persistent directory on disk ({@code NAGRIK_STORAGE_LOCAL_DIR}). Files survive
 * application restarts; back the directory up together with the database.
 */
@Service
@ConditionalOnProperty(name = "nagrik.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalEvidenceStorageService implements EvidenceStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalEvidenceStorageService.class);
    private static final String TEMP_SUFFIX = ".uploading";

    private final Path root;

    @Autowired
    public LocalEvidenceStorageService(NagrikProperties properties) throws IOException {
        this(Path.of(properties.storage().local().directory()));
    }

    public LocalEvidenceStorageService(Path directory) throws IOException {
        this.root = directory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (!Files.isWritable(root)) {
            throw new IllegalStateException("Evidence storage directory is not writable: " + root);
        }
        log.info("Evidence storage: local directory {}", root);
    }

    public Path root() {
        return root;
    }

    @Override
    public String providerName() {
        return "local";
    }

    @Override
    public void store(String key, InputStream content, long sizeBytes, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        try {
            Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public StoredObject open(String key) throws IOException {
        Path path = resolve(key);
        return new StoredObject(Files.newInputStream(path), Files.size(path));
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete evidence file {}: {}", key, e.getMessage());
        }
    }

    @Override
    public void listKeysOlderThan(Instant cutoff, Consumer<String> visitor) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                try {
                    if (Files.getLastModifiedTime(p).toInstant().isBefore(cutoff)) {
                        String key = root.relativize(p).toString().replace('\\', '/');
                        if (key.endsWith(TEMP_SUFFIX)) {
                            Files.deleteIfExists(p);
                        } else {
                            visitor.accept(key);
                        }
                    }
                } catch (IOException | IllegalArgumentException e) {
                    log.debug("Skipping {} during orphan scan: {}", p, e.getMessage());
                }
            });
        }
        pruneEmptyDirectories(root);
    }

    /** Resolves a key inside the root, rejecting anything that would escape it. */
    Path resolve(String key) {
        StorageKeys.requireSafe(key);
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("Storage key escapes the storage directory");
        }
        return resolved;
    }

    private void pruneEmptyDirectories(Path dir) throws IOException {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(dir)) {
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    pruneEmptyDirectories(child);
                    try (Stream<Path> remaining = Files.list(child)) {
                        if (remaining.findAny().isEmpty()) {
                            Files.deleteIfExists(child);
                        }
                    }
                }
            }
        }
    }
}
