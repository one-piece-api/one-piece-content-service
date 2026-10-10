package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.config.ImageProperties;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.devilfruit.SubcategoryReference;
import dev.onepieceapi.contentservice.domain.devilfruit.TypeReference;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitListRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTranslationRequest;
import dev.onepieceapi.contentservice.web.dto.response.SubcategoryReferenceResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** From a Devil Fruit request to the domain, and from the domain to its responses. */
class DevilFruitMappersTest {

	private static final UUID CONTENT_ID = UUID.fromString("9c1d2e3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f");

	private static final UUID TYPE_ID = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final User NAMI = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private static final TypeReference PARAMECIA = new TypeReference(TYPE_ID, "Paramecia", Map.of("it", "Paramisha"));

	@Test
	void aRequestBecomesWhatItSaysAsTypedWithItsType() {
		var request = new DevilFruitRequest(" Gomu Gomu ", TYPE_ID, null,
				Map.of("it", new DevilFruitTranslationRequest(" Gomu ", null, "Pro", null)), null);

		var fruit = DevilFruitRequestMapper.toDomain(request);

		assertThat(fruit.romaji()).isEqualTo(" Gomu Gomu ");
		assertThat(fruit.typeContentId()).isEqualTo(TYPE_ID);
		assertThat(fruit.translations())
			.containsExactly(entry("it", new DevilFruitTranslation(" Gomu ", null, "Pro", null)));
	}

	@Test
	void aRequestWithNothingInItIsAFruitWithNothingWritten() {
		Map<String, DevilFruitTranslationRequest> translations = new HashMap<>();
		translations.put("it", null);

		var fruit = DevilFruitRequestMapper.toDomain(new DevilFruitRequest(null, null, null, translations, null));
		var bare = DevilFruitRequestMapper.toDomain(new DevilFruitRequest(null, null, null, null, null));

		assertThat(fruit.typeContentId()).isNull();
		assertThat(fruit.translations())
			.containsExactly(entry("it", new DevilFruitTranslation(null, null, null, null)));
		assertThat(bare.translations()).isEmpty();
	}

	@Test
	void theListFiltersAreTheCommonOnesAndTheTypeOnlyWhenAsked() {
		var plain = DevilFruitRequestMapper
			.toFilter(new DevilFruitListRequest(VersionStatus.DRAFT, "gomu", "nami", 3, null));
		var narrowed = DevilFruitRequestMapper.toFilter(new DevilFruitListRequest(null, null, null, null, TYPE_ID));

		assertThat(plain.status()).isEqualTo(VersionStatus.DRAFT);
		assertThat(plain.query()).isEqualTo("gomu");
		assertThat(plain.author()).isEqualTo("nami");
		assertThat(plain.updatedWithinDays()).isEqualTo(3);
		assertThat(plain.related()).isEmpty();
		assertThat(narrowed.related()).containsExactly(entry("type", TYPE_ID));
	}

	@Test
	void aVersionAndARowSayTheirTypeAsGiven() {
		var access = new VersionAccess<>(version(TYPE_ID), EnumSet.of(VersionAction.SUBMIT));
		var types = Map.of(TYPE_ID, PARAMECIA);

		var version = DevilFruitResponseMapper.toVersionResponse(access, types,
				new ImageResponseMapper(new ImageProperties("/api/content", Map.of())));
		var row = DevilFruitResponseMapper.toSummaryResponse(
				new ContentSummary<>(CONTENT_ID, version(TYPE_ID), null, access.allowedActions()), types);

		assertThat(version.body().type().id()).isEqualTo(TYPE_ID);
		assertThat(version.body().type().romaji()).isEqualTo("Paramecia");
		assertThat(version.body().type().names()).containsExactly(entry("it", "Paramisha"));
		assertThat(version.body().translations()).containsOnlyKeys("it");
		assertThat(row.body().type()).isEqualTo(version.body().type());
		assertThat(row.body().names()).containsExactly(entry("it", "Gomu"));
	}

	@Test
	void aTypeNotGivenIsOnlyItsIdAndNoTypeIsNone() {
		assertThat(DevilFruitResponseMapper.toTypeResponse(null, Map.of())).isNull();

		var unknown = DevilFruitResponseMapper.toTypeResponse(TYPE_ID, Map.of());

		assertThat(unknown.id()).isEqualTo(TYPE_ID);
		assertThat(unknown.romaji()).isNull();
		assertThat(unknown.names()).isEmpty();
	}

	@Test
	void aSubcategoryIsNamedAsItsTypeGivesItOnlyItsIdWhenTheTypeLacksItAndNoneIsNull() {
		UUID mythical = UUID.randomUUID();
		var zoan = new TypeReference(TYPE_ID, "Dobutsu", Map.of("it", "Zoo Zoo"),
				List.of(new SubcategoryReference(mythical, Map.of("it", "Mitologico"))));
		var types = Map.of(TYPE_ID, zoan);
		var named = new DevilFruit("Uo Uo", TYPE_ID, mythical, Map.of(), null);
		var unknown = new DevilFruit("Uo Uo", TYPE_ID, UUID.randomUUID(), Map.of(), null);

		assertThat(DevilFruitResponseMapper.toSubcategoryResponse(named, types))
			.isEqualTo(new SubcategoryReferenceResponse(mythical, Map.of("it", "Mitologico")));
		assertThat(DevilFruitResponseMapper.toSubcategoryResponse(unknown, types).names()).isEmpty();
		assertThat(DevilFruitResponseMapper.toSubcategoryResponse(fruit(TYPE_ID), types)).isNull();
		assertThat(DevilFruitResponseMapper.toResponse(zoan).subcategories())
			.containsExactly(new SubcategoryReferenceResponse(mythical, Map.of("it", "Mitologico")));
	}

	@Test
	void aRequestSaysItsSubcategory() {
		UUID mythical = UUID.randomUUID();

		var fruit = DevilFruitRequestMapper.toDomain(new DevilFruitRequest("Uo Uo", TYPE_ID, mythical, null, null));

		assertThat(fruit.subcategoryId()).isEqualTo(mythical);
	}

	private static DevilFruit fruit(UUID type) {
		return new DevilFruit("Gomu Gomu", type, Map.of("it", new DevilFruitTranslation("Gomu", null, null, null)));
	}

	private static Version<DevilFruit> version(UUID type) {
		return Version.<DevilFruit>builder()
			.number(1)
			.status(VersionStatus.DRAFT)
			.author(NAMI)
			.body(fruit(type))
			.createdAt(Instant.EPOCH)
			.updatedAt(Instant.EPOCH)
			.build();
	}

}
