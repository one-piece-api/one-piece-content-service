package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitField;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitKnowledge;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitKnowledgeRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitListRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitRevealRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTranslationRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** From the requests about Devil Fruits to the domain. */
@UtilityClass
public class DevilFruitRequestMapper {

	/** The relation of a fruit to its type, as {@code ContentFilter} names it. */
	private static final String TYPE_RELATION = "type";

	/** What the request says, as typed: tidying it up is left to the domain. */
	public DevilFruit toDomain(DevilFruitRequest request) {
		Map<String, DevilFruitTranslation> translations = new TreeMap<>();
		Optional.ofNullable(request.translations())
			.orElseGet(Map::of)
			.forEach((language, translation) -> translations.put(language, toDomain(translation)));
		return DevilFruit.builder()
			.romaji(request.romaji())
			.typeContentId(request.type())
			.subcategoryId(request.subcategory())
			.translations(translations)
			.firstAppearance(FirstAppearanceMapper.toDomain(request.firstAppearance()))
			.multipleUsers(request.allowsMultipleUsers())
			.description(ParagraphRequestMapper.toDomain(request.description()))
			.advantages(ParagraphRequestMapper.toDomain(request.advantages()))
			.disadvantages(ParagraphRequestMapper.toDomain(request.disadvantages()))
			.initialKnowledge(Optional.ofNullable(request.initialKnowledge())
				.map(DevilFruitRequestMapper::toDomain)
				.orElse(DevilFruitKnowledge.NONE))
			.reveals(RevealRequestMapper.each(request.reveals(), DevilFruitRequestMapper::toDomain))
			.build();
	}

	private static DevilFruitKnowledge toDomain(DevilFruitKnowledgeRequest knowledge) {
		return new DevilFruitKnowledge(knowledge.romaji(), knowledge.type(), knowledge.subcategory(),
				RevealRequestMapper.names(knowledge.translations(), DevilFruitTranslationRequest::name));
	}

	private static Reveal<DevilFruitField, DevilFruitKnowledge> toDomain(DevilFruitRevealRequest reveal) {
		var values = new DevilFruitKnowledge(reveal.romaji(), reveal.type(), reveal.subcategory(),
				RevealRequestMapper.names(reveal.translations(), DevilFruitTranslationRequest::name));
		return RevealRequestMapper.toDomain(reveal, values);
	}

	/**
	 * What the save does to the draft's image (implementation plan of the Devil Fruit,
	 * D5): replaced by an upload, removed when asked, kept otherwise; both at once is
	 * refused.
	 * @param upload the file sent with the request; null or empty for none
	 */
	public ImageChange toImageChange(DevilFruitRequest request, MultipartFile upload) {
		return ImageRequestMapper.toImageChange(request.removesImage(), upload);
	}

	/** The common filters, then the type when the list is narrowed to one. */
	public ContentFilter toFilter(DevilFruitListRequest request) {
		var filter = new ContentFilter(request.status(), request.q(), request.author(), request.updatedWithinDays());
		return request.type() == null ? filter : filter.relatedTo(TYPE_RELATION, request.type());
	}

	/** A language sent with nothing in it is a language with nothing written. */
	private static DevilFruitTranslation toDomain(DevilFruitTranslationRequest translation) {
		return translation == null ? new DevilFruitTranslation(null) : new DevilFruitTranslation(translation.name());
	}

}
