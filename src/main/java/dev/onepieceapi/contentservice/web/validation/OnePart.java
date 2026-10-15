package dev.onepieceapi.contentservice.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * On a tagged union sent as an object with one property per variant, e.g. an event time -
 * {@code {"chapter": ...}} or {@code {"worldDate": ...}}: exactly one of them is written.
 * Checked by {@link OnePartValidator}; a missing union is not its to refuse.
 */
@Documented
@Constraint(validatedBy = OnePartValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnePart {

	String message() default "must have exactly one of {parts}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
