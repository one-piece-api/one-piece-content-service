package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionTranslationEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEntity;
import dev.onepieceapi.contentservice.persistence.entity.WorkingRevisionEntity;

import java.util.List;

/**
 * "Enciclopedia" (Step 5, extended Step 8): one entry, from any of the three entity kinds
 * it can come from - a `REVIEWED` working revision awaiting publish, an item's live
 * published version, or a retired item's last published version before its live pointer
 * was cleared. Kept in {@code service} (not {@code web}) so the service layer never has
 * to depend on {@code web} to build response DTOs itself - the controller maps each
 * variant with {@code DevilFruitTypeResponseMapper}, same as every other list endpoint.
 */
public sealed interface EncyclopediaEntry {

	record ReviewedCandidate(WorkingRevisionEntity revision,
			List<TranslationEntity> translations) implements EncyclopediaEntry {
	}

	record PublishedItem(ContentVersionEntity version,
			List<ContentVersionTranslationEntity> translations) implements EncyclopediaEntry {
	}

	/**
	 * UF-CNT-10: an item whose live pointer is currently clear, shown with the content of
	 * the last version that was live before it was retired.
	 */
	record RetiredItem(ContentVersionEntity lastVersion,
			List<ContentVersionTranslationEntity> translations) implements EncyclopediaEntry {
	}

}
