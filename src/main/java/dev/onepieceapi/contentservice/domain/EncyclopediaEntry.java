package dev.onepieceapi.contentservice.domain;

/**
 * "Enciclopedia" (Step 5, extended Step 8): one entry, from any of the three states it
 * can come from - a `REVIEWED` working revision awaiting publish, an item's live
 * published version, or a retired item's last published version before its live pointer
 * was cleared. The service assembles instances of it; the web layer's
 * {@code DevilFruitTypeResponseMapper} maps each variant to its response shape, same as
 * every other list endpoint.
 */
public sealed interface EncyclopediaEntry {

	record ReviewedCandidate(WorkingRevision revision) implements EncyclopediaEntry {
	}

	record PublishedItem(ContentVersion version) implements EncyclopediaEntry {
	}

	/**
	 * UF-CNT-10: an item whose live pointer is currently clear, shown with the content of
	 * the last version that was live before it was retired.
	 */
	record RetiredItem(ContentVersion lastVersion) implements EncyclopediaEntry {
	}

}
