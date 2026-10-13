package com.nagriksetu.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class WardLocatorTest {

    private final List<double[]> square = List.of(
            new double[] {17.0, 78.0}, new double[] {17.0, 79.0}, new double[] {18.0, 79.0}, new double[] {18.0, 78.0});

    @Test
    void pointInPolygon() {
        assertThat(WardLocator.contains(square, 17.5, 78.5)).isTrue();
        assertThat(WardLocator.contains(square, 16.9, 78.5)).isFalse();
        assertThat(WardLocator.contains(square, 17.5, 79.1)).isFalse();
    }

    @Test
    void concavePolygon() {
        // An L shape: the notch at the top-right is outside.
        List<double[]> l = List.of(new double[] {0, 0}, new double[] {0, 2}, new double[] {1, 2},
                new double[] {1, 1}, new double[] {2, 1}, new double[] {2, 0});
        assertThat(WardLocator.contains(l, 0.5, 1.5)).isTrue();
        assertThat(WardLocator.contains(l, 1.5, 0.5)).isTrue();
        assertThat(WardLocator.contains(l, 1.5, 1.5)).isFalse();
    }
}
