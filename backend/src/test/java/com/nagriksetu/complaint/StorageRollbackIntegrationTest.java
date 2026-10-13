package com.nagriksetu.complaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nagriksetu.evidence.EvidenceOrphanCleanupProbe;
import com.nagriksetu.evidence.LocalEvidenceStorageService;
import com.nagriksetu.evidence.OrphanEvidenceCleanupJob;
import com.nagriksetu.support.IntegrationTest;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** A storage failure part-way through an upload must leave neither database rows nor orphaned files. */
class StorageRollbackIntegrationTest extends IntegrationTest {

    @MockitoSpyBean
    LocalEvidenceStorageService spyStorage;

    @Autowired ComplaintRepository complaints;
    @Autowired OrphanEvidenceCleanupJob cleanupJob;

    @Test
    void failedUploadRollsBackComplaintAndDeletesStoredFiles() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            if (calls.incrementAndGet() == 2) {
                throw new IOException("disk full");
            }
            return inv.callRealMethod();
        }).when(spyStorage).store(anyString(), any(), anyLong(), anyString());

        mvc.perform(complaintForm(LAT_IN_A, LNG_IN_A,
                        photo("one.png", pngBytes(Color.RED), "image/png"),
                        photo("two.png", pngBytes(Color.BLUE), "image/png"))
                        .header("Authorization", bearer(citizen)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"));

        assertThat(calls.get()).isEqualTo(2);
        assertThat(complaints.count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from evidence", Integer.class)).isZero();
        assertThat(filesIn(spyStorage.root())).isZero();
    }

    @Test
    void cleanupJobRemovesOldFilesWithoutEvidenceRows() throws Exception {
        long id = createComplaint(citizen, true).get("id").asLong();
        String orphan = "complaints/" + id + "/citizen-report/00000000-0000-0000-0000-000000000000.png";
        spyStorage.store(orphan, new ByteArrayInputStream(new byte[] {1, 2}), 2, "image/png");
        Files.setLastModifiedTime(spyStorage.root().resolve(orphan), FileTime.from(Instant.now().minus(Duration.ofDays(1))));
        try (Stream<Path> s = Files.walk(spyStorage.root())) {
            s.filter(Files::isRegularFile).forEach(EvidenceOrphanCleanupProbe::age);
        }

        assertThat(cleanupJob.run()).isEqualTo(1);
        assertThat(spyStorage.exists(orphan)).isFalse();
        assertThat(filesIn(spyStorage.root())).isEqualTo(1); // the real evidence file is kept
    }

    private static long filesIn(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile).count();
        }
    }
}
