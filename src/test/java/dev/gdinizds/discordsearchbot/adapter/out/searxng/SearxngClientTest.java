package dev.gdinizds.discordsearchbot.adapter.out.searxng;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.gdinizds.discordsearchbot.config.Resilience;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearxngClientTest {

    private final WireMockServer wireMock = new WireMockServer(options().dynamicPort());
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private SearxngClient client;

    @BeforeEach
    void setUp() {
        wireMock.start();
        var circuitBreakers = CircuitBreakerRegistry.ofDefaults();
        circuitBreakers.circuitBreaker("searxng", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        var timeLimiters = TimeLimiterRegistry.ofDefaults();
        timeLimiters.timeLimiter("searxng", TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build());
        var resilience = new Resilience(circuitBreakers, RetryRegistry.ofDefaults(), timeLimiters,
                BulkheadRegistry.ofDefaults(), executor);
        client = new SearxngClient(RestClient.builder(), JsonMapper.builder().build(), resilience,
                wireMock.baseUrl(), Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
        executor.shutdownNow();
    }

    @Test
    void searchWebParsesResultsAndLimits() {
        wireMock.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"results":[
                          {"title":"Result 1","url":"https://res1.org","content":"Desc 1","engine":"google"},
                          {"title":"Result 2","url":"https://res2.org","content":"Desc 2","engine":"bing"},
                          {"title":"Result 3","url":"https://res3.org","content":"Desc 3","engine":"duckduckgo"}]}
                        """)));

        var results = client.searchWeb("spring boot", "pt-BR", 1, 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).title()).isEqualTo("Result 1");
        assertThat(results.get(0).url()).isEqualTo("https://res1.org");
        assertThat(results.get(1).url()).isEqualTo("https://res2.org");

        wireMock.verify(getRequestedFor(urlPathEqualTo("/search"))
                .withQueryParam("q", equalTo("spring boot"))
                .withQueryParam("categories", equalTo("general"))
                .withQueryParam("format", equalTo("json"))
                .withQueryParam("language", equalTo("pt-BR"))
                .withQueryParam("safesearch", equalTo("1")));
    }

    @Test
    void searchImagesParsesImagesAndFiltersInvalidUrls() {
        wireMock.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"results":[
                          {"title":"Dog","url":"https://site.org/dog","img_src":"https://images.org/dog.jpg","engine":"google images","resolution":"1024x768"},
                          {"title":"Invalid","url":"https://site.org/inv","img_src":""},
                          {"title":"Relative","url":"https://site.org/rel","img_src":"/rel/img.jpg"},
                          {"title":"Cat","url":"https://site.org/cat","img_src":"https://images.org/cat.png","engine":"bing images","resolution":"800x600"}]}
                        """)));

        var images = client.searchImages("animals", "pt-BR", 1, 5);

        assertThat(images).hasSize(2);
        assertThat(images.get(0).imageUrl()).isEqualTo("https://images.org/dog.jpg");
        assertThat(images.get(1).imageUrl()).isEqualTo("https://images.org/cat.png");
    }

    @Test
    void circuitBreakerOpensOnRepeatedFailures() {
        wireMock.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> client.searchWeb("fail", "pt-BR", 1, 5))
                    .isNotInstanceOf(CallNotPermittedException.class);
        }

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> client.searchWeb("fail", "pt-BR", 1, 5))
                    .isInstanceOf(CallNotPermittedException.class);
        }

        wireMock.verify(5, getRequestedFor(urlPathEqualTo("/search")));
    }
}
