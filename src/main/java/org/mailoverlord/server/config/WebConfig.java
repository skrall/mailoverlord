package org.mailoverlord.server.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.SortHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC config.
 *
 * <p>Controllers here take {@code Pageable} and {@code Sort} arguments, but Spring MVC
 * does not pick up {@code HandlerMethodArgumentResolver} beans on its own. Spring Data
 * REST already builds a resolver that respects the {@code spring.data.web.*} properties,
 * so it is simply added to the MVC argument resolvers. Declaring
 * {@code @EnableSpringDataWebSupport} instead would clash with the equivalent bean that
 * Spring Data REST defines, and would fail to start.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PageableHandlerMethodArgumentResolver pageableResolver;
    private final SortHandlerMethodArgumentResolver sortResolver;

    public WebConfig(PageableHandlerMethodArgumentResolver pageableResolver,
                     SortHandlerMethodArgumentResolver sortResolver) {
        this.pageableResolver = pageableResolver;
        this.sortResolver = sortResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(pageableResolver);
        resolvers.add(sortResolver);
    }
}
