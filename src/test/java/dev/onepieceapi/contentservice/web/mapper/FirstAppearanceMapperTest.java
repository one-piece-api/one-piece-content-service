package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeSubcategoryRequest;
import dev.onepieceapi.contentservice.web.dto.request.FirstAppearanceRequest;
import dev.onepieceapi.contentservice.web.dto.response.NumberedReferenceResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A first appearance between the wire and the domain, for whatever has one. */
class FirstAppearanceMapperTest {

	private static final UUID ROMANCE_DAWN = UUID.fromString("7d3b2a1c-0e9f-4a8b-8c7d-6e5f4a3b2c1d");

	@Test
	void aFirstAppearanceLeftOutOrWithoutAChapterIsNone() {
		assertThat(FirstAppearanceMapper.toDomain(null)).isEqualTo(StoryPoint.NONE);
		assertThat(FirstAppearanceMapper.toDomain(new FirstAppearanceRequest(null, null))).isEqualTo(StoryPoint.NONE);
		assertThat(FirstAppearanceMapper.toDomain(new FirstAppearanceRequest(ROMANCE_DAWN, null)))
			.isEqualTo(StoryPoint.ofChapter(ROMANCE_DAWN));
	}

	@Test
	void aTypeRequestCarriesTheFirstAppearancesOfTheTypeAndOfEachSubcategory() {
		var request = new DevilFruitTypeRequest("Zoan", null,
				List.of(new DevilFruitTypeSubcategoryRequest(null, null,
						new FirstAppearanceRequest(ROMANCE_DAWN, null))),
				new FirstAppearanceRequest(ROMANCE_DAWN, null));

		var type = DevilFruitTypeRequestMapper.toDomain(request);

		assertThat(type.firstAppearance()).isEqualTo(StoryPoint.ofChapter(ROMANCE_DAWN));
		assertThat(type.subcategories().getFirst().firstAppearance()).isEqualTo(StoryPoint.ofChapter(ROMANCE_DAWN));
	}

	@Test
	void noneIsAnsweredAsNothingAndAChapterAsItIsTodayOrByItsIdAlone() {
		var today = Map.of(ROMANCE_DAWN, new NumberedReference(ROMANCE_DAWN, 1, Map.of("it", "Romance Dawn")));

		assertThat(FirstAppearanceMapper.toResponse(StoryPoint.NONE, today)).isNull();
		assertThat(FirstAppearanceMapper.toResponse(StoryPoint.ofChapter(ROMANCE_DAWN), today).chapter())
			.isEqualTo(new NumberedReferenceResponse(ROMANCE_DAWN, 1, Map.of("it", "Romance Dawn")));
		assertThat(FirstAppearanceMapper.toResponse(StoryPoint.ofChapter(ROMANCE_DAWN), Map.of()).chapter().id())
			.isEqualTo(ROMANCE_DAWN);
	}

}
