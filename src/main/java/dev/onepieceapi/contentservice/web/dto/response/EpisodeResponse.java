package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;
import java.util.Map;

/**
 * What a version of an episode says - the body of its {@link VersionResponse}.
 *
 * @param arc the arc it belongs to, as it is today; null for none
 * @param broadcasts the broadcast day per country, ordered by date
 * @param translations title and description per language code
 */
public record EpisodeResponse(Integer number, NumberedReferenceResponse arc, String japaneseTitle,
		List<ReleaseResponse> broadcasts, Map<String, StoryTranslationResponse> translations) {

}
