package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * What one version of a saga says (implementation plan of the story structure, S2): its
 * number (its natural key) and, per language, its title and description.
 */
@Entity
@Table(name = "saga_version")
@Getter
@NoArgsConstructor
@FieldNameConstants
public class SagaVersionEntity extends VersionBodyEntity {

	/** The natural key of the entity; null while not written. */
	private Integer number;

	/**
	 * The localized fields per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "saga_version_translation", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, StoryTranslationEmbeddable> translations = new HashMap<>();

	public SagaVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	@Override
	public NaturalKey naturalKey() {
		return new NaturalKey(Fields.number, this.number);
	}

	/** Replaces everything the version says, and notes when. */
	public void rewrite(Integer newNumber, Map<String, StoryTranslationEmbeddable> newTranslations, Instant now) {
		this.number = newNumber;
		this.translations.clear();
		this.translations.putAll(newTranslations);
		touch(now);
	}

}
