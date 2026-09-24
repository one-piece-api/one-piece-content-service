package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One (content version, language) pair's snapshotted {@code name}/{@code description}.
 */
@Entity
@Table(name = "content_version_translation")
@Getter
@NoArgsConstructor
public class ContentVersionTranslationEntity {

	@EmbeddedId
	private ContentVersionTranslationId id;

	private String name;

	private String description;

	public ContentVersionTranslationEntity(UUID contentVersionId, String languageCode, String name,
			String description) {
		this.id = new ContentVersionTranslationId(contentVersionId, languageCode);
		this.name = name;
		this.description = description;
	}

}
