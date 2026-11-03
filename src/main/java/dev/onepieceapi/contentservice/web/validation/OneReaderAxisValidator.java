package dev.onepieceapi.contentservice.web.validation;

import dev.onepieceapi.contentservice.web.dto.request.PreviewRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Checks {@link OneReaderAxis}. The violation names {@code asOfChapter}, so the error
 * contract lists it with the other field violations.
 */
public class OneReaderAxisValidator implements ConstraintValidator<OneReaderAxis, PreviewRequest> {

	private static final String FIELD = "asOfChapter";

	@Override
	public boolean isValid(PreviewRequest request, ConstraintValidatorContext context) {
		if (request == null || (request.asOfChapter() == null) != (request.asOfEpisode() == null)) {
			return true;
		}
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
			.addPropertyNode(FIELD)
			.addConstraintViolation();
		return false;
	}

}
