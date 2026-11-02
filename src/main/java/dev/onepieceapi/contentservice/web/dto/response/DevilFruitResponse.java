package dev.onepieceapi.contentservice.web.dto.response;

import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * What a version of a Devil Fruit says - the body of its {@link VersionResponse}.
 *
 * @param type the Devil Fruit Type it belongs to, as it is today; null while not chosen
 * @param subcategory the subcategory of that type it names; null for none
 * @param multipleUsers whether several characters may use it at the same time
 * @param translations the name per language code
 * @param image its image; null for none
 * @param firstAppearance the chapter the reader first meets it in; null for none
 * @param description its paragraphs, in order; empty for none
 * @param advantages as {@code description}
 * @param disadvantages as {@code description}
 * @param initialKnowledge what the reader believes at the first appearance (workflow
 * 3.7.1); null for nothing
 * @param reveals what the reader learns later, in order; empty for none
 */
@Builder
public record DevilFruitResponse(String romaji, TypeReferenceResponse type, SubcategoryReferenceResponse subcategory,
		boolean multipleUsers, Map<String, DevilFruitTranslationResponse> translations, ImageResponse image,
		FirstAppearanceResponse firstAppearance, List<ParagraphResponse> description,
		List<ParagraphResponse> advantages, List<ParagraphResponse> disadvantages,
		DevilFruitKnowledgeResponse initialKnowledge, List<DevilFruitRevealResponse> reveals) {

}
