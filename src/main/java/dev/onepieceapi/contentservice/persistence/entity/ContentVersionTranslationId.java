package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
public class ContentVersionTranslationId implements Serializable {

	private UUID contentVersionId;

	private String languageCode;

	public ContentVersionTranslationId(UUID contentVersionId, String languageCode) {
		this.contentVersionId = contentVersionId;
		this.languageCode = languageCode;
	}

}
