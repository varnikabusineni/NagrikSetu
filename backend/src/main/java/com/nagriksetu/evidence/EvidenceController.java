package com.nagriksetu.evidence;

import com.nagriksetu.common.ApiException;
import com.nagriksetu.complaint.ComplaintAccessPolicy;
import com.nagriksetu.security.CurrentUser;
import com.nagriksetu.security.UserPrincipal;
import com.nagriksetu.user.UserRepository;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves evidence images. Every request is authorised: the caller is identified either by a bearer
 * token or by a short-lived signed URL bound to a specific user, and that user's permission to view
 * the complaint is checked again against the database before any bytes are streamed.
 */
@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private record Served(Evidence evidence, String filename) {
    }

    private static final Logger log = LoggerFactory.getLogger(EvidenceController.class);

    private final EvidenceRepository evidenceRepository;
    private final EvidenceStorageService storage;
    private final EvidenceUrlSigner signer;
    private final ComplaintAccessPolicy accessPolicy;
    private final UserRepository users;
    private final TransactionTemplate readOnlyTx;

    public EvidenceController(EvidenceRepository evidenceRepository, EvidenceStorageService storage,
                              EvidenceUrlSigner signer, ComplaintAccessPolicy accessPolicy, UserRepository users,
                              org.springframework.transaction.PlatformTransactionManager txManager) {
        this.evidenceRepository = evidenceRepository;
        this.storage = storage;
        this.signer = signer;
        this.accessPolicy = accessPolicy;
        this.users = users;
        this.readOnlyTx = new TransactionTemplate(txManager);
        this.readOnlyTx.setReadOnly(true);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(
            @PathVariable UUID id,
            @RequestParam(name = "u", required = false) Long signedUserId,
            @RequestParam(name = "exp", required = false) Long exp,
            @RequestParam(name = "sig", required = false) String sig) {

        Served served = readOnlyTx.execute(status -> {
            UserPrincipal viewer = CurrentUser.orNull();
            if (viewer == null && signedUserId != null && exp != null && signer.verify(id, signedUserId, exp, sig)) {
                viewer = users.findById(signedUserId).filter(u -> u.isEnabled()).map(UserPrincipal::from).orElse(null);
            }
            if (viewer == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "This photo link has expired. Reload the page to view it.");
            }
            Evidence e = evidenceRepository.findWithComplaint(id).orElseThrow(() -> ApiException.notFound("Photo"));
            ComplaintAccessPolicy.Access access = accessPolicy.accessOf(viewer, e.getComplaint());
            if (access == ComplaintAccessPolicy.Access.NONE) {
                throw ApiException.notFound("Photo");
            }
            // Public viewers never learn the uploader's original file name.
            String filename = access == ComplaintAccessPolicy.Access.PUBLIC
                    ? "photo." + e.getStorageKey().substring(e.getStorageKey().lastIndexOf('.') + 1)
                    : e.getOriginalFilename();
            return new Served(e, filename);
        });
        Evidence evidence = served.evidence();

        try {
            EvidenceStorageService.StoredObject object = storage.open(evidence.getStorageKey());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(evidence.getContentType()))
                    .contentLength(object.sizeBytes())
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                    .header("Content-Disposition", ContentDisposition.inline()
                            .filename(served.filename()).build().toString())
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'; sandbox")
                    .body(new InputStreamResource(object.content()));
        } catch (NoSuchFileException e) {
            log.error("Evidence {} metadata exists but file {} is missing from storage", id, evidence.getStorageKey());
            throw new ApiException(HttpStatus.NOT_FOUND, "FILE_MISSING", "This photo is no longer available");
        } catch (IOException e) {
            log.error("Could not read evidence {}: {}", id, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE",
                    "Photo storage is temporarily unavailable. Please try again.");
        }
    }
}
