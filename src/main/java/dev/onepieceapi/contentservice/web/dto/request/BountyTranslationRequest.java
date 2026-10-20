package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.bounty.BountyTranslation;
import jakarta.validation.constraints.Size;

/** The localized fields of a bounty in one language, as a draft is saved. */
public record BountyTranslationRequest(@Size(max = BountyTranslation.DESCRIPTION_MAX_LENGTH) String description) {

}
