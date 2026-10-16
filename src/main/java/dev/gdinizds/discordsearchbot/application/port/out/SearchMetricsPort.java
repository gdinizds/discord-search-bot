package dev.gdinizds.discordsearchbot.application.port.out;

import java.time.Duration;

public interface SearchMetricsPort {

    void recordRequest(String type, String outcome);

    void recordLatency(String type, Duration duration);

    void recordSearxngQuery(String category, String outcome);

    void recordImageStored(String outcome);

    void recordNavigationClick(String action, String outcome);

    SearchMetricsPort NOOP = new SearchMetricsPort() {
        @Override
        public void recordRequest(String type, String outcome) {}

        @Override
        public void recordLatency(String type, Duration duration) {}

        @Override
        public void recordSearxngQuery(String category, String outcome) {}

        @Override
        public void recordImageStored(String outcome) {}

        @Override
        public void recordNavigationClick(String action, String outcome) {}
    };
}
