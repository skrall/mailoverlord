package org.mailoverlord.server.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The rules {@link UsableOverrides} exists to enforce.
 *
 * <p>Checked through a real validator rather than by calling the validator directly, because what
 * matters is that the annotations on the DTO are wired up and are reached: a constraint that is
 * declared but never applied to the request validates nothing and reports nothing, which is
 * indistinguishable from having no validation at all unless a test asks the bean.
 */
class UsableOverridesTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    @Test
    void anOverrideWithNoAddressIsRejected() {
        MessageReleaseRequest request = releaseRequest();
        request.setOverrideTo(true);

        assertThat(messagesOf(validator.validate(request)))
                .containsExactly("overrideToAddresses must name at least one address when "
                        + "overrideTo is true.");
    }

    @Test
    void anOverrideFromWithNoAddressIsRejected() {
        MessageReleaseRequest request = releaseRequest();
        request.setOverrideFrom(true);

        assertThat(messagesOf(validator.validate(request)))
                .containsExactly("overrideFromAddress must name at least one address when "
                        + "overrideFrom is true.");
    }

    /**
     * Blank rather than null, so it took a different route to the same failure.
     */
    @Test
    void anOverrideWithABlankAddressIsRejected() {
        MessageReleaseRequest request = releaseRequest();
        request.setOverrideTo(true);
        request.setOverrideToAddresses("   ");

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void anOverrideWithAnUnparseableAddressIsRejected() {
        MessageReleaseRequest request = releaseRequest();
        request.setOverrideTo(true);
        request.setOverrideToAddresses("not an address");

        assertThat(messagesOf(validator.validate(request)))
                .containsExactly("overrideToAddresses contains an address that cannot be parsed: "
                        + "\"not an address\".");
    }

    @Test
    void anOverrideWithAUsableAddressIsAccepted() {
        MessageReleaseRequest request = releaseRequest();
        request.setOverrideTo(true);
        request.setOverrideToAddresses("someone@example.com, other@example.com");

        assertThat(validator.validate(request)).isEmpty();
    }

    /**
     * The flag is what makes the address meaningful. A caller that fills the field in and then
     * turns the override off is not asking for anything, so refusing them would break a client
     * for no reason.
     */
    @Test
    void anUnusedAddressIsNotValidated() {
        MessageReleaseRequest request = releaseRequest();
        request.setOverrideTo(false);
        request.setOverrideToAddresses("not an address");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void messageIdsMustBePresent() {
        assertThat(messagesOf(validator.validate(new MessageReleaseRequest())))
                .containsExactly("messageIds must contain at least one message id.");
    }

    @Test
    void messageIdsAreCapped() {
        MessageReleaseRequest request = new MessageReleaseRequest();
        for (long id = 0; id <= MessageDeleteRequest.MAX_IDS_PER_REQUEST; id++) {
            request.addMessageId(id);
        }

        assertThat(messagesOf(validator.validate(request)))
                .containsExactly("messageIds must contain at most "
                        + MessageDeleteRequest.MAX_IDS_PER_REQUEST + " message ids.");
    }

    @Test
    void aBatchExactlyAtTheCapIsAccepted() {
        MessageDeleteRequest request = new MessageDeleteRequest();
        for (long id = 0; id < MessageDeleteRequest.MAX_IDS_PER_REQUEST; id++) {
            request.addMessageId(id);
        }

        assertThat(validator.validate(request)).isEmpty();
    }

    /**
     * A request that is otherwise valid, so a test about the overrides is not also asserting
     * something about {@code messageIds}.
     */
    private static MessageReleaseRequest releaseRequest() {
        MessageReleaseRequest request = new MessageReleaseRequest();
        request.addMessageId(1L);
        return request;
    }

    private static Set<String> messagesOf(Set<ConstraintViolation<MessageReleaseRequest>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }
}