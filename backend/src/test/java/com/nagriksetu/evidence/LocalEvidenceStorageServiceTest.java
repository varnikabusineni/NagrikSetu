package com.nagriksetu.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalEvidenceStorageServiceTest {

    @TempDir
    Path dir;

    @Test
    void storedFilesSurviveARestart() throws Exception {
        byte[] data = "fake image bytes".getBytes();
        new LocalEvidenceStorageService(dir).store("complaints/1/citizen-report/abc.png", new ByteArrayInputStream(data), data.length, "image/png");

        // A new instance over the same directory simulates the backend restarting.
        LocalEvidenceStorageService afterRestart = new LocalEvidenceStorageService(dir);
        assertThat(afterRestart.exists("complaints/1/citizen-report/abc.png")).isTrue();
        EvidenceStorageService.StoredObject object = afterRestart.open("complaints/1/citizen-report/abc.png");
        try (InputStream in = object.content()) {
            assertThat(in.readAllBytes()).isEqualTo(data);
        }
        assertThat(object.sizeBytes()).isEqualTo(data.length);
    }

    @Test
    void leavesNoTemporaryFilesBehind() throws Exception {
        LocalEvidenceStorageService storage = new LocalEvidenceStorageService(dir);
        storage.store("complaints/2/resolution/x.jpg", new ByteArrayInputStream(new byte[] {1, 2, 3}), 3, "image/jpeg");
        try (Stream<Path> files = Files.walk(dir)) {
            assertThat(files.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).toList()).containsExactly("x.jpg");
        }
    }

    @Test
    void rejectsPathTraversalAndUnsafeKeys() throws Exception {
        LocalEvidenceStorageService storage = new LocalEvidenceStorageService(dir);
        for (String key : List.of("../outside.png", "complaints/../../outside.png", "/etc/passwd", "complaints//x.png",
                "Complaints/X.PNG", "complaints/1/", "", "..", "complaints/1/a.png\u0000.txt", "c:\\windows\\x.png")) {
            assertThatThrownBy(() -> storage.store(key, new ByteArrayInputStream(new byte[] {1}), 1, "image/png"))
                    .as(key).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.open(key)).as(key).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(Files.exists(dir.getParent().resolve("outside.png"))).isFalse();
    }

    @Test
    void deletesAndListsOldKeys() throws Exception {
        LocalEvidenceStorageService storage = new LocalEvidenceStorageService(dir);
        storage.store("complaints/3/citizen-report/old.png", new ByteArrayInputStream(new byte[] {1}), 1, "image/png");
        storage.store("complaints/3/citizen-report/new.png", new ByteArrayInputStream(new byte[] {1}), 1, "image/png");
        Files.setLastModifiedTime(dir.resolve("complaints/3/citizen-report/old.png"),
                FileTime.from(Instant.now().minus(Duration.ofHours(3))));

        List<String> old = new ArrayList<>();
        storage.listKeysOlderThan(Instant.now().minus(Duration.ofHours(1)), old::add);
        assertThat(old).containsExactly("complaints/3/citizen-report/old.png");

        storage.delete("complaints/3/citizen-report/old.png");
        assertThat(storage.exists("complaints/3/citizen-report/old.png")).isFalse();
        assertThat(storage.exists("complaints/3/citizen-report/new.png")).isTrue();
    }
}
