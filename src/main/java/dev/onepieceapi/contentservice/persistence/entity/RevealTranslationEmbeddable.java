package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * What a reveal says in one language (V40): its title and the name learned there, each
 * null when not written.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RevealTranslationEmbeddable {

	private String title;

	private String name;

}
