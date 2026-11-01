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
import java.util.UUID;

/**
 * What one version of an arc says (implementation plan of the story structure, S2, S4):
 * its number (its natural key), the content of its saga and, per language, its title, and
 * its paragraphs. The entity type of the link is a constant the database fills in: it
 * keeps an arc from pointing to anything but a saga.
 */
@Entity
@Table(name = "arc_version")
@Getter
@NoArgsConstructor
@FieldNameConstants
public class ArcVersionEntity extends VersionBodyEntity {

	/** The natural key of the entity; null while not written. */
	private Integer number;

	/** The saga's content, not one of its versions; null while not chosen. */
	private UUID sagaContentId;

	/**
	 * The localized fields per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "arc_version_translation", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, TitleTranslationEmbeddable> translations = new HashMap<>();

	/**
	 * Its paragraphs, every section in one list (workflow 3.7.3): a section is in the
	 * order of its rows; the position is the index in the list.
	 */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "version_id", nullable = false)
	@OrderColumn(name = "position")
	private List<ArcParagraphEntity> paragraphs = new ArrayList<>();

	public ArcVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	@Override
	public NaturalKey naturalKey() {
		return new NaturalKey(Fields.number, this.number);
	}

	/** Replaces everything the version says, and notes when. */
	public void rewrite(Integer newNumber, UUID newSagaContentId,
			Map<String, TitleTranslationEmbeddable> newTranslations, List<ParagraphFields> newParagraphs, Instant now) {
		this.number = newNumber;
		this.sagaContentId = newSagaContentId;
		this.translations.clear();
		this.translations.putAll(newTranslations);
		PositionalRows.rewrite(this.paragraphs, newParagraphs, ArcParagraphEntity::new, ParagraphEntity::rewrite);
		touch(now);
	}

}
