package dev.onepieceapi.contentservice.persistence;

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
public class TranslationId implements Serializable {

	private UUID workingRevisionId;

	private String languageCode;

	public TranslationId(UUID workingRevisionId, String languageCode) {
		this.workingRevisionId = workingRevisionId;
		this.languageCode = languageCode;
	}

}
