package dev.onepieceapi.contentservice.web.validation;

import dev.onepieceapi.contentservice.domain.workflow.SortField;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * On a {@code Pageable} parameter: the request may sort only by the fields of the given
 * enum, e.g. {@code @SortableBy(DevilFruitTypeSortField.class)}. Anything else is refused
 * as a validation failure before the controller method runs. Checked by
 * {@link SortableByValidator}.
 */
@Documented
@Constraint(validatedBy = SortableByValidator.class)
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SortableBy {

	/** The enum listing the sortable fields. */
	Class<? extends Enum<? extends SortField>> value();

	String message() default "can only be sorted by {fields}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
