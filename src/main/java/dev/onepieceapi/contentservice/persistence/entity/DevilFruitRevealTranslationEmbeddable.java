package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * What a reveal says in one language (ADR-0010): the fields believed before the reveal,
 * as {@link TranslationEmbeddable} has them, and the title of the reveal itself (T10) -
 * which is not a believed value, so the shared embeddable does not carry it.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DevilFruitRevealTranslationEmbeddable {

	private String name;

	private String description;

	private String advantages;

	private String disadvantages;

	private String title;

}
