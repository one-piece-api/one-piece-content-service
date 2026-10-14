package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * The title of a volume version in one language - a value owned by its version. Mapped as
 * {@code name}, like {@link StoryTranslationEmbeddable}, so the generic queries read it;
 * stored in column {@code title}. A volume has no description.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class VolumeTranslationEmbeddable {

	/** The title. */
	@Column(name = "title")
	private String name;

}
