package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A reveal of one version of a Devil Fruit (ADR-0010): a child of
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

	private String romaji;

	/** The content of the type believed; null when not part of it. */
	private UUID typeContentId;

	private UUID subcategoryId;

	/**
	 * What was believed per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "devil_fruit_version_reveal_translation", joinColumns = @JoinColumn(name = "reveal_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, TranslationEmbeddable> translations = new HashMap<>();

	public DevilFruitRevealEntity(Written written) {
		this.id = UUID.randomUUID();
		rewrite(written);
	}

	/** Replaces what the reveal says. */
	public void rewrite(Written written) {
		this.revealedAtChapterId = written.revealedAtChapterId();
		this.romaji = written.romaji();
		this.typeContentId = written.typeContentId();
		this.subcategoryId = written.subcategoryId();
		this.translations.clear();
		this.translations.putAll(written.translations());
	}

	/** What a reveal says, as a version is written. */
	public record Written(UUID revealedAtChapterId, String romaji, UUID typeContentId, UUID subcategoryId,
			Map<String, TranslationEmbeddable> translations) {
	}

}
