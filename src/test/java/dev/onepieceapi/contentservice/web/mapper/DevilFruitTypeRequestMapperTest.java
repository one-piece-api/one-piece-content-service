package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategoryTranslation;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeSubcategoryRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeSubcategoryTranslationRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeTranslationRequest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class DevilFruitTypeRequestMapperTest {

	@Test
	void aRequestBecomesWhatItSaysAsTyped() {
		var request = new DevilFruitTypeRequest(" Zoan ",
				Map.of("it", new DevilFruitTypeTranslationRequest("Zoo Zoo", " ", null, null)), null);

		var devilFruitType = DevilFruitTypeRequestMapper.toDomain(request);

		assertThat(devilFruitType.romaji()).isEqualTo(" Zoan ");
		assertThat(devilFruitType.translations())
			.containsExactly(entry("it", new DevilFruitTypeTranslation("Zoo Zoo", " ", null, null)));
	}

	@Test
	void aRequestWithoutTranslationsHasNone() {
		var devilFruitType = DevilFruitTypeRequestMapper.toDomain(new DevilFruitTypeRequest(null, null, null));

		assertThat(devilFruitType.romaji()).isNull();
		assertThat(devilFruitType.translations()).isEmpty();
	}

	@Test
	void aLanguageSentWithNothingInItHasNothingWritten() {
		Map<String, DevilFruitTypeTranslationRequest> translations = new HashMap<>();
		translations.put("en", null);

		var devilFruitType = DevilFruitTypeRequestMapper
			.toDomain(new DevilFruitTypeRequest("Zoan", translations, null));

		assertThat(devilFruitType.translations())
			.containsExactly(entry("en", new DevilFruitTypeTranslation(null, null, null, null)));
	}

	@Test
	void aRequestSaysItsSubcategoriesInOrderAnEmptyOneAsNothingWrittenAndNoneAsAnEmptyList() {
		UUID ancient = UUID.randomUUID();
		var request = new DevilFruitTypeRequest("Zoan", null,
				Arrays.asList(new DevilFruitTypeSubcategoryRequest(ancient,
						Map.of("it", new DevilFruitTypeSubcategoryTranslationRequest("Antico", null))), null));

		var devilFruitType = DevilFruitTypeRequestMapper.toDomain(request);

		assertThat(devilFruitType.subcategories()).containsExactly(
				new DevilFruitTypeSubcategory(ancient,
						Map.of("it", new DevilFruitTypeSubcategoryTranslation("Antico", null))),
				new DevilFruitTypeSubcategory(null, Map.of()));
		assertThat(DevilFruitTypeRequestMapper.toDomain(new DevilFruitTypeRequest(null, null, null)).subcategories())
			.isEmpty();
	}

}
