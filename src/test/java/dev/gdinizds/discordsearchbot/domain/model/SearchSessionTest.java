package dev.gdinizds.discordsearchbot.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchSessionTest {

    @Test
    void navigatesIndicesCorrectly() {
        List<HitItem> items = List.of(
                new HitItem("T1", "http://1", "S1", null, null, "google", null, SearchType.WEB),
                new HitItem("T2", "http://2", "S2", null, null, "bing", null, SearchType.WEB),
                new HitItem("T3", "http://3", "S3", null, null, "duckduckgo", null, SearchType.WEB)
        );

        Instant now = Instant.now();
        SearchSession session = SearchSession.create(
                "s1", "c1", "u1", "g1", "ch1", "m1", "tok1", "query", SearchType.WEB, items, now, 3600
        );

        assertThat(session.currentIndex()).isEqualTo(0);
        assertThat(session.hasPrevious()).isFalse();
        assertThat(session.hasNext()).isTrue();
        assertThat(session.previousIndex()).isEqualTo(0);
        assertThat(session.nextIndex()).isEqualTo(1);
        assertThat(session.currentItem().title()).isEqualTo("T1");

        SearchSession nextSession = session.withIndex(session.nextIndex(), now);
        assertThat(nextSession.currentIndex()).isEqualTo(1);
        assertThat(nextSession.hasPrevious()).isTrue();
        assertThat(nextSession.hasNext()).isTrue();
        assertThat(nextSession.currentItem().title()).isEqualTo("T2");

        SearchSession lastSession = nextSession.withIndex(nextSession.nextIndex(), now);
        assertThat(lastSession.currentIndex()).isEqualTo(2);
        assertThat(lastSession.hasPrevious()).isTrue();
        assertThat(lastSession.hasNext()).isFalse();
        assertThat(lastSession.nextIndex()).isEqualTo(2);

        int rand = lastSession.randomIndex();
        assertThat(rand).isBetween(0, 2);
    }

    @Test
    void singleItemSessionDisablesNavigation() {
        List<HitItem> items = List.of(
                new HitItem("T1", "http://1", "S1", null, null, "google", null, SearchType.WEB)
        );
        SearchSession session = SearchSession.create(
                "s1", "c1", "u1", "g1", "ch1", "m1", "tok1", "query", SearchType.WEB, items, Instant.now(), 3600
        );

        assertThat(session.hasPrevious()).isFalse();
        assertThat(session.hasNext()).isFalse();
        assertThat(session.randomIndex()).isEqualTo(0);
    }
}
