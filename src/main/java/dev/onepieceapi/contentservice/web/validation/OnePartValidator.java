package dev.onepieceapi.contentservice.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

/** Checks {@link OnePart}: a union with none or both of its variants is refused. */
public class OnePartValidator implements ConstraintValidator<OnePart, TaggedUnion> {

	@Override
	public boolean isValid(TaggedUnion union, ConstraintValidatorContext context) {
		if (union == null) {
			return true;
		}
		context.unwrap(HibernateConstraintValidatorContext.class).addMessageParameter("parts", union.partNames());
		return union.writtenParts() == 1;
	}

}
