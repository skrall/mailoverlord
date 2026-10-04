package org.mailoverlord.server.config;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cache headers for the built UI.
 *
 * <p>Spring Boot serves resources under {@code static/} with no {@code Cache-Control} at all,
 * because nothing sets {@code spring.web.resources.cache}. That leaves the browser to invent a
 * freshness lifetime from {@code Last-Modified}, which RFC 9111 permits when a response has no
 * explicit expiry. The Boot Maven plugin pins resource timestamps to 1980 to keep builds
 * reproducible, and browsers take 10% of the age as the heuristic, so the entry document works
 * out as fresh for about five years. A plain reload then never picks up a new build. See #45.
 *
 * <p>Adding an {@code ETag} would not fix that: without {@code Cache-Control} the heuristic is
 * still computed from {@code Last-Modified}, so the browser skips the conditional request anyway.
 * Only an explicit policy settles it.
 *
 * <p>The two kinds of resource need opposite treatment. Vite content-hashes the filenames under
 * {@code assets/}, so a change of contents always means a change of name and those responses can
 * be kept indefinitely. The entry document is the exception: it names the current hashes, so it
 * has to be revalidated on every load, or it goes on pointing at assets that the latest build
 * has already deleted.
 *
 * <p>A single {@code spring.web.resources.cache.cachecontrol.*} setting cannot express that split,
 * since it applies to every static resource, which is why these are registered by hand. The
 * patterns are deliberately narrower than the {@code /**} handler Boot registers, so this
 * configuration only overrides the two paths it has an opinion about and Boot's own handler
 * continues to serve everything else.
 */
@Configuration
public class ResourceCacheConfig implements WebMvcConfigurer {

    private static final Duration HASHED_ASSET_MAX_AGE = Duration.ofDays(365);

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(HASHED_ASSET_MAX_AGE)
                        .cachePublic()
                        .immutable());
        registry.addResourceHandler("/index.html")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache());
    }
}