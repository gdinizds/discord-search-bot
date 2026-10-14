package dev.gdinizds.discordsearchbot.adapter.out.s3;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageFormatTest {

    @Test
    void sniffsPng() {
        byte[] data = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertThat(ImageFormat.sniff(data)).contains(ImageFormat.PNG);
    }

    @Test
    void sniffsJpeg() {
        byte[] data = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
        assertThat(ImageFormat.sniff(data)).contains(ImageFormat.JPEG);
    }

    @Test
    void sniffsGif() {
        byte[] data = new byte[]{'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, 0, 0};
        assertThat(ImageFormat.sniff(data)).contains(ImageFormat.GIF);
    }

    @Test
    void sniffsWebp() {
        byte[] data = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertThat(ImageFormat.sniff(data)).contains(ImageFormat.WEBP);
    }

    @Test
    void returnsEmptyForInvalidOrShortData() {
        assertThat(ImageFormat.sniff(null)).isEmpty();
        assertThat(ImageFormat.sniff(new byte[]{1, 2, 3})).isEmpty();
        assertThat(ImageFormat.sniff(new byte[20])).isEmpty();
    }
}
