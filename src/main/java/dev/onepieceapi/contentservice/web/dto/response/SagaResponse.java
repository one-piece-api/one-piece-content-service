package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;
import java.util.Map;

/**
 * What a version of a saga says - the body of its {@link VersionResponse}.
 *
 * @param translations the title per language code
 * @param description its paragraphs, in order; empty for none
 */
public record SagaResponse(Integer number, Map<String, TitleTranslationResponse> translations,
		List<ParagraphResponse> description) {

}
