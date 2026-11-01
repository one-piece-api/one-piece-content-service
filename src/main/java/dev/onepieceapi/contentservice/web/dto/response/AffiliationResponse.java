package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;

import java.util.Map;

/**
 * What a version of an Affiliation says - the body of its {@link VersionResponse}.
 *
 * @param translations the name per language code
 * @param image its emblem; null for none
 * @param firstAppearance the chapter and/or the episode it first appears in; null for
 * none
 * @param kind what sort of group it is; null while not chosen
 * @param description its paragraphs, in order; empty for none
 */
public record AffiliationResponse(String romaji, Map<String, AffiliationTranslationResponse> translations,
		ImageResponse image, FirstAppearanceResponse firstAppearance, AffiliationKind kind,
		List<ParagraphResponse> description) {

}
