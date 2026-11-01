package dev.onepieceapi.contentservice.persistence.entity;

import java.util.List;
import java.util.ArrayList;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
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
 * number (its natural key) and, per language, its title, and its paragraphs.
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
	private Map<String, TitleTranslationEmbeddable> translations = new HashMap<>();

	/**
	 * Its paragraphs, every section in one list (workflow 3.7.3): a section is in the
	 * order of its rows; the position is the index in the list.
	 */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "version_id", nullable = false)
	@OrderColumn(name = "position")
	private List<SagaParagraphEntity> paragraphs = new ArrayList<>();

	public SagaVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	@Override
	public NaturalKey naturalKey() {
		return new NaturalKey(Fields.number, this.number);
	}

	/** Replaces everything the version says, and notes when. */
	public void rewrite(Integer newNumber, Map<String, TitleTranslationEmbeddable> newTranslations,
			List<ParagraphFields> newParagraphs, Instant now) {
		this.number = newNumber;
		this.translations.clear();
		this.translations.putAll(newTranslations);
		PositionalRows.rewrite(this.paragraphs, newParagraphs, SagaParagraphEntity::new, ParagraphEntity::rewrite);
		touch(now);
	}

}
