package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * What a draft of a bounty is saved with - everything the version says, in place of what
 * it said. Any part may be missing: a draft is saved incomplete. Lengths and the sign of
 * the amount are the only limits checked here; whether the poster's name and the amount
 * are free to use together, and whether the links may be made, is for the service to say.
 *
 * @param posterName the name printed on the poster
 * @param amount the Berry, as plain digits; 0 or more
 * @param inactive revoked, suspended or its holder dead; left out for false
 * @param previous the id of the content of the bounty this one replaces; whether it can
 * be chosen is answered by {@code GET /bounties/linkable}; left out for none
 * @param holder the id of the content of the character the poster is about; whether it
 * can be chosen is answered by {@code GET /characters/linkable}; left out for none
 * @param translations the description per language code; a language left out, or with
 * nothing written in it, has no translation
 * @param firstAppearance where the poster is revealed; left out for none, which a draft
 * may be - it is required to go online
 * @param removeImage true to remove the draft's poster; not together with an uploaded
 * one. Left out, the poster is kept - or replaced, when one is uploaded with the request
 */
public record BountyRequest(@Size(max = Bounty.POSTER_NAME_MAX_LENGTH) String posterName,
		@PositiveOrZero @Schema(description = "Berry, as plain digits") Long amount, BountyCondition condition,
		Boolean inactive, @Schema(description = "The content id of the bounty it replaces") UUID previous,
		@Schema(description = "The content id of the character the poster is about") UUID holder,
		Map<String, @Valid BountyTranslationRequest> translations, Boolean removeImage,
		FirstAppearanceRequest firstAppearance) {

	/** Whether the bounty is marked inactive; not saying so is not. */
	public boolean markedInactive() {
		return Boolean.TRUE.equals(this.inactive);
	}

	/** Whether the poster is to be removed; not saying so keeps it. */
	public boolean removesImage() {
		return Boolean.TRUE.equals(this.removeImage);
	}

}
