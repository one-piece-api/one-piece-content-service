package dev.onepieceapi.contentservice.web.validation;

import dev.onepieceapi.contentservice.domain.workflow.SortField;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks {@link SortableBy}: every field the request sorts by must be one of the enum
 * given to the annotation.
 */
public class SortableByValidator implements ConstraintValidator<SortableBy, Pageable> {

	private Set<String> sortableFields;

	@Override
	public void initialize(SortableBy constraint) {
		this.sortableFields = new TreeSet<>();
		Arrays.stream(constraint.value().getEnumConstants())
			.map(SortField.class::cast)
			.map(SortField::field)
			.forEach(this.sortableFields::add);
	}

	@Override
	public boolean isValid(Pageable pageable, ConstraintValidatorContext context) {
		if (pageable == null) {
			return true;
		}
		tellWhatIsAllowed(context);
		return pageable.getSort().stream().map(Sort.Order::getProperty).allMatch(this.sortableFields::contains);
	}

	/**
	 * Fills the {fields} placeholder of the message: "can only be sorted by [romaji,
	 * updatedAt]".
	 */
	private void tellWhatIsAllowed(ConstraintValidatorContext context) {
		context.unwrap(HibernateConstraintValidatorContext.class).addMessageParameter("fields", this.sortableFields);
	}

}
