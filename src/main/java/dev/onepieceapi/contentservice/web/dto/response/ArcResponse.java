package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;
import java.util.Map;

/**
 * What a version of an arc says - the body of its {@link VersionResponse}.
 *
 * @param saga the saga it belongs to, as it is today; null while not chosen
 * @param translations the title per language code
 * @param description its paragraphs, in order; empty for none
 */
public record ArcResponse(Integer number, NumberedReferenceResponse saga,
		Map<String, TitleTranslationResponse> translations, List<ParagraphResponse> description) {

}
