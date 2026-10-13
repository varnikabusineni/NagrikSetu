package com.nagriksetu.evidence;

import com.nagriksetu.config.NagrikProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net for files stored without a matching evidence row (e.g. the process crashed between
 * writing a file and committing its transaction). Only files older than the configured age are removed.
 */
@Component
public class OrphanEvidenceCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OrphanEvidenceCleanupJob.class);

    private final EvidenceStorageService storage;
    private final EvidenceRepository repository;
    private final NagrikProperties.OrphanCleanup config;

    public OrphanEvidenceCleanupJob(EvidenceStorageService storage, EvidenceRepository repository,
                                    NagrikProperties properties) {
        this.storage = storage;
        this.repository = repository;
        this.config = properties.storage().orphanCleanup();
    }

    @Scheduled(cron = "${nagrik.storage.orphan-cleanup.cron:0 17 3 * * *}")
    public void scheduledRun() {
        if (config != null && config.enabled()) {
            run();
        }
    }

    public int run() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(config == null ? 60 : config.minAgeMinutes()));
        List<String> batch = new ArrayList<>();
        int[] removed = {0};
        try {
            storage.listKeysOlderThan(cutoff, key -> {
                batch.add(key);
                if (batch.size() >= 200) {
                    removed[0] += purge(batch);
                    batch.clear();
                }
            });
            removed[0] += purge(batch);
        } catch (Exception e) {
            log.warn("Orphan evidence cleanup failed: {}", e.getMessage());
        }
        if (removed[0] > 0) {
            log.info("Orphan evidence cleanup removed {} file(s)", removed[0]);
        }
        return removed[0];
    }

    private int purge(List<String> keys) {
        if (keys.isEmpty()) {
            return 0;
        }
        Set<String> known = new HashSet<>(repository.findExistingKeys(keys));
        int count = 0;
        for (String key : keys) {
            if (!known.contains(key)) {
                storage.delete(key);
                count++;
            }
        }
        return count;
    }
}
