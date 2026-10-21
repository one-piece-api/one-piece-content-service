package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import lombok.Builder;

import java.util.Map;
import java.util.UUID;

/**
 * Everything a bounty version says, as its row is written - what
 * {@link BountyVersionEntity#rewrite} replaces it with.
 */
@Builder
public record BountyFields(String posterName, Long amount, BountyCondition condition, boolean inactive,
		UUID previousContentId, UUID holderContentId, Map<String, BountyTranslationEmbeddable> translations,
		String imageId, FirstAppearanceIds firstAppearance) {

}
