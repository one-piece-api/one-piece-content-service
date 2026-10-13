package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * The title and description of a story version in one language - a value owned by its
 * version, e.g. {@link SagaVersionEntity#getTranslations()}. The title is mapped as
 * {@code name}: the attribute the generic list queries search and sort by and the
 * uniqueness check reads (see {@link VersionBodyEntity}), stored in column {@code title}.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class StoryTranslationEmbeddable {

	/** The title. */
	@Column(name = "title")
	private String name;

	private String description;

}
