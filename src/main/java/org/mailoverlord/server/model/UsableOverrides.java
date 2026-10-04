package org.mailoverlord.server.model;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Checks that a {@link MessageReleaseRequest} asks to substitute recipients or a sender only when
 * it also says what to substitute them with.
 *
 * <p>A field constraint cannot do this: the rule is about two fields together, one flag and one
 * value. {@code @NotBlank} on the addresses alone would reject a caller that leaves them unset
 * because it is not overriding anything, and {@code @AssertTrue} on a method would report the
 * violation against the name of that method rather than against the field that is wrong.
 *
 * <p>Each failure is reported against the offending field, so a caller is told which address to
 * fix rather than that something about the request was untrue.
 *
 * @see UsableOverridesValidator
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UsableOverridesValidator.class)
public @interface UsableOverrides {

    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}