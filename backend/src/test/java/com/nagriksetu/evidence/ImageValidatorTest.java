package com.nagriksetu.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageValidatorTest {

    @Test
    void detectsFormatsFromMagicBytes() {
        assertThat(ImageValidator.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 12))
                .isEqualTo("image/jpeg");
        assertThat(ImageValidator.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0}, 12))
                .isEqualTo("image/png");
        assertThat(ImageValidator.detect(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}, 12))
                .isEqualTo("image/webp");
        assertThat(ImageValidator.detect("GIF89a......".getBytes(), 12)).isNull();
        assertThat(ImageValidator.detect("<svg xmlns=".getBytes(), 11)).isNull();
        assertThat(ImageValidator.detect(new byte[] {(byte) 0xFF}, 1)).isNull();
    }

    @Test
    void sanitisesFilenames() {
        assertThat(ImageValidator.sanitiseFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(ImageValidator.sanitiseFilename("C:\\Users\\me\\photo 1.JPG")).isEqualTo("photo 1.JPG");
        assertThat(ImageValidator.sanitiseFilename("<img onerror=x>.png")).isEqualTo("_img onerror_x_.png");
        assertThat(ImageValidator.sanitiseFilename("...hidden")).isEqualTo("hidden");
        assertThat(ImageValidator.sanitiseFilename(null)).isEqualTo("photo");
        assertThat(ImageValidator.sanitiseFilename("////")).isEqualTo("photo");
    }
}
