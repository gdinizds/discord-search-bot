package dev.gdinizds.discordsearchbot.adapter.out.s3;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeImageDownloaderTest {

    @Test
    void validatesPublicAndPrivateIpAddresses() throws Exception {
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("127.0.0.1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("10.0.0.1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("192.168.1.1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("172.16.0.1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("169.254.169.254"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("::1"))).isFalse();

        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("1.1.1.1"))).isTrue();
    }

    @Test
    void rejectsDisallowedSchemesAndPorts() {
        var downloader = new SafeImageDownloader(java.time.Duration.ofSeconds(1), 1024, "test-bot");

        assertThatThrownBy(() -> downloader.download("ftp://example.com/image.png"))
                .isInstanceOf(SafeImageDownloader.ImageRejectedException.class);

        assertThatThrownBy(() -> downloader.download("http://example.com:8080/image.png"))
                .isInstanceOf(SafeImageDownloader.ImageRejectedException.class);
    }
}
