package org.mailoverlord.server.config;

import org.mailoverlord.server.model.MessageDetail;
import org.mailoverlord.server.model.MessagePart;
import org.mailoverlord.server.model.MessageResponse;
import org.mailoverlord.server.model.MessageSummary;
import org.mailoverlord.server.model.PageResponse;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Runtime hints needed to serialise the JSON API from a native image.
 *
 * <p>Jackson reads and writes these types reflectively, and a native image only keeps members
 * it knows are reachable. The response types are records, so Jackson needs both the accessors
 * and the canonical constructor to be registered; without them the API answers with an empty
 * object or fails outright.
 */
public class ApiModelRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Types the JSON API returns, which Jackson must be able to introspect.
     */
    private static final Class<?>[] API_TYPES = {
            MessageSummary.class,
            MessageDetail.class,
            MessagePart.class,
            PageResponse.class,
            MessageResponse.class
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (Class<?> type : API_TYPES) {
            hints.reflection().registerTypeIfPresent(classLoader, type.getName(),
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
    }
}
