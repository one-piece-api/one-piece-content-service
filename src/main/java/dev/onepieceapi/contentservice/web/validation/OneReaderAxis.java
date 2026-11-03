package dev.onepieceapi.contentservice.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * On a preview request: the reader is at a chapter or at an episode, exactly one of the
 * two (reveals plan R6, D1). Checked by {@link OneReaderAxisValidator}.
 */
@Documented
@Constraint(validatedBy = OneReaderAxisValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OneReaderAxis {

	String message() default "exactly one of asOfChapter and asOfEpisode is required";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
