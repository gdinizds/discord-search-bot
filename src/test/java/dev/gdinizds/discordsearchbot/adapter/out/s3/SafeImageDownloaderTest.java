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
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("100.64.0.1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("192.0.2.1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("198.51.100.1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("203.0.113.1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("::1"))).isFalse();

        // IPv4-compatible and IPv4-mapped IPv6 addresses
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("::127.0.0.1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("::10.0.0.1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("::192.168.1.1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("::169.254.169.254"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("0:0:0:0:0:0:7f00:1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("::ffff:127.0.0.1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("::ffff:10.0.0.1"))).isFalse();

        // IPv6 reserved ranges
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("fe80::1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("fc00::1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("fd00::1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("2001:db8::1"))).isFalse();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("100::1"))).isFalse();

        // Public IPs
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("1.1.1.1"))).isTrue();
        assertThat(SafeImageDownloader.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
    }

    @Test
    void rejectsDisallowedSchemesAndPorts() {
        var downloader = new SafeImageDownloader(java.time.Duration.ofSeconds(1), 1024, "test-bot");

        assertThatThrownBy(() -> downloader.download("ftp://example.com/image.png"))
                .isInstanceOf(SafeImageDownloader.ImageRejectedException.class);

        assertThatThrownBy(() -> downloader.download("http://example.com:8080/image.png"))
                .isInstanceOf(SafeImageDownloader.ImageRejectedException.class);
    }

    @Test
    void rejectsInternalHostnames() {
        var downloader = new SafeImageDownloader(java.time.Duration.ofSeconds(1), 1024, "test-bot");

        assertThatThrownBy(() -> downloader.download("http://localhost/image.png"))
                .isInstanceOf(SafeImageDownloader.ImageRejectedException.class);

        assertThatThrownBy(() -> downloader.download("http://consul-server.consul.svc.cluster.local/image.png"))
                .isInstanceOf(SafeImageDownloader.ImageRejectedException.class);

        assertThatThrownBy(() -> downloader.download("http://vault.internal/image.png"))
                .isInstanceOf(SafeImageDownloader.ImageRejectedException.class);
    }
}
