package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.domain.species.SpeciesReference;
import dev.onepieceapi.contentservice.domain.species.SpeciesTranslation;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.service.species.SpeciesLinks;
import dev.onepieceapi.contentservice.service.species.SpeciesService;
import dev.onepieceapi.contentservice.service.exception.SpeciesNotFoundException;
import dev.onepieceapi.contentservice.service.exception.ContentErrorCode;
import dev.onepieceapi.contentservice.service.exception.ImageRefusedException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.web.controller.TestCallers.callerWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
 * The Species section over HTTP: the workflow endpoints it shares with every entity are
 * tested by {@link DevilFruitTypeControllerTest}, here what is its own - its fields, its
 * picture, its first appearance, the refusals it answers and who may write.
 */
@WebMvcTest(SpeciesController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class, ConcurrentModificationExceptionHandler.class })
class SpeciesControllerTest {

	private static final UUID CONTENT_ID = UUID.fromString("9c1d2e3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f");

	private static final UUID CHAPTER_ID = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final Instant CREATED = Instant.parse("2026-10-21T08:00:00Z");

	private static final User NAMI = new User(UUID.fromString("11111111-1111-1111-1111-111111111111"), "nami",
			"nami@onepiece.local");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SpeciesService service;

	@MockitoBean
	private SpeciesLinks links;

	@MockitoBean
	private FirstAppearanceLinks firstAppearances;

	@MockitoBean
	private ImageResponseMapper images;

	@Test
	void aVersionSaysEverythingWithItsPictureAndItsFirstAppearance() throws Exception {
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenReturn(new VersionAccess<>(version(VersionStatus.PUBLISHED, "abc"), Set.of()));
		when(this.firstAppearances.referencesFor(anyCollection()))
			.thenReturn(Map.of(CHAPTER_ID, new NumberedReference(CHAPTER_ID, 1, Map.of("it", "Romance Dawn"))));
		when(this.images.toResponse("abc")).thenReturn(new ImageResponse("abc", "/api/content/images/abc"));

		this.mockMvc.perform(get("/species/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.romaji").value("Gyojin"))
			.andExpect(jsonPath("$.body.translations.it.name").value("Uomo-pesce"))
			.andExpect(jsonPath("$.body.translations.it.description").value("Una razza marina"))
			.andExpect(jsonPath("$.body.image.url").value("/api/content/images/abc"))
			.andExpect(jsonPath("$.body.firstAppearance.chapter.number").value(1))
			.andExpect(jsonPath("$.body.firstAppearance.chapter.names.it").value("Romance Dawn"));
	}

	@Test
	void eachRowOfTheListSaysTheRomajiAndTheNames() throws Exception {
		var rows = List.of(new ContentSummary<>(CONTENT_ID, version(VersionStatus.PUBLISHED, null), 1, Set.of()));
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), 1));

		this.mockMvc.perform(get("/species").param("sort", "name").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].body.romaji").value("Gyojin"))
			.andExpect(jsonPath("$.content[0].body.names.it").value("Uomo-pesce"));
	}

	@Test
	void aSortOnlyOtherEntitiesHaveIsABadRequest() throws Exception {
		this.mockMvc.perform(get("/species").param("sort", "amount").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void creatingPassesOnEverythingWrittenAndAnswersWithTheContent() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));

		this.mockMvc
			.perform(post("/species").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"romaji\":\"Gyojin\",\"translations\":{\"en\":{\"name\":\"Fish-Man\","
						+ "\"description\":\"Fish-Man\"}},\"firstAppearance\":{\"chapter\":\"" + CHAPTER_ID + "\"}}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()));

		var written = ArgumentCaptor.forClass(Species.class);
		verify(this.service).create(eq(Set.of(Permission.CONTENT_WRITE)), any(), written.capture(),
				eq(ImageChange.KEEP));
		assertThat(written.getValue().romaji()).isEqualTo("Gyojin");
		assertThat(written.getValue().translations().get("en"))
			.isEqualTo(new SpeciesTranslation("Fish-Man", "Fish-Man"));
		assertThat(written.getValue().firstAppearance()).isEqualTo(StoryPoint.ofChapter(CHAPTER_ID));
	}

	@Test
	void creatingWithAnPicturePassesOnTheFile() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));
		byte[] upload = { 1, 2, 3 };

		this.mockMvc
			.perform(multipart("/species").file(versionPart("{\"romaji\":\"Gyojin\"}"))
				.file(new MockMultipartFile("image", "fish-man.png", "image/png", upload))
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isCreated());

		var change = ArgumentCaptor.forClass(ImageChange.class);
		verify(this.service).create(any(), any(), any(), change.capture());
		assertThat(change.getValue()).isInstanceOfSatisfying(ImageChange.Replace.class,
				replace -> assertThat(replace.upload()).isEqualTo(upload));
	}

	@Test
	void editingWithoutAFileKeepsThePictureAndRemoveImageRemovesIt() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), eq(CONTENT_ID), eq(1), any(), any())).thenReturn(access);

		this.mockMvc
			.perform(multipart(HttpMethod.PUT, "/species/" + CONTENT_ID + "/versions/1")
				.file(versionPart("{\"romaji\":\"Kaigun\"}"))
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk());
		this.mockMvc
			.perform(put("/species/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"romaji\":\"Kaigun\",\"removeImage\":true}"))
			.andExpect(status().isOk());

		verify(this.service).edit(any(), any(), eq(CONTENT_ID), eq(1), any(), eq(ImageChange.KEEP));
		verify(this.service).edit(any(), any(), eq(CONTENT_ID), eq(1), any(), eq(ImageChange.REMOVE));
	}

	@Test
	void aTextLongerThanItsLimitIsABadRequestAndOneAsLongIsAccepted() throws Exception {
		for (String body : List.of("{\"romaji\":\"" + "x".repeat(101) + "\"}",
				"{\"translations\":{\"it\":{\"name\":\"" + "x".repeat(101) + "\"}}}",
				"{\"translations\":{\"it\":{\"description\":\"" + "x".repeat(2001) + "\"}}}")) {
			this.mockMvc
				.perform(post("/species").with(callerWith(Permission.CONTENT_WRITE))
					.contentType("application/json")
					.content(body))
				.andExpect(status().isBadRequest());
		}
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));
		this.mockMvc
			.perform(post("/species").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"romaji\":\"" + "x".repeat(100) + "\",\"translations\":{\"it\":{\"name\":\""
						+ "x".repeat(100) + "\",\"description\":\"" + "x".repeat(2000) + "\"}}}"))
			.andExpect(status().isCreated());
	}

	@Test
	void anSpeciesTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.get(any(), any(), eq(CONTENT_ID))).thenThrow(new SpeciesNotFoundException(CONTENT_ID));

		this.mockMvc.perform(get("/species/" + CONTENT_ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_SPECIES_NOT_FOUND"));
	}

	@Test
	void aRomajiAnotherSpeciesHasIsUnprocessableAndNamesIt() throws Exception {
		var taken = List.of(new FieldViolation("romaji", "is already used by another content"));
		when(this.service.create(any(), any(), any(), any())).thenThrow(new ValueAlreadyUsedException(taken));

		this.mockMvc
			.perform(post("/species").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"romaji\":\"Kaigun\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_ALREADY_USED"))
			.andExpect(jsonPath("$.errors[0].field").value("romaji"));
	}

	@Test
	void aRefusedPictureNamesItsReasonAndNumbers() throws Exception {
		when(this.service.create(any(), any(), any(), any())).thenThrow(new ImageRefusedException(
				ContentErrorCode.IMAGE_NOT_TRANSPARENT, "The image has no transparency", Map.of("minPercent", 5)));

		this.mockMvc
			.perform(multipart("/species").file(versionPart("{\"romaji\":\"Kaigun\"}"))
				.file(new MockMultipartFile("image", "kaigun.png", "image/png", new byte[] { 1 }))
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_IMAGE_NOT_TRANSPARENT"))
			.andExpect(jsonPath("$.field").value("image"))
			.andExpect(jsonPath("$.minPercent").value(5));
	}

	@Test
	void aPublicationWithoutAFirstAppearanceIsAConflictWithItsReason() throws Exception {
		var block = new ActionBlock(BlockReason.FIRST_APPEARANCE_MISSING, Map.of());
		when(this.service.publish(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenThrow(new VersionActionBlockedException(VersionAction.PUBLISH, block));

		this.mockMvc
			.perform(
					post("/species/" + CONTENT_ID + "/versions/1/publish").with(callerWith(Permission.CONTENT_PUBLISH)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_ACTION_BLOCKED"))
			.andExpect(jsonPath("$.reason").value("FIRST_APPEARANCE_MISSING"));
	}

	@Test
	void readingTakesContentReadAndWritingContentWrite() throws Exception {
		this.mockMvc.perform(get("/species")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/species").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(post("/species").with(callerWith(Permission.CONTENT_READ))
				.contentType("application/json")
				.content("{}"))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(post("/species/" + CONTENT_ID + "/versions/1/publish").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isForbidden());
	}

	private static MockMultipartFile versionPart(String json) {
		return new MockMultipartFile("version", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
	}

	private static Version<Species> version(VersionStatus status, String imageId) {
		var translations = Map.of("it", new SpeciesTranslation("Uomo-pesce", "Una razza marina"));
		return Version.<Species>builder()
			.number(1)
			.status(status)
			.author(NAMI)
			.body(new Species("Gyojin", translations, imageId, StoryPoint.ofChapter(CHAPTER_ID)))
			.createdAt(CREATED)
			.updatedAt(CREATED)
			.build();
	}

	@Test
	void theSpeciesACharacterMayBeLinkedToAreListedForWhoeverWritesCharacters() throws Exception {
		var gyojin = new SpeciesReference(CONTENT_ID, "Gyojin", Map.of("it", "Uomo-pesce"));
		when(this.links.linkable(any(), anyInt(), anyInt()))
			.thenReturn(new PageImpl<>(List.of(gyojin), PageRequest.of(1, 5), 6));

		this.mockMvc
			.perform(get("/species/linkable").param("q", "gyo")
				.param("page", "1")
				.param("size", "5")
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(6))
			.andExpect(jsonPath("$.content[0].id").value(CONTENT_ID.toString()))
			.andExpect(jsonPath("$.content[0].romaji").value("Gyojin"))
			.andExpect(jsonPath("$.content[0].names.it").value("Uomo-pesce"));
		verify(this.links).linkable("gyo", 1, 5);
	}

	@Test
	void theLinkableSpeciesAreFirstPageTwentyByDefault() throws Exception {
		when(this.links.linkable(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

		this.mockMvc.perform(get("/species/linkable").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk());

		verify(this.links).linkable(null, 0, 20);
	}

	@Test
	void theLinkableSpeciesAreNotForWhoOnlyReadsAndAreNotAContentId() throws Exception {
		this.mockMvc.perform(get("/species/linkable").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(get("/species/linkable")).andExpect(status().isUnauthorized());
		verifyNoInteractions(this.links);
	}

}
