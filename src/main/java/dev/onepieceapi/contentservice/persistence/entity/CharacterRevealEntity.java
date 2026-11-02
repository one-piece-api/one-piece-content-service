package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.character.CharacterField;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
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

/** A reveal of one version of a Character (workflow 3.7.1): see {@link RevealEntity}. */
@Entity
@Table(name = "character_version_reveal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterRevealEntity extends RevealEntity {

	@ElementCollection
	@CollectionTable(name = "character_version_reveal_field", joinColumns = @JoinColumn(name = "reveal_id"))
	@Column(name = "field")
	@Enumerated(EnumType.STRING)
	private Set<CharacterField> fields = EnumSet.noneOf(CharacterField.class);

	/** The values learned; null when not part of it. */
	private String romaji;

	private String japaneseName;

	@Enumerated(EnumType.STRING)
	private CharacterStatus status;

	/** The title and the name learned per language code. */
	@ElementCollection
	@CollectionTable(name = "character_version_reveal_translation", joinColumns = @JoinColumn(name = "reveal_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, RevealTranslationEmbeddable> translations = new HashMap<>();

	public CharacterRevealEntity(Written written) {
		super(UUID.randomUUID());
		rewrite(written);
	}

	/** Replaces what the reveal says. */
	public void rewrite(Written written) {
		rewritePoint(written.point());
		this.fields.clear();
		this.fields.addAll(written.fields());
		this.romaji = written.romaji();
		this.japaneseName = written.japaneseName();
		this.status = written.status();
		this.translations.clear();
		this.translations.putAll(written.translations());
	}

	/** What a reveal row says, as {@link #rewrite} writes it. */
	@Builder
	public record Written(FirstAppearanceIds point, Set<CharacterField> fields, String romaji, String japaneseName,
			CharacterStatus status, Map<String, RevealTranslationEmbeddable> translations) {
	}

}
