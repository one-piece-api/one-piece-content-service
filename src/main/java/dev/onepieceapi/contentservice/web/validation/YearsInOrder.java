package dev.onepieceapi.contentservice.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * On a span of years, e.g. a world date: its last year is not before its first. Reported
 * on the last year. Checked by {@link YearsInOrderValidator}.
 */
@Documented
@Constraint(validatedBy = YearsInOrderValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface YearsInOrder {

	String message() default "must not be before yearFrom";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
