package com.nagriksetu.evidence;

import com.nagriksetu.complaint.Complaint;
import com.nagriksetu.complaint.Resolution;
import com.nagriksetu.user.User;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Saves validated images through the configured storage provider and records their metadata.
 * Must run inside the transaction that creates the owning complaint/resolution: if that transaction
 * rolls back, every file stored during it is deleted so no orphaned files remain.
 */
@Service
public class EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

    private final EvidenceStorageService storage;
    private final EvidenceRepository repository;

    public EvidenceService(EvidenceStorageService storage, EvidenceRepository repository) {
        this.storage = storage;
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Evidence> storeAll(Complaint complaint, User uploader, List<ImageValidator.ValidatedImage> images,
                                   EvidenceType type, Resolution resolution) {
        if (images.isEmpty()) {
            return List.of();
        }
        List<String> storedKeys = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storedKeys.forEach(key -> {
                        storage.delete(key);
                        log.info("Removed evidence file {} after rolled-back transaction", key);
                    });
                }
            }
        });

        List<Evidence> saved = new ArrayList<>();
        for (ImageValidator.ValidatedImage image : images) {
            UUID id = UUID.randomUUID();
            String key = "complaints/" + complaint.getId() + "/" + type.name().toLowerCase(Locale.ROOT).replace('_', '-')
                    + "/" + id + "." + image.extension();
            String sha256;
            try (InputStream raw = image.file().getInputStream();
                 DigestInputStream in = new DigestInputStream(raw, MessageDigest.getInstance("SHA-256"))) {
                storage.store(key, in, image.file().getSize(), image.contentType());
                storedKeys.add(key);
                sha256 = HexFormat.of().formatHex(in.getMessageDigest().digest());
            } catch (IOException e) {
                throw new EvidenceStorageException("Could not save photo " + image.safeFilename(), e);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            saved.add(repository.save(new Evidence(id, complaint, resolution, uploader, type, storage.providerName(),
                    key, image.contentType(), image.safeFilename(), image.file().getSize(), sha256)));
        }
        return saved;
    }
}
