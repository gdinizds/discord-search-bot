package dev.gdinizds.discordsearchbot.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchFilterTest {

    @Test
    void appliesDefaultsWhenValuesAreNullOrZero() {
        SearchFilter filter = SearchFilter.of("linux kernel", null, null, null, 0);

        assertThat(filter.query()).isEqualTo("linux kernel");
        assertThat(filter.type()).isEqualTo(SearchType.WEB);
        assertThat(filter.language()).isEqualTo("pt-BR");
        assertThat(filter.safeSearch()).isEqualTo(1);
        assertThat(filter.limit()).isEqualTo(10);
    }

    @Test
    void clampsLimitsAndSafeSearchBounds() {
        SearchFilter over = SearchFilter.of("test", SearchType.IMAGE, "en-US", 5, 100);
        assertThat(over.safeSearch()).isEqualTo(2);
        assertThat(over.limit()).isEqualTo(25);

        SearchFilter under = SearchFilter.of("test", SearchType.FILE, "pt-BR", -3, -5);
        assertThat(under.safeSearch()).isEqualTo(0);
        assertThat(under.limit()).isEqualTo(10);
    }
}
