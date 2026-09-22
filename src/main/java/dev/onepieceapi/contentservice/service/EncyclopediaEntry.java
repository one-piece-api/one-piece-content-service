package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.persistence.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.ContentVersionTranslationEntity;
import dev.onepieceapi.contentservice.persistence.TranslationEntity;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionEntity;

import java.util.List;

/**
 * "Enciclopedia" (Step 5): one entry, from either of the two entity kinds it can come
 * from - a `REVIEWED` working revision awaiting publish, or an item's live published
 * version. Kept in {@code service} (not {@code web}) so the service layer never has to
 * depend on {@code web} to build response DTOs itself - the controller maps each variant
 * with {@code DevilFruitTypeResponseMapper}, same as every other list endpoint.
 */
public sealed interface EncyclopediaEntry {

	record ReviewedCandidate(WorkingRevisionEntity revision,
			List<TranslationEntity> translations) implements EncyclopediaEntry {
	}

	record PublishedItem(ContentVersionEntity version,
			List<ContentVersionTranslationEntity> translations) implements EncyclopediaEntry {
	}

}
