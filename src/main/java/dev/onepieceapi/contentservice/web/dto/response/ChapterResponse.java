package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;
import java.util.Map;

/**
 * What a version of a chapter says - the body of its {@link VersionResponse}.
 *
 * @param arc the arc it belongs to, as it is today; null while not chosen
 * @param volume the volume that collects it, as it is today; null for none
 * @param releases the release day per country, ordered by date
 * @param translations title and description per language code
 */
public record ChapterResponse(Integer number, NumberedReferenceResponse arc, NumberedReferenceResponse volume,
		String japaneseTitle, Integer pageCount, List<ReleaseResponse> releases,
		Map<String, StoryTranslationResponse> translations) {

}
