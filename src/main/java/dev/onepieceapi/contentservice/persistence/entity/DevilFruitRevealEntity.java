package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitField;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A reveal of one version of a Devil Fruit (workflow 3.7.1): see {@link RevealEntity}.
 */
@Entity
@Table(name = "devil_fruit_version_reveal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DevilFruitRevealEntity extends RevealEntity {

	@ElementCollection
	@CollectionTable(name = "devil_fruit_version_reveal_field", joinColumns = @JoinColumn(name = "reveal_id"))
	@Column(name = "field")
	@Enumerated(EnumType.STRING)
	private Set<DevilFruitField> fields = EnumSet.noneOf(DevilFruitField.class);

	/** The values learned; null when not part of it. */
	private String romaji;

	private UUID typeContentId;

	private UUID subcategoryId;

	/** The title and the name learned per language code. */
	@ElementCollection
	@CollectionTable(name = "devil_fruit_version_reveal_translation", joinColumns = @JoinColumn(name = "reveal_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, RevealTranslationEmbeddable> translations = new HashMap<>();

	public DevilFruitRevealEntity(Written written) {
		super(UUID.randomUUID());
		rewrite(written);
	}

	/** Replaces what the reveal says. */
	public void rewrite(Written written) {
		rewritePoint(written.point());
		this.fields.clear();
		this.fields.addAll(written.fields());
		this.romaji = written.romaji();
		this.typeContentId = written.typeContentId();
		this.subcategoryId = written.subcategoryId();
		this.translations.clear();
		this.translations.putAll(written.translations());
	}

	/** What a reveal row says, as {@link #rewrite} writes it. */
	@Builder
	public record Written(FirstAppearanceIds point, Set<DevilFruitField> fields, String romaji, UUID typeContentId,
			UUID subcategoryId, Map<String, RevealTranslationEmbeddable> translations) {
	}

}
