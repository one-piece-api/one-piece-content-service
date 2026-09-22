package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.exception.NotFoundException;

import java.util.UUID;

/**
 * Raised when an item has nothing to show in the encyclopedia - it doesn't exist, or it
 * exists but has neither an active reviewed candidate nor ever been published. Same
 * "don't distinguish missing from not-yet-visible" isolation stance as
 * {@link WorkingRevisionNotFoundException}: `content:read` only ever exposes
 * `REVIEWED`/`PUBLISHED`/`RETIRED` content (4.1), never a hint that a `DRAFT`-only item
 * exists.
 */
public class EncyclopediaItemNotFoundException extends NotFoundException {

	public EncyclopediaItemNotFoundException(UUID itemId) {
		super(ContentErrorCode.ENCYCLOPEDIA_ITEM_NOT_FOUND, "No encyclopedia entry for item " + itemId);
	}

}
