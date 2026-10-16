package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitField;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A reveal of one version of a Devil Fruit (ADR-0010, T13): a child of
 * {@link DevilFruitVersionEntity}, saved and removed only through it. A reveal has no
 * identity across versions: its row is the one at its position.
 */
@Entity
@Table(name = "devil_fruit_version_reveal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DevilFruitRevealEntity {

	@Id
	private UUID id;

	/** The content of the chapter it happens in; null while not chosen. */
	private UUID revealedAtChapterId;

	/** The fields the reader learns there (T13). */
	@ElementCollection
	@CollectionTable(name = "devil_fruit_version_reveal_field", joinColumns = @JoinColumn(name = "reveal_id"))
	@Column(name = "field")
	@Enumerated(EnumType.STRING)
	private Set<DevilFruitField> fields = EnumSet.noneOf(DevilFruitField.class);

	/** The values learned, for the fields not yet the truth; null when not part of it. */
	private String romaji;

	/** The content of the type learned; null when not part of it. */
	private UUID typeContentId;

	private UUID subcategoryId;

	/**
	 * What is learned per language code, with the title of the reveal; a language nothing
	 * was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "devil_fruit_version_reveal_translation", joinColumns = @JoinColumn(name = "reveal_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, DevilFruitRevealTranslationEmbeddable> translations = new HashMap<>();

	public DevilFruitRevealEntity(Written written) {
		this.id = UUID.randomUUID();
		rewrite(written);
	}

	/** Replaces what the reveal says. */
	public void rewrite(Written written) {
		this.revealedAtChapterId = written.revealedAtChapterId();
		this.fields.clear();
		this.fields.addAll(written.fields());
		this.romaji = written.romaji();
		this.typeContentId = written.typeContentId();
		this.subcategoryId = written.subcategoryId();
		this.translations.clear();
		this.translations.putAll(written.translations());
	}

	/** What a reveal says, as a version is written. */
	public record Written(UUID revealedAtChapterId, Set<DevilFruitField> fields, String romaji, UUID typeContentId,
			UUID subcategoryId, Map<String, DevilFruitRevealTranslationEmbeddable> translations) {
	}

}
