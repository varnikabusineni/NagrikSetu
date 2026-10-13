package com.nagriksetu.evidence;

import com.nagriksetu.complaint.Complaint;
import com.nagriksetu.complaint.Resolution;
import com.nagriksetu.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for one stored evidence image. The bytes live in the configured {@link EvidenceStorageService};
 * every official who works on the complaint sees this same record — files are never copied per official.
 */
@Entity
@Table(name = "evidence")
public class Evidence {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", updatable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolution_id")
    private Resolution resolution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", updatable = false)
    private User uploader;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, updatable = false)
    private EvidenceType type;

    @Column(name = "storage_provider", nullable = false, updatable = false)
    private String storageProvider;

    @Column(name = "storage_key", nullable = false, unique = true, updatable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "original_filename", nullable = false, updatable = false)
    private String originalFilename;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    private String sha256;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Evidence() {
    }

    public Evidence(UUID id, Complaint complaint, Resolution resolution, User uploader, EvidenceType type,
                    String storageProvider, String storageKey, String contentType, String originalFilename,
                    long sizeBytes, String sha256) {
        this.id = id;
        this.complaint = complaint;
        this.resolution = resolution;
        this.uploader = uploader;
        this.type = type;
        this.storageProvider = storageProvider;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.originalFilename = originalFilename;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
    }

    public UUID getId() {
        return id;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public User getUploader() {
        return uploader;
    }

    public EvidenceType getType() {
        return type;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
