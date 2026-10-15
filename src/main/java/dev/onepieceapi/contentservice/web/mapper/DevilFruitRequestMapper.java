package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitPhase;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitListRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitPhaseRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTranslationRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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
		List<DevilFruitPhase> phases = Optional.ofNullable(request.phases())
			.orElseGet(List::of)
			.stream()
			.map(DevilFruitRequestMapper::toDomain)
			.toList();
		return new DevilFruit(request.romaji(), request.type(), request.subcategory(), toDomain(request.translations()),
				null, FirstAppearanceMapper.toDomain(request.firstAppearance()), phases);
	}

	/** A phase sent as null holds nothing, which validation then names. */
	private static DevilFruitPhase toDomain(DevilFruitPhaseRequest phase) {
		if (phase == null) {
			return new DevilFruitPhase(StoryPoint.NONE, null, null, null, Map.of());
		}
		StoryPoint supersededAt = phase.supersededAt() == null ? StoryPoint.NONE
				: new StoryPoint(phase.supersededAt().chapter());
		return new DevilFruitPhase(supersededAt, phase.romaji(), phase.type(), phase.subcategory(),
				toDomain(phase.translations()));
	}

	private static Map<String, DevilFruitTranslation> toDomain(Map<String, DevilFruitTranslationRequest> requested) {
		Map<String, DevilFruitTranslation> translations = new TreeMap<>();
		Optional.ofNullable(requested)
			.orElseGet(Map::of)
			.forEach((language, translation) -> translations.put(language, toDomain(translation)));
		return translations;
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
		return translation == null ? new DevilFruitTranslation(null, null, null, null) : new DevilFruitTranslation(
				translation.name(), translation.description(), translation.advantages(), translation.disadvantages());
	}

}
