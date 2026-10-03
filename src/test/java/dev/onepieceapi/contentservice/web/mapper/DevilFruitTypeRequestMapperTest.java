package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeTranslationRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class DevilFruitTypeRequestMapperTest {

	@Test
	void aRequestBecomesWhatItSaysAsTyped() {
		var request = new DevilFruitTypeRequest(" Zoan ",
				Map.of("it", new DevilFruitTypeTranslationRequest("Zoo Zoo", " ")));

		var devilFruitType = DevilFruitTypeRequestMapper.toDomain(request);

		assertThat(devilFruitType.romaji()).isEqualTo(" Zoan ");
		assertThat(devilFruitType.translations())
			.containsExactly(entry("it", new DevilFruitTypeTranslation("Zoo Zoo", " ")));
	}

	@Test
	void aRequestWithoutTranslationsHasNone() {
		var devilFruitType = DevilFruitTypeRequestMapper.toDomain(new DevilFruitTypeRequest(null, null));

		assertThat(devilFruitType.romaji()).isNull();
		assertThat(devilFruitType.translations()).isEmpty();
	}

	@Test
	void aLanguageSentWithNothingInItHasNothingWritten() {
		Map<String, DevilFruitTypeTranslationRequest> translations = new HashMap<>();
		translations.put("en", null);

		var devilFruitType = DevilFruitTypeRequestMapper.toDomain(new DevilFruitTypeRequest("Zoan", translations));

		assertThat(devilFruitType.translations())
			.containsExactly(entry("en", new DevilFruitTypeTranslation(null, null)));
	}

}
