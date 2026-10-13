package com.nagriksetu.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nagriksetu.config.NagrikProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitFilterTest {

    @Test
    void blocksAfterLimitWithinWindow() {
        NagrikProperties props = new NagrikProperties(
                new NagrikProperties.Security("x".repeat(40), 60, java.util.List.of(), 600, new NagrikProperties.RateLimit(3, 3, 3)),
                null, null, null, null);
        RateLimitFilter filter = new RateLimitFilter(props, new ObjectMapper());
        assertThat(filter.tryAcquire("k", 3, Duration.ofMinutes(1))).isTrue();
        assertThat(filter.tryAcquire("k", 3, Duration.ofMinutes(1))).isTrue();
        assertThat(filter.tryAcquire("k", 3, Duration.ofMinutes(1))).isTrue();
        assertThat(filter.tryAcquire("k", 3, Duration.ofMinutes(1))).isFalse();
        assertThat(filter.tryAcquire("other", 3, Duration.ofMinutes(1))).isTrue();
    }
}
