package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.Epithet;
import dev.onepieceapi.contentservice.domain.character.Gender;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.character.CharacterReference;
import dev.onepieceapi.contentservice.domain.species.SpeciesReference;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.service.character.CharacterService;
import dev.onepieceapi.contentservice.service.exception.CharacterNotFoundException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.bounty.BountyLinks;
import dev.onepieceapi.contentservice.service.character.CharacterLinks;
import dev.onepieceapi.contentservice.service.species.SpeciesLinks;
import dev.onepieceapi.contentservice.service.story.FirstAppearanceLinks;
import dev.onepieceapi.contentservice.web.dto.response.ImageResponse;
import dev.onepieceapi.contentservice.web.mapper.ImageResponseMapper;
import dev.onepieceapi.contentservice.web.security.SecurityConfig;
import dev.onepieceapi.exception.web.ApplicationExceptionHandler;
import dev.onepieceapi.exception.web.ConcurrentModificationExceptionHandler;
import dev.onepieceapi.exception.web.FieldViolation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.MonthDay;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.web.controller.TestCallers.callerWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Character section over HTTP: the workflow endpoints it shares with every entity are
 * tested by {@link DevilFruitTypeControllerTest}, here what is its own - its fields and
 * lists, its birthday, its portrait, its species, the refusals it answers and who may
 * write.
 */
@WebMvcTest(CharacterController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class, ConcurrentModificationExceptionHandler.class })
class CharacterControllerTest {

	private static final UUID CONTENT_ID = UUID.fromString("9c1d2e3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f");

	private static final UUID CHAPTER_ID = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID NINGEN = UUID.fromString("7a1d3c2b-5e4f-4a6b-8c9d-0e1f2a3b4c5d");

	private static final UUID GYOJIN = UUID.fromString("8b2e4d3c-6f5a-4b7c-9d0e-1f2a3b4c5d6e");

	private static final Instant CREATED = Instant.parse("2026-10-21T08:00:00Z");

	private static final User CHOPPER = new User(UUID.fromString("11111111-1111-1111-1111-111111111111"), "chopper",
			"chopper@onepiece.local");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CharacterService service;

	@MockitoBean
	private SpeciesLinks species;

	@MockitoBean
	private CharacterLinks links;

	@MockitoBean
	private BountyLinks bounties;

	@MockitoBean
	private FirstAppearanceLinks firstAppearances;

	@MockitoBean
	private ImageResponseMapper images;

	@Test
	void aVersionSaysEverythingItsListsInTheirOrderAndEachSpeciesByItsNames() throws Exception {
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenReturn(new VersionAccess<>(version(VersionStatus.PUBLISHED, "abc"), Set.of()));
		when(this.species.referencesOf(List.of(GYOJIN, NINGEN)))
			.thenReturn(Map.of(NINGEN, new SpeciesReference(NINGEN, "Ningen", Map.of("it", "Umano"))));
		when(this.firstAppearances.referencesFor(anyCollection())).thenReturn(Map.of());
		when(this.images.toResponse("abc")).thenReturn(new ImageResponse("abc", "/api/content/images/abc"));

		this.mockMvc.perform(get("/characters/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.romaji").value("Nami"))
			.andExpect(jsonPath("$.body.japaneseName").value("ナミ"))
			.andExpect(jsonPath("$.body.translations.it.name").value("Nami"))
			.andExpect(jsonPath("$.body.status").value("ALIVE"))
			.andExpect(jsonPath("$.body.gender").value("FEMALE"))
			.andExpect(jsonPath("$.body.birthday").value("--07-03"))
			.andExpect(jsonPath("$.body.epithets[0].text.en").value("Cat Burglar"))
			.andExpect(jsonPath("$.body.epithets[1].text.it").value("Strega del tempo"))
			.andExpect(jsonPath("$.body.aliases[0]").value("Miss Nami"))
			.andExpect(jsonPath("$.body.species[0].id").value(GYOJIN.toString()))
			.andExpect(jsonPath("$.body.species[0].romaji").doesNotExist())
			.andExpect(jsonPath("$.body.species[1].romaji").value("Ningen"))
			.andExpect(jsonPath("$.body.species[1].names.it").value("Umano"))
			.andExpect(jsonPath("$.body.image.url").value("/api/content/images/abc"));
	}

	@Test
	void eachRowOfTheListSaysTheRomajiAndTheNames() throws Exception {
		var rows = List.of(new ContentSummary<>(CONTENT_ID, version(VersionStatus.PUBLISHED, null), 1, Set.of()));
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), 1));
		when(this.bounties.bountyCounts(any(), eq(List.of(CONTENT_ID)))).thenReturn(Map.of(CONTENT_ID, 3L));

		this.mockMvc.perform(get("/characters").param("sort", "name").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].body.romaji").value("Nami"))
			.andExpect(jsonPath("$.content[0].body.names.it").value("Nami"))
			.andExpect(jsonPath("$.content[0].body.bountyCount").value(3));
	}

	@Test
	void aCharacterWithNoBountyHasACountOfZero() throws Exception {
		var rows = List.of(new ContentSummary<>(CONTENT_ID, version(VersionStatus.PUBLISHED, null), 1, Set.of()));
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), 1));

		this.mockMvc.perform(get("/characters").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].body.bountyCount").value(0));
	}

	@Test
	void theCharactersThatCanBeLinkedAreListedByTheirRomaji() throws Exception {
		when(this.links.linkable(any(), eq(0), eq(20)))
			.thenReturn(new PageImpl<>(List.of(new CharacterReference(CONTENT_ID, "Nami", Map.of("it", "Nami")))));

		this.mockMvc.perform(get("/characters/linkable").param("q", "nam").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(CONTENT_ID.toString()))
			.andExpect(jsonPath("$.content[0].romaji").value("Nami"));
		this.mockMvc.perform(get("/characters/linkable").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isForbidden());
	}

	@Test
	void theListIsNarrowedByTheStatusOfTheCharacterAndBySpecies() throws Exception {
		when(this.service.list(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

		this.mockMvc
			.perform(get("/characters").param("characterStatus", "DECEASED")
				.param("species", NINGEN.toString())
				.with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk());

		var filter = ArgumentCaptor.forClass(ContentFilter.class);
		verify(this.service).list(any(), any(), filter.capture(), any(), any());
		assertThat(filter.getValue().attributes()).isEqualTo(
				Map.of(Character.STATUS_ATTRIBUTE, CharacterStatus.DECEASED, Character.SPECIES_ATTRIBUTE, NINGEN));
	}

	@Test
	void creatingPassesOnEverythingWrittenAndAnswersWithTheContent() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));

		this.mockMvc
			.perform(post("/characters").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content(
						"""
								{"romaji":"Nami","japaneseName":"ナミ","translations":{"en":{"name":"Nami","description":"Navigator"}},
								"status":"ALIVE","gender":"FEMALE","birthday":"--07-03",
								"epithets":[{"text":{"it":"Gatta ladra","en":"Cat Burglar"}}],"aliases":["Miss Nami"],
								"species":["%s","%s"],"firstAppearance":{"chapter":"%s"}}"""
							.formatted(GYOJIN, NINGEN, CHAPTER_ID)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()));

		var written = ArgumentCaptor.forClass(Character.class);
		verify(this.service).create(eq(Set.of(Permission.CONTENT_WRITE)), any(), written.capture(),
				eq(ImageChange.KEEP));
		var body = written.getValue();
		assertThat(body.romaji()).isEqualTo("Nami");
		assertThat(body.japaneseName()).isEqualTo("ナミ");
		assertThat(body.translations().get("en")).isEqualTo(new CharacterTranslation("Nami", "Navigator"));
		assertThat(body.status()).isEqualTo(CharacterStatus.ALIVE);
		assertThat(body.gender()).isEqualTo(Gender.FEMALE);
		assertThat(body.birthday()).isEqualTo(MonthDay.of(7, 3));
		assertThat(body.epithets()).containsExactly(new Epithet(Map.of("it", "Gatta ladra", "en", "Cat Burglar")));
		assertThat(body.aliases()).containsExactly("Miss Nami");
		assertThat(body.speciesIds()).containsExactly(GYOJIN, NINGEN);
		assertThat(body.firstAppearance()).isEqualTo(StoryPoint.ofChapter(CHAPTER_ID));
	}

	@Test
	void aBirthdayNoYearHasIsABadRequestAndTheTwentyNinthOfFebruaryIsAccepted() throws Exception {
		this.mockMvc
			.perform(post("/characters").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"romaji\":\"Nami\",\"birthday\":\"--02-30\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));
		this.mockMvc
			.perform(post("/characters").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"romaji\":\"Nami\",\"birthday\":\"--02-29\"}"))
			.andExpect(status().isCreated());
	}

	@Test
	void aTextOrAListLongerThanItsLimitIsABadRequest() throws Exception {
		String aliases = "[" + "\"a\",".repeat(Character.MAX_ALIASES) + "\"a\"]";
		for (String body : List.of("{\"romaji\":\"" + "x".repeat(101) + "\"}",
				"{\"japaneseName\":\"" + "x".repeat(101) + "\"}",
				"{\"translations\":{\"it\":{\"description\":\"" + "x".repeat(2001) + "\"}}}",
				"{\"epithets\":[{\"text\":{\"it\":\"" + "x".repeat(101) + "\"}}]}",
				"{\"aliases\":[\"" + "x".repeat(101) + "\"]}", "{\"aliases\":" + aliases + "}", "{\"aliases\":[null]}",
				"{\"species\":[null]}", "{\"status\":\"MISSING\"}")) {
			this.mockMvc
				.perform(post("/characters").with(callerWith(Permission.CONTENT_WRITE))
					.contentType("application/json")
					.content(body))
				.andExpect(status().isBadRequest());
		}
		verifyNoInteractions(this.service);
	}

	@Test
	void creatingWithAPortraitPassesOnTheFileAndEditingCanRemoveIt() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));
		when(this.service.edit(any(), any(), eq(CONTENT_ID), eq(1), any(), any())).thenReturn(draft);
		byte[] upload = { 1, 2, 3 };

		this.mockMvc
			.perform(multipart("/characters").file(versionPart("{\"romaji\":\"Nami\"}"))
				.file(new MockMultipartFile("image", "nami.png", "image/png", upload))
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isCreated());
		this.mockMvc
			.perform(multipart(HttpMethod.PUT, "/characters/" + CONTENT_ID + "/versions/1")
				.file(versionPart("{\"romaji\":\"Nami\"}"))
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk());
		this.mockMvc
			.perform(put("/characters/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"romaji\":\"Nami\",\"removeImage\":true}"))
			.andExpect(status().isOk());

		var change = ArgumentCaptor.forClass(ImageChange.class);
		verify(this.service).create(any(), any(), any(), change.capture());
		assertThat(change.getValue()).isInstanceOfSatisfying(ImageChange.Replace.class,
				replace -> assertThat(replace.upload()).isEqualTo(upload));
		verify(this.service).edit(any(), any(), eq(CONTENT_ID), eq(1), any(), eq(ImageChange.KEEP));
		verify(this.service).edit(any(), any(), eq(CONTENT_ID), eq(1), any(), eq(ImageChange.REMOVE));
	}

	@Test
	void aCharacterTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.get(any(), any(), eq(CONTENT_ID))).thenThrow(new CharacterNotFoundException(CONTENT_ID));

		this.mockMvc.perform(get("/characters/" + CONTENT_ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_CHARACTER_NOT_FOUND"));
	}

	@Test
	void aRomajiAnotherCharacterHasIsUnprocessableAndNamesIt() throws Exception {
		var taken = List.of(new FieldViolation("romaji", "is already used by another content"));
		when(this.service.create(any(), any(), any(), any())).thenThrow(new ValueAlreadyUsedException(taken));

		this.mockMvc
			.perform(post("/characters").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"romaji\":\"Nami\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_ALREADY_USED"))
			.andExpect(jsonPath("$.errors[0].field").value("romaji"));
	}

	@Test
	void aPublicationWhileASpeciesIsNotOnlineIsAConflictNamingIt() throws Exception {
		var block = new ActionBlock(BlockReason.RELATED_NOT_ONLINE,
				Map.of("speciesIndex", 1, "speciesId", NINGEN, "speciesRomaji", "Ningen"));
		when(this.service.publish(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenThrow(new VersionActionBlockedException(VersionAction.PUBLISH, block));

		this.mockMvc
			.perform(post("/characters/" + CONTENT_ID + "/versions/1/publish")
				.with(callerWith(Permission.CONTENT_PUBLISH)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_ACTION_BLOCKED"))
			.andExpect(jsonPath("$.reason").value("RELATED_NOT_ONLINE"))
			.andExpect(jsonPath("$.detail.speciesIndex").value(1))
			.andExpect(jsonPath("$.detail.speciesRomaji").value("Ningen"));
	}

	@Test
	void readingTakesContentReadAndWritingContentWrite() throws Exception {
		this.mockMvc.perform(get("/characters")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/characters").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(post("/characters").with(callerWith(Permission.CONTENT_READ))
				.contentType("application/json")
				.content("{}"))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(post("/characters/" + CONTENT_ID + "/versions/1/publish")
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isForbidden());
	}

	private static MockMultipartFile versionPart(String json) {
		return new MockMultipartFile("version", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
	}

	private static Version<Character> version(VersionStatus status, String imageId) {
		var body = Character.builder()
			.romaji("Nami")
			.japaneseName("ナミ")
			.translations(Map.of("it", new CharacterTranslation("Nami", "La navigatrice")))
			.status(CharacterStatus.ALIVE)
			.gender(Gender.FEMALE)
			.birthday(MonthDay.of(7, 3))
			.epithets(List.of(new Epithet(Map.of("en", "Cat Burglar")), new Epithet(Map.of("it", "Strega del tempo"))))
			.aliases(List.of("Miss Nami"))
			.speciesIds(List.of(GYOJIN, NINGEN))
			.imageId(imageId)
			.firstAppearance(StoryPoint.ofChapter(CHAPTER_ID))
			.build();
		return Version.<Character>builder()
			.number(1)
			.status(status)
			.author(CHOPPER)
			.body(body)
			.createdAt(CREATED)
			.updatedAt(CREATED)
			.build();
	}

}
