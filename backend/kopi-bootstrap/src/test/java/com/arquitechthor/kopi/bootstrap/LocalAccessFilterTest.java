package com.arquitechthor.kopi.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/// Only this machine's browser (ADR-016).
class LocalAccessFilterTest {

    private final LocalAccessFilter filter = new LocalAccessFilter(Set.of("localhost", "127.0.0.1", "[::1]"));

    private static MockHttpServletRequest request(String method, String host) {
        var request = new MockHttpServletRequest(method, "/games");
        if (host != null) {
            request.addHeader("Host", host);
        }
        return request;
    }

    @Nested
    class TheHost {

        @Test
        void localNamesAreAnsweredOnAnyPort() {
            for (var host : new String[] {"localhost:8080", "127.0.0.1:8090", "[::1]:8080", "LOCALHOST", "localhost"}) {
                assertThat(filter.refusal(request("GET", host))).as(host).isEmpty();
            }
        }

        @Test
        void aRebindingDomainCannotReadAnything() {
            // evil.example now resolves to 127.0.0.1, but the browser still says whose page it is.
            assertThat(filter.refusal(request("GET", "evil.example:8080"))).hasValue("Host evil.example:8080");
        }

        @Test
        void withoutAHostHeaderTheServerNameDecides() {
            var local = request("GET", null);
            var remote = request("GET", null);
            remote.setServerName("192.168.1.20");

            assertThat(filter.refusal(local)).isEmpty();
            assertThat(filter.refusal(remote)).hasValue("Host 192.168.1.20");
        }
    }

    @Nested
    class AChange {

        @Test
        void fromAKopiPageGoesThrough() {
            var post = request("POST", "localhost:8080");
            post.addHeader("Origin", "http://localhost:8080");
            post.addHeader("Sec-Fetch-Site", "same-origin");

            assertThat(filter.refusal(post)).isEmpty();
        }

        @Test
        void fromAnotherSiteIsRefused() {
            var post = request("POST", "localhost:8080");
            post.addHeader("Origin", "https://evil.example");

            assertThat(filter.refusal(post)).hasValue("Origin https://evil.example");
        }

        @Test
        void fromASandboxedPageOrAFileIsRefused() {
            var delete = request("DELETE", "localhost:8080");
            delete.addHeader("Origin", "null");

            assertThat(filter.refusal(delete)).isPresent();
        }

        @Test
        void withoutOriginTheFetchMetadataDecides() {
            var crossSite = request("POST", "localhost:8080");
            crossSite.addHeader("Sec-Fetch-Site", "cross-site");
            var typedByHand = request("POST", "localhost:8080");
            typedByHand.addHeader("Sec-Fetch-Site", "none");

            assertThat(filter.refusal(crossSite)).hasValue("Sec-Fetch-Site cross-site");
            assertThat(filter.refusal(typedByHand)).isEmpty();
        }

        @Test
        void withNeitherHeaderItIsNotABrowserAndGoesThrough() {
            // curl, a script: what the README shows for the API.
            assertThat(filter.refusal(request("DELETE", "localhost:8080"))).isEmpty();
        }
    }

    @Test
    void aReadFromAnotherSiteIsLeftToTheBrowser() {
        // Without CORS headers the browser does not let that page read the answer.
        var get = request("GET", "localhost:8080");
        get.addHeader("Origin", "https://evil.example");

        assertThat(filter.refusal(get)).isEmpty();
    }

    @Test
    void aRefusedRequestIsA403AndGoesNoFurther() throws Exception {
        var response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request("GET", "evil.example"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(((MockFilterChain) chain).getRequest()).isNull();
    }

    @Test
    void readsHostAndOriginNames() {
        assertThat(LocalAccessFilter.hostName("[::1]:8080")).isEqualTo("[::1]");
        assertThat(LocalAccessFilter.hostName("Localhost:8080")).isEqualTo("localhost");
        assertThat(LocalAccessFilter.originHost("http://[::1]:8080")).isEqualTo("[::1]");
        assertThat(LocalAccessFilter.originHost("null")).isNull();
        assertThat(LocalAccessFilter.originHost("not a url")).isNull();
    }
}
