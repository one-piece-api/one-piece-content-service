package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.language.Language;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.web.dto.request.ContentListRequest;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTypeTranslationResponse;
import dev.onepieceapi.contentservice.web.dto.response.LanguageResponse;
import dev.onepieceapi.contentservice.web.dto.response.UserResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The web mappers: the workflow half shared by every content, and the Devil Fruit Type
 * half plugged into it.
 */
class ResponseMappersTest {

	private static final UUID CONTENT_ID = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-09-30T08:00:00Z");

	private static final Instant UPDATED = Instant.parse("2026-10-01T09:30:00Z");

	private static final User NAMI = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private static final User ZORO = new User(UUID.randomUUID(), "zoro", "zoro@onepiece.local");

	private static final DevilFruitType ZOAN = new DevilFruitType("Zoan",
			Map.of("it", new DevilFruitTypeTranslation("Zoo Zoo", "Trasforma in animale"), "en",
					new DevilFruitTypeTranslation(null, "Turns into an animal")));

	@Test
	void aVersionResponseCarriesTheWorkflowAndTheBodyTheCallerMapped() {
		var response = ContentResponseMapper.toVersionResponse(rejected(2), devilFruitType -> "the body");

		assertThat(response.number()).isEqualTo(2);
		assertThat(response.status()).isEqualTo(VersionStatus.REJECTED);
		assertThat(response.author()).isEqualTo(new UserResponse(NAMI.id(), "nami", "nami@onepiece.local"));
		assertThat(response.basedOn()).isEqualTo(1);
		assertThat(response.claimant()).isEqualTo(new UserResponse(ZORO.id(), "zoro", "zoro@onepiece.local"));
		assertThat(response.everPublished()).isFalse();
		assertThat(response.rejectionReason()).isEqualTo("Too short");
		assertThat(response.body()).isEqualTo("the body");
		assertThat(response.createdAt()).isEqualTo(CREATED);
		assertThat(response.updatedAt()).isEqualTo(UPDATED);
	}

	@Test
	void aContentResponseListsTheChainWithoutBodiesAndTheOnlineVersion() {
		var content = new Content<>(CONTENT_ID,
				List.of(version(1, VersionStatus.SUPERSEDED), version(2, VersionStatus.PUBLISHED), rejected(3)));

		var response = ContentResponseMapper.toContentResponse(content);

		assertThat(response.id()).isEqualTo(CONTENT_ID);
		assertThat(response.onlineVersionNumber()).isEqualTo(2);
		assertThat(response.versions()).extracting(summary -> summary.number(), summary -> summary.everPublished())
			.containsExactly(tuple(1, true), tuple(2, true), tuple(3, false));
		assertThat(response.versions().get(0).claimant()).isNull();
		assertThat(response.versions().get(2).claimant().username()).isEqualTo("zoro");
	}

	@Test
	void aContentWithNothingOnlineSaysSoWithNull() {
		var content = new Content<>(CONTENT_ID, List.of(version(1, VersionStatus.DRAFT)));

		assertThat(ContentResponseMapper.toContentResponse(content).onlineVersionNumber()).isNull();
	}

	@Test
	void aSummaryResponseIsTheRowOfTheContentShownByItsVersion() {
		var summary = new ContentSummary<>(CONTENT_ID, rejected(3), 2);

		var response = ContentResponseMapper.toSummaryResponse(summary, DevilFruitType::romaji);

		assertThat(response.id()).isEqualTo(CONTENT_ID);
		assertThat(response.versionNumber()).isEqualTo(3);
		assertThat(response.status()).isEqualTo(VersionStatus.REJECTED);
		assertThat(response.author().username()).isEqualTo("nami");
		assertThat(response.updatedAt()).isEqualTo(UPDATED);
		assertThat(response.onlineVersionNumber()).isEqualTo(2);
		assertThat(response.body()).isEqualTo("Zoan");
	}

	@Test
	void anEventResponseTellsWhoDidWhatAndWhen() {
		var response = ContentResponseMapper
			.toEventResponse(new VersionEvent("VERSION_REJECTED", ZORO, "Too short", UPDATED));

		assertThat(response.action()).isEqualTo("VERSION_REJECTED");
		assertThat(response.actor().username()).isEqualTo("zoro");
		assertThat(response.detail()).isEqualTo("Too short");
		assertThat(response.occurredAt()).isEqualTo(UPDATED);
	}

	@Test
	void theBodyOfADevilFruitTypeHasEveryTranslationSortedByLanguage() {
		var body = DevilFruitTypeResponseMapper.toResponse(ZOAN);

		assertThat(body.romaji()).isEqualTo("Zoan");
		assertThat(body.translations()).containsExactly(
				entry("en", new DevilFruitTypeTranslationResponse(null, "Turns into an animal")),
				entry("it", new DevilFruitTypeTranslationResponse("Zoo Zoo", "Trasforma in animale")));
	}

	@Test
	void theRowOfADevilFruitTypeHasOnlyTheNamesThatExist() {
		var names = DevilFruitTypeResponseMapper.toNamesResponse(ZOAN);

		assertThat(names.romaji()).isEqualTo("Zoan");
		assertThat(names.names()).containsExactly(entry("it", "Zoo Zoo"));
	}

	@Test
	void theDevilFruitTypeMapperPlugsItsBodiesIntoTheSharedResponses() {
		var version = DevilFruitTypeResponseMapper.toVersionResponse(rejected(2));
		var row = DevilFruitTypeResponseMapper.toSummaryResponse(new ContentSummary<>(CONTENT_ID, rejected(2), null));

		assertThat(version.body().translations()).containsKeys("en", "it");
		assertThat(row.body().names()).containsOnlyKeys("it");
		assertThat(row.onlineVersionNumber()).isNull();
	}

	@Test
	void theListRequestBecomesTheFilterFieldByField() {
		var request = new ContentListRequest(VersionStatus.DRAFT, "zoan", NAMI.id(), 7);

		assertThat(ContentRequestMapper.toFilter(request))
			.isEqualTo(new ContentFilter(VersionStatus.DRAFT, "zoan", NAMI.id(), 7));
	}

	@Test
	void aLanguageResponseIsItsCodeAndName() {
		assertThat(LanguageResponseMapper.toResponse(new Language("fr", "Français")))
			.isEqualTo(new LanguageResponse("fr", "Français"));
	}

	private static Version<DevilFruitType> version(int number, VersionStatus status) {
		return Version.<DevilFruitType>builder()
			.number(number)
			.basedOn(number == 1 ? null : number - 1)
			.status(status)
			.author(NAMI)
			.body(ZOAN)
			.createdAt(CREATED)
			.updatedAt(UPDATED)
			.build();
	}

	/** A version zoro held and rejected. */
	private static Version<DevilFruitType> rejected(int number) {
		return Version.<DevilFruitType>builder()
			.number(number)
			.basedOn(number - 1)
			.status(VersionStatus.REJECTED)
			.author(NAMI)
			.claimant(ZORO)
			.rejectionReason("Too short")
			.body(ZOAN)
			.createdAt(CREATED)
			.updatedAt(UPDATED)
			.build();
	}

}
