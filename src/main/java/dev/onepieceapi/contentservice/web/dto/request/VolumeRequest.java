package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * What a draft of a volume is saved with - everything the version says, in place of what
 * it said. Any part may be missing: a draft is saved incomplete. Whether the number is
 * free to use is for the service to say.
 *
 * @param number 0 or more, its public address
 * @param japaneseTitle the title as published in Japan, one for every language; optional
 * @param releases the release day per country; optional
 * @param translations the title per language code; a language left out, or with nothing
 * written in it, has no translation
 * @param removeImage true to remove the cover of the draft; not together with an uploaded
 * one. Left out, the cover is kept - or replaced, when one is uploaded with the request
 */
public record VolumeRequest(@PositiveOrZero Integer number,
		@Size(max = StoryTranslation.JAPANESE_TITLE_MAX_LENGTH) String japaneseTitle,
		List<@Valid ReleaseRequest> releases, Map<String, @Valid VolumeTranslationRequest> translations,
		Boolean removeImage) {

	/** Whether the cover is to be removed; not saying so keeps it. */
	public boolean removesImage() {
		return Boolean.TRUE.equals(this.removeImage);
	}

}
