package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationTranslation;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.service.affiliation.AffiliationService;
import dev.onepieceapi.contentservice.service.exception.AffiliationNotFoundException;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Affiliation section over HTTP: the workflow endpoints it shares with every entity
 * are tested by {@link DevilFruitTypeControllerTest}, here what is its own - its fields,
 * its emblem, its first appearance, the refusals it answers and who may write.
 */
@WebMvcTest(AffiliationController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class, ConcurrentModificationExceptionHandler.class })
class AffiliationControllerTest {

	private static final UUID CONTENT_ID = UUID.fromString("9c1d2e3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f");

	private static final UUID CHAPTER_ID = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final Instant CREATED = Instant.parse("2026-10-21T08:00:00Z");

	private static final User NAMI = new User(UUID.fromString("11111111-1111-1111-1111-111111111111"), "nami",
			"nami@onepiece.local");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AffiliationService service;

	@MockitoBean
	private FirstAppearanceLinks firstAppearances;

	@MockitoBean
	private ImageResponseMapper images;

	@Test
	void aVersionSaysEverythingWithItsEmblemAndItsFirstAppearance() throws Exception {
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenReturn(new VersionAccess<>(version(VersionStatus.PUBLISHED, "abc"), Set.of()));
		when(this.firstAppearances.referencesFor(anyCollection()))
			.thenReturn(Map.of(CHAPTER_ID, new NumberedReference(CHAPTER_ID, 1, Map.of("it", "Romance Dawn"))));
		when(this.images.toResponse("abc")).thenReturn(new ImageResponse("abc", "/api/content/images/abc"));

		this.mockMvc
			.perform(get("/affiliations/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.romaji").value("Mugiwara no Ichimi"))
			.andExpect(jsonPath("$.body.translations.it.name").value("Pirati di Cappello di Paglia"))
			.andExpect(jsonPath("$.body.translations.it.description").value("La ciurma di Rufy"))
			.andExpect(jsonPath("$.body.kind").value("PIRATE_CREW"))
			.andExpect(jsonPath("$.body.image.url").value("/api/content/images/abc"))
			.andExpect(jsonPath("$.body.firstAppearance.chapter.number").value(1))
			.andExpect(jsonPath("$.body.firstAppearance.chapter.names.it").value("Romance Dawn"));
	}

	@Test
	void eachRowOfTheListSaysTheRomajiAndTheNames() throws Exception {
		var rows = List.of(new ContentSummary<>(CONTENT_ID, version(VersionStatus.PUBLISHED, null), 1, Set.of()));
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), 1));

		this.mockMvc.perform(get("/affiliations").param("sort", "name").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].body.romaji").value("Mugiwara no Ichimi"))
			.andExpect(jsonPath("$.content[0].body.names.it").value("Pirati di Cappello di Paglia"));
	}

	@Test
	void theListIsNarrowedByKind() throws Exception {
		when(this.service.list(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

		this.mockMvc.perform(get("/affiliations").param("kind", "NAVY").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk());

		var filter = ArgumentCaptor.forClass(ContentFilter.class);
		verify(this.service).list(any(), any(), filter.capture(), any(), any());
		assertThat(filter.getValue().attributes()).isEqualTo(Map.of(Affiliation.KIND_ATTRIBUTE, AffiliationKind.NAVY));
	}

	@Test
	void aSortOnlyOtherEntitiesHaveIsABadRequest() throws Exception {
		this.mockMvc.perform(get("/affiliations").param("sort", "amount").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void creatingPassesOnEverythingWrittenAndAnswersWithTheContent() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));

		this.mockMvc.perform(post("/affiliations").with(callerWith(Permission.CONTENT_WRITE))
			.contentType("application/json")
			.content("{\"romaji\":\"Mugiwara no Ichimi\",\"translations\":{\"en\":{\"name\":\"Straw Hat Pirates\","
					+ "\"description\":\"Luffy's crew\"}},\"firstAppearance\":{\"chapter\":\"" + CHAPTER_ID + "\"}}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()));

		var written = ArgumentCaptor.forClass(Affiliation.class);
		verify(this.service).create(eq(Set.of(Permission.CONTENT_WRITE)), any(), written.capture(),
				eq(ImageChange.KEEP));
		assertThat(written.getValue().romaji()).isEqualTo("Mugiwara no Ichimi");
		assertThat(written.getValue().translations().get("en"))
			.isEqualTo(new AffiliationTranslation("Straw Hat Pirates", "Luffy's crew"));
		assertThat(written.getValue().firstAppearance()).isEqualTo(StoryPoint.ofChapter(CHAPTER_ID));
	}

	@Test
	void creatingWithAnEmblemPassesOnTheFile() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));
		byte[] upload = { 1, 2, 3 };

		this.mockMvc
			.perform(multipart("/affiliations").file(versionPart("{\"romaji\":\"Mugiwara no Ichimi\"}"))
				.file(new MockMultipartFile("image", "jolly-roger.png", "image/png", upload))
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isCreated());

		var change = ArgumentCaptor.forClass(ImageChange.class);
		verify(this.service).create(any(), any(), any(), change.capture());
		assertThat(change.getValue()).isInstanceOfSatisfying(ImageChange.Replace.class,
				replace -> assertThat(replace.upload()).isEqualTo(upload));
	}

	@Test
	void editingWithoutAFileKeepsTheEmblemAndRemoveImageRemovesIt() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), eq(CONTENT_ID), eq(1), any(), any())).thenReturn(access);

		this.mockMvc
			.perform(multipart(HttpMethod.PUT, "/affiliations/" + CONTENT_ID + "/versions/1")
				.file(versionPart("{\"romaji\":\"Kaigun\"}"))
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk());
		this.mockMvc
			.perform(put("/affiliations/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_WRITE))
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
				.perform(post("/affiliations").with(callerWith(Permission.CONTENT_WRITE))
					.contentType("application/json")
					.content(body))
				.andExpect(status().isBadRequest());
		}
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));
		this.mockMvc
			.perform(post("/affiliations").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"romaji\":\"" + "x".repeat(100) + "\",\"translations\":{\"it\":{\"name\":\""
						+ "x".repeat(100) + "\",\"description\":\"" + "x".repeat(2000) + "\"}}}"))
			.andExpect(status().isCreated());
	}

	@Test
	void anAffiliationTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.get(any(), any(), eq(CONTENT_ID))).thenThrow(new AffiliationNotFoundException(CONTENT_ID));

		this.mockMvc.perform(get("/affiliations/" + CONTENT_ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_AFFILIATION_NOT_FOUND"));
	}

	@Test
	void aRomajiAnotherAffiliationHasIsUnprocessableAndNamesIt() throws Exception {
		var taken = List.of(new FieldViolation("romaji", "is already used by another content"));
		when(this.service.create(any(), any(), any(), any())).thenThrow(new ValueAlreadyUsedException(taken));

		this.mockMvc
			.perform(post("/affiliations").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"romaji\":\"Kaigun\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_ALREADY_USED"))
			.andExpect(jsonPath("$.errors[0].field").value("romaji"));
	}

	@Test
	void aRefusedEmblemNamesItsReasonAndNumbers() throws Exception {
		when(this.service.create(any(), any(), any(), any())).thenThrow(new ImageRefusedException(
				ContentErrorCode.IMAGE_NOT_TRANSPARENT, "The image has no transparency", Map.of("minPercent", 5)));

		this.mockMvc
			.perform(multipart("/affiliations").file(versionPart("{\"romaji\":\"Kaigun\"}"))
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
			.perform(post("/affiliations/" + CONTENT_ID + "/versions/1/publish")
				.with(callerWith(Permission.CONTENT_PUBLISH)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_ACTION_BLOCKED"))
			.andExpect(jsonPath("$.reason").value("FIRST_APPEARANCE_MISSING"));
	}

	@Test
	void readingTakesContentReadAndWritingContentWrite() throws Exception {
		this.mockMvc.perform(get("/affiliations")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/affiliations").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(post("/affiliations").with(callerWith(Permission.CONTENT_READ))
				.contentType("application/json")
				.content("{}"))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(post("/affiliations/" + CONTENT_ID + "/versions/1/publish")
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isForbidden());
	}

	@Test
	void thereIsNoLinkableEndpoint() throws Exception {
		this.mockMvc.perform(get("/affiliations/linkable").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().is4xxClientError());
	}

	private static MockMultipartFile versionPart(String json) {
		return new MockMultipartFile("version", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
	}

	private static Version<Affiliation> version(VersionStatus status, String imageId) {
		var translations = Map.of("it",
				new AffiliationTranslation("Pirati di Cappello di Paglia", "La ciurma di Rufy"));
		return Version.<Affiliation>builder()
			.number(1)
			.status(status)
			.author(NAMI)
			.body(new Affiliation("Mugiwara no Ichimi", translations, imageId, StoryPoint.ofChapter(CHAPTER_ID),
					AffiliationKind.PIRATE_CREW))
			.createdAt(CREATED)
			.updatedAt(CREATED)
			.build();
	}

}
