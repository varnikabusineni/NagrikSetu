package com.nagriksetu.evidence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

/** Test helper: makes files look old enough for the orphan cleanup job. */
public final class EvidenceOrphanCleanupProbe {

    private EvidenceOrphanCleanupProbe() {
    }

    public static void age(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(Duration.ofDays(1))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
