package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * What a draft of an Affiliation is saved with - everything the version says, in place of
 * what it said. Any part may be missing: a draft is saved incomplete. Lengths are the
 * only limit checked here; whether a value is free to use, and whether the first
 * appearance may be linked, is for the service to say.
 *
 * @param translations name and description per language code; a language left out, or
 * with nothing written in it, has no translation
 * @param firstAppearance the chapter and/or the episode the group is first shown or named
 * in; left out for none, which a draft may be - it is required to submit
 * @param removeImage true to remove the draft's emblem; not together with an uploaded
 * one. Left out, the emblem is kept - or replaced, when one is uploaded with the request
 */
public record AffiliationRequest(@Size(max = Affiliation.ROMAJI_MAX_LENGTH) String romaji,
		Map<String, @Valid AffiliationTranslationRequest> translations, Boolean removeImage,
		FirstAppearanceRequest firstAppearance, AffiliationKind kind) {

	/** Whether the emblem is to be removed; not saying so keeps it. */
	public boolean removesImage() {
		return Boolean.TRUE.equals(this.removeImage);
	}

}
