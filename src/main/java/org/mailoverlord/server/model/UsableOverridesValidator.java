package org.mailoverlord.server.model;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates {@link UsableOverrides}.
 *
 * <p>An override that names nothing used to reach {@code getOverrideToAddresses().split(",")} and
 * throw a {@link NullPointerException}, which the controller's handler reported as a 200 saying
 * only that the release had failed. That is the bug this constraint exists to make
 * unrepresentable: see #13.
 *
 * <p>Parsing rather than pattern-matching is deliberate. This accepts exactly what the service
 * accepts, so an address that used to be delivered is not now refused by a stricter rule living
 * in the wrong layer, and an address that could never have been delivered is refused before any
 * message goes out rather than once per id after delivery has begun.
 */
public class UsableOverridesValidator implements ConstraintValidator<UsableOverrides, MessageReleaseRequest> {

    @Override
    public boolean isValid(MessageReleaseRequest request, ConstraintValidatorContext context) {
        boolean valid = true;
        context.disableDefaultConstraintViolation();

        if (request.isOverrideTo()) {
            valid &= reject(context, "overrideToAddresses", request.getOverrideToAddresses());
        }
        if (request.isOverrideFrom()) {
            valid &= reject(context, "overrideFromAddress", request.getOverrideFromAddress());
        }
        return valid;
    }

    /**
     * Reports why the addresses cannot be used, or that they can.
     *
     * @return {@code true} when the list is usable
     */
    private static boolean reject(ConstraintValidatorContext context, String field, String addresses) {
        if (addresses == null || addresses.isBlank()) {
            context.buildConstraintViolationWithTemplate(
                            field + " must name at least one address when "
                                    + flagOf(field) + " is true.")
                    .addPropertyNode(field)
                    .addConstraintViolation();
            return false;
        }
        for (String address : addresses.split(",")) {
            if (!isParseable(address)) {
                context.buildConstraintViolationWithTemplate(
                                field + " contains an address that cannot be parsed: \""
                                        + address.trim() + "\".")
                        .addPropertyNode(field)
                        .addConstraintViolation();
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the address can be turned into an {@link InternetAddress}.
     *
     * <p>Strict is off because the service parses the same way; strict parsing rejects a bare
     * {@code user@host}, which is a legitimate address for a local relay.
     */
    private static boolean isParseable(String address) {
        try {
            new InternetAddress(address.trim(), false);
            return true;
        } catch (AddressException e) {
            return false;
        }
    }

    private static String flagOf(String field) {
        return "overrideToAddresses".equals(field) ? "overrideTo" : "overrideFrom";
    }
}