package dev.onepieceapi.contentservice.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Checks {@link YearsInOrder}: a last year before the first is refused, named on the last
 * year; a missing year is not its to refuse.
 */
public class YearsInOrderValidator implements ConstraintValidator<YearsInOrder, YearSpan> {

	@Override
	public boolean isValid(YearSpan span, ConstraintValidatorContext context) {
		if (span == null || span.yearFrom() == null || span.yearTo() == null || span.yearTo() >= span.yearFrom()) {
			return true;
		}
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
			.addPropertyNode(YearSpan.YEAR_TO)
			.addConstraintViolation();
		return false;
	}

}
