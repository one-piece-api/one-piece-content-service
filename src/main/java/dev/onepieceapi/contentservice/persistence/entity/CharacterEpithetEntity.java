package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
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
 * An epithet of one version of a Character (implementation plan of the Character, D6,
 * D14): a child of {@link CharacterVersionEntity}, saved and removed only through it. Its
 * row has an id of its own, the parent of its translations.
 */
@Entity
@Table(name = "character_version_epithet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterEpithetEntity {

	@Id
	private UUID id;

	/** The text per language code; a language nothing was saved for is absent. */
	@ElementCollection
	@CollectionTable(name = "character_version_epithet_translation", joinColumns = @JoinColumn(name = "epithet_id"))
	@MapKeyColumn(name = "language_code")
	@Column(name = "text")
	private Map<String, String> text = new HashMap<>();

	public CharacterEpithetEntity(Map<String, String> written) {
		this.id = UUID.randomUUID();
		rewrite(written);
	}

	/** Replaces what the epithet says. */
	public void rewrite(Map<String, String> written) {
		this.text.clear();
		this.text.putAll(written);
	}

}
