package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;
import java.util.Map;

/**
 * What a version of a volume says - the body of its {@link VersionResponse}.
 *
 * @param releases the release day per country, ordered by date
 * @param cover its cover; null for none
 * @param translations the title per language code
 */
public record VolumeResponse(Integer number, String japaneseTitle, List<ReleaseResponse> releases, ImageResponse cover,
		Map<String, VolumeTranslationResponse> translations) {

}
