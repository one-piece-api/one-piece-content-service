package dev.onepieceapi.contentservice.persistence;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One (working revision, language) pair's {@code name}/{@code description} - absent for a
 * language the author hasn't saved anything for yet, per
 * docs/user-flows/authentication-and-user-management.md 3.2.
 */
@Entity
@Table(name = "devil_fruit_type_translation")
@Getter
@NoArgsConstructor
public class TranslationEntity {

	@EmbeddedId
	private TranslationId id;

	@Setter
	private String name;

	@Setter
	private String description;

	public TranslationEntity(UUID workingRevisionId, String languageCode, String name, String description) {
		this.id = new TranslationId(workingRevisionId, languageCode);
		this.name = name;
		this.description = description;
	}

}
