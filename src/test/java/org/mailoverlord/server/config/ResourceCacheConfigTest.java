package org.mailoverlord.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mailoverlord.server.AbstractMailoverlordIntegrationTest;

/**
 * Cache header tests for the built UI.
 *
 * <p>Without an explicit policy the browser derives a freshness lifetime from
 * {@code Last-Modified}, which the Boot Maven plugin pins to 1980 for reproducible builds. That
 * heuristic runs to roughly five years, so a plain reload keeps serving the previous build. See
 * #45.
 *
 * <p>The two kinds of resource get opposite policies, because the entry document names the
 * current asset hashes and so has to be revalidated, while the hashed assets cannot change under
 * a name the document already refers to. A regression in either half shows up as a stale UI in
 * the browser rather than as a failing request, which is why the headers are pinned here.
 */
class ResourceCacheConfigTest extends AbstractMailoverlordIntegrationTest {

    private static final String HASHED_ASSET_CACHE_CONTROL = "max-age=31536000, public, immutable";

    @Test
    void entryDocumentIsNotCached() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    /**
     * {@code /} reaches the same document through Boot's welcome page forward, so it inherits the
     * policy above. MockMvc records the forward instead of performing it and returns an empty
     * body, which is why the headers themselves are only asserted on {@code /index.html}.
     *
     * <p>Asserting the forward target still guards the wiring: if the welcome page ever pointed
     * somewhere else, {@code /} would quietly stop being covered by the policy.
     */
    @Test
    void rootForwardsToTheCachedEntryDocument() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    /**
     * Read from the built document rather than named literally, since the names are content
     * hashes and change with every UI edit. Naming them here made this fail on any change to the
     * UI, which is a test that cries wolf about the very thing it is meant to protect.
     */
    @Test
    void hashedAssetsAreCachedIndefinitely() throws Exception {
        for (String reference : assetReferences()) {
            mockMvc.perform(get(reference))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", HASHED_ASSET_CACHE_CONTROL));
        }
    }

    @Test
    void missingAssetStillReportsNotFound() throws Exception {
        mockMvc.perform(get("/assets/not-a-real-file.js"))
                .andExpect(status().isNotFound());
    }

    /**
     * {@code immutable} is only safe because Vite content-hashes everything under
     * {@code assets/}. If a build ever emitted a stable filename there, the browser would keep
     * the old file for a year and the change would never reach anyone.
     */
    @Test
    void everyAssetTheDocumentReferencesIsContentHashed() throws IOException {
        assertThat(assetReferences())
                .as("references in index.html")
                .isNotEmpty()
                .allSatisfy(reference -> assertThat(reference)
                        .as("%s must carry a content hash to be safe to cache for a year",
                                reference)
                        .matches("^/assets/[^/]+-[\\w-]{8,}\\.(?:js|css)$"));
    }

    /**
     * Every local asset the built entry document points at, taken from the document itself.
     */
    private static List<String> assetReferences() throws IOException {
        Matcher matcher = Pattern.compile("(?:src|href)=\"(/[^\"]+)\"").matcher(readIndexHtml());
        List<String> references = new ArrayList<>();
        matcher.results().forEach(result -> references.add(result.group(1)));
        return references;
    }

    private static String readIndexHtml() throws IOException {
        try (InputStream in = ResourceCacheConfigTest.class
                .getResourceAsStream("/static/index.html")) {
            assertThat(in).as("/static/index.html on the test classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}