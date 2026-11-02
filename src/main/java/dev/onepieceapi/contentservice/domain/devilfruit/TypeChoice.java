package dev.onepieceapi.contentservice.domain.devilfruit;

import java.util.Map;
import java.util.UUID;

/**
 * A Devil Fruit Type, and maybe one of its subcategories, that a fruit holds outside its
 * current value: in its initial knowledge or in a reveal (workflow 3.7.1). It obeys the
 * rules of the current type - linkable on save, online to go online - and is reported
 * where it is.
 *
 * @param field where it is in a request, e.g. {@code reveals[2].}; the prefix of its
 * fields
 * @param part what names where it is in the detail of a block, e.g. its
 * {@code revealIndex}
 * @param typeContentId the content of the type; null when not held
 * @param subcategoryId one subcategory of that type; null for none
 */
public record TypeChoice(String field, Map<String, Object> part, UUID typeContentId, UUID subcategoryId) {

	public TypeChoice(String field, Map<String, Object> part, DevilFruitKnowledge values) {
		this(field, part, values.typeContentId(), values.subcategoryId());
	}

	/** Whether it names a type or a subcategory. */
	public boolean isHeld() {
		return this.typeContentId != null || this.subcategoryId != null;
	}

}
