package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.story.Release;
import dev.onepieceapi.contentservice.domain.story.Volume;
import dev.onepieceapi.contentservice.domain.story.VolumeTranslation;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.service.exception.ContentErrorCode;
import dev.onepieceapi.contentservice.service.exception.ImageRefusedException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.exception.VolumeNotFoundException;
import dev.onepieceapi.contentservice.service.story.VolumeLinks;
import dev.onepieceapi.contentservice.service.story.VolumeService;
import dev.onepieceapi.contentservice.web.dto.response.ImageResponse;
import dev.onepieceapi.contentservice.web.mapper.ImageResponseMapper;
import dev.onepieceapi.contentservice.web.security.SecurityConfig;
import dev.onepieceapi.exception.web.ApplicationExceptionHandler;
import dev.onepieceapi.exception.web.ConcurrentModificationExceptionHandler;
import dev.onepieceapi.exception.web.FieldViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.web.controller.TestCallers.callerWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
 * The volume section over HTTP: what the workflow endpoints it shares with every entity
 * are tested by {@link DevilFruitTypeControllerTest}, here what is its own - the cover as
 * an id and a URL, uploaded with the version as a multipart request, kept, replaced or
 * removed; the volumes a chapter may be linked to; the extras (Japanese title, releases);
 * a value refused or an action blocked; the limits of what a draft is saved with; and who
 * may write.
 */
@WebMvcTest(VolumeController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class, ConcurrentModificationExceptionHandler.class })
class VolumeControllerTest {

	private static final UUID CONTENT_ID = UUID.fromString("9c1d2e3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f");

	private static final Instant CREATED = Instant.parse("2026-10-13T08:00:00Z");

	private static final User NAMI = new User(UUID.fromString("11111111-1111-1111-1111-111111111111"), "nami",
			"nami@onepiece.local");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VolumeService service;

	@MockitoBean
	private VolumeLinks links;

	@MockitoBean
	private ImageResponseMapper images;

	@Test
	void aVersionSaysItsCoverAsAnIdAndAUrlAndItsExtras() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, "abc"), Set.of());
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1))).thenReturn(access);
		when(this.images.toResponse("abc")).thenReturn(new ImageResponse("abc", "/api/content/images/abc"));

		this.mockMvc.perform(get("/volumes/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.number").value(1))
			.andExpect(jsonPath("$.body.cover.id").value("abc"))
			.andExpect(jsonPath("$.body.cover.url").value("/api/content/images/abc"))
			.andExpect(jsonPath("$.body.japaneseTitle").value("ROMANCE DAWN"))
			.andExpect(jsonPath("$.body.releases[0].country").value("JP"))
			.andExpect(jsonPath("$.body.releases[0].date").value("1997-12-24"))
			.andExpect(jsonPath("$.body.translations.it.title").value("L'alba"));
	}

	@Test
	void aVolumeWithoutACoverSaysNone() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, null), Set.of());
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1))).thenReturn(access);

		this.mockMvc.perform(get("/volumes/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.cover").doesNotExist());
	}

	@Test
	void theListSaysTheNumberTheNamesAndTheCoverOfEachRow() throws Exception {
		var rows = List.of(new ContentSummary<>(CONTENT_ID, version(VersionStatus.PUBLISHED, "abc"), 1, Set.of()));
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), 1));
		when(this.images.toResponse("abc")).thenReturn(new ImageResponse("abc", "/api/content/images/abc"));

		this.mockMvc.perform(get("/volumes").param("q", "alba").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].body.number").value(1))
			.andExpect(jsonPath("$.content[0].body.names.it").value("L'alba"))
			.andExpect(jsonPath("$.content[0].body.cover.id").value("abc"));
	}

	@Test
	void aVolumeTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.get(any(), any(), eq(CONTENT_ID))).thenThrow(new VolumeNotFoundException(CONTENT_ID));

		this.mockMvc.perform(get("/volumes/" + CONTENT_ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VOLUME_NOT_FOUND"));
	}

	@Test
	void theVolumesAChapterMayBeLinkedToAreListedForWhoeverWritesChapters() throws Exception {
		var volumeOne = new NumberedReference(CONTENT_ID, 1, Map.of("it", "L'alba"));
		when(this.links.linkable(any(), anyInt(), anyInt()))
			.thenReturn(new PageImpl<>(List.of(volumeOne), PageRequest.of(1, 5), 6));

		this.mockMvc
			.perform(get("/volumes/linkable").param("q", "alba")
				.param("page", "1")
				.param("size", "5")
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(6))
			.andExpect(jsonPath("$.content[0].id").value(CONTENT_ID.toString()))
			.andExpect(jsonPath("$.content[0].number").value(1))
			.andExpect(jsonPath("$.content[0].names.it").value("L'alba"));
		verify(this.links).linkable("alba", 1, 5);
	}

	@Test
	void theLinkableVolumesAreFirstPageTwentyByDefaultAndNotForWhoOnlyReads() throws Exception {
		when(this.links.linkable(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

		this.mockMvc.perform(get("/volumes/linkable").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk());
		verify(this.links).linkable(null, 0, 20);
		this.mockMvc.perform(get("/volumes/linkable").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(get("/volumes/linkable")).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@CsvSource({ "size,51", "size,0", "page,-1" })
	void aPageOfLinkableVolumesBeyondTheLimitsIsABadRequest(String parameter, String value) throws Exception {
		this.mockMvc
			.perform(get("/volumes/linkable").param(parameter, value).with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
	}

	@Test
	void creatingAVolumeWithoutACoverPassesOnItsExtrasAndAnswersWithTheContent() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));

		this.mockMvc
			.perform(post("/volumes").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"number\":1,\"japaneseTitle\":\"ROMANCE DAWN\","
						+ "\"releases\":[{\"country\":\"JP\",\"date\":\"1997-12-24\"}],"
						+ "\"translations\":{\"it\":{\"title\":\"L'alba\"}}}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()));

		var written = ArgumentCaptor.forClass(Volume.class);
		verify(this.service).create(eq(Set.of(Permission.CONTENT_WRITE)), any(), written.capture(),
				eq(ImageChange.KEEP));
		assertThat(written.getValue().number()).isEqualTo(1);
		assertThat(written.getValue().japaneseTitle()).isEqualTo("ROMANCE DAWN");
		assertThat(written.getValue().releases()).containsExactly(new Release("JP", LocalDate.of(1997, 12, 24)));
		assertThat(written.getValue().translations().get("it")).isEqualTo(new VolumeTranslation("L'alba"));
	}

	@Test
	void creatingWithACoverPassesOnItsBytesWithTheVersionPart() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, "abc"), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));
		byte[] upload = { 1, 2, 3 };

		this.mockMvc
			.perform(multipart("/volumes").file(versionPart("{\"number\":1}"))
				.file(new MockMultipartFile("cover", "cover.png", "image/png", upload))
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()));

		var change = ArgumentCaptor.forClass(ImageChange.class);
		verify(this.service).create(any(), any(), any(), change.capture());
		assertThat(change.getValue()).isInstanceOfSatisfying(ImageChange.Replace.class,
				replace -> assertThat(replace.upload()).isEqualTo(upload));
	}

	@Test
	void editingWithoutAFileKeepsTheCoverAndRemoveImageRemovesIt() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), eq(CONTENT_ID), eq(1), any(), any())).thenReturn(access);

		this.mockMvc
			.perform(multipart(HttpMethod.PUT, "/volumes/" + CONTENT_ID + "/versions/1")
				.file(versionPart("{\"number\":1}"))
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk());
		this.mockMvc.perform(edit("{\"number\":1,\"removeImage\":true}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk());

		verify(this.service).edit(any(), any(), eq(CONTENT_ID), eq(1), any(), eq(ImageChange.KEEP));
		verify(this.service).edit(any(), any(), eq(CONTENT_ID), eq(1), any(), eq(ImageChange.REMOVE));
	}

	@Test
	void aCoverTogetherWithRemoveImageIsUnprocessable() throws Exception {
		this.mockMvc
			.perform(multipart(HttpMethod.PUT, "/volumes/" + CONTENT_ID + "/versions/1")
				.file(versionPart("{\"number\":1,\"removeImage\":true}"))
				.file(new MockMultipartFile("cover", "cover.png", "image/png", new byte[] { 1 }))
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_INVALID"))
			.andExpect(jsonPath("$.errors[0].field").value("image"));
	}

	@Test
	void aRefusedCoverNamesItsReasonAndNumbers() throws Exception {
		when(this.service.create(any(), any(), any(), any())).thenThrow(new ImageRefusedException(
				ContentErrorCode.IMAGE_TOO_SMALL, "The image is too small", Map.of("width", 280, "minWidth", 560)));

		this.mockMvc
			.perform(multipart("/volumes").file(versionPart("{\"number\":1}"))
				.file(new MockMultipartFile("cover", "cover.png", "image/png", new byte[] { 1 }))
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_IMAGE_TOO_SMALL"))
			.andExpect(jsonPath("$.width").value(280))
			.andExpect(jsonPath("$.minWidth").value(560));
	}

	@Test
	void writingIsForbiddenWithoutContentWriteAndUnauthorizedWithoutAToken() throws Exception {
		var readOnly = callerWith(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

		this.mockMvc.perform(create().with(readOnly)).andExpect(status().isForbidden());
		this.mockMvc.perform(edit("{}").with(readOnly)).andExpect(status().isForbidden());
		this.mockMvc.perform(multipart("/volumes").file(versionPart("{}")).with(readOnly))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(create()).andExpect(status().isUnauthorized());
		this.mockMvc.perform(edit("{}")).andExpect(status().isUnauthorized());
		verifyNoInteractions(this.service);
	}

	@Test
	void aRepeatedCountryIsUnprocessableAndNamesItsPosition() throws Exception {
		var invalid = List.of(new FieldViolation("releases[1].country", "has more than one date for the same country"));
		when(this.service.edit(any(), any(), any(), eq(1), any(), any())).thenThrow(new ValueInvalidException(invalid));

		this.mockMvc.perform(edit("{\"number\":1}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_INVALID"))
			.andExpect(jsonPath("$.errors[0].field").value("releases[1].country"));
	}

	@Test
	void aNumberAlreadyUsedIsUnprocessableAndNamesItsField() throws Exception {
		var taken = List.of(new FieldViolation("number", "is already used by another content"));
		when(this.service.edit(any(), any(), any(), eq(1), any(), any()))
			.thenThrow(new ValueAlreadyUsedException(taken));

		this.mockMvc.perform(edit("{\"number\":3}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_ALREADY_USED"))
			.andExpect(jsonPath("$.errors[0].field").value("number"));
	}

	@Test
	void retiringWhileChaptersAreOnlineInTheVolumeIsAConflictNamingSome() throws Exception {
		var chapter = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");
		var block = new ActionBlock(BlockReason.ONLINE_ITEMS_LINKED,
				Map.of("count", 1L, "chapters", List.of(Map.of("id", chapter, "number", 7))));
		when(this.service.retire(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenThrow(new VersionActionBlockedException(VersionAction.RETIRE, block));

		this.mockMvc
			.perform(post("/volumes/" + CONTENT_ID + "/versions/1/retire").with(callerWith(Permission.CONTENT_RETIRE)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_ACTION_BLOCKED"))
			.andExpect(jsonPath("$.reason").value("ONLINE_ITEMS_LINKED"))
			.andExpect(jsonPath("$.detail.count").value(1))
			.andExpect(jsonPath("$.detail.chapters[0].id").value(chapter.toString()))
			.andExpect(jsonPath("$.detail.chapters[0].number").value(7));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "{\"number\":-1}|number", "{\"japaneseTitle\":\"TOO_LONG_TITLE\"}|japaneseTitle",
					"{\"releases\":[{\"date\":\"1997-12-24\"}]}|releases[0].country",
					"{\"releases\":[{\"country\":\"JP\"}]}|releases[0].date",
					"{\"translations\":{\"en\":{\"title\":\"TOO_LONG_TITLE\"}}}|translations[en].title" })
	void aValueBeyondItsLimitIsABadRequestNamingItsField(String template, String field) throws Exception {
		var body = template.replace("TOO_LONG_TITLE", "x".repeat(201));

		var result = this.mockMvc.perform(edit(body).with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
			.andReturn();

		assertThat(result.getResponse().getContentAsString()).contains(field);
		verifyNoInteractions(this.service);
	}

	@Test
	void aTextAsLongAsItsLimitIsAccepted() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), any(), eq(1), any(), any())).thenReturn(draft);
		var body = "{\"number\":0,\"japaneseTitle\":\"%s\",\"translations\":{\"en\":{\"title\":\"%s\"}}}"
			.formatted("x".repeat(200), "x".repeat(200));

		this.mockMvc.perform(edit(body).with(callerWith(Permission.CONTENT_WRITE))).andExpect(status().isOk());
	}

	private static MockMultipartFile versionPart(String json) {
		return new MockMultipartFile("version", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
	}

	/** A creation, not signed in yet. */
	private static MockHttpServletRequestBuilder create() {
		return post("/volumes").contentType("application/json").content("{}");
	}

	/** A save of version 1 of the content, not signed in yet. */
	private static MockHttpServletRequestBuilder edit(String body) {
		return put("/volumes/" + CONTENT_ID + "/versions/1").contentType("application/json").content(body);
	}

	private static Version<Volume> version(VersionStatus status, String coverImageId) {
		var body = Volume.builder()
			.number(1)
			.coverImageId(coverImageId)
			.japaneseTitle("ROMANCE DAWN")
			.releases(List.of(new Release("JP", LocalDate.of(1997, 12, 24))))
			.translations(Map.of("it", new VolumeTranslation("L'alba")))
			.build();
		return Version.<Volume>builder()
			.number(1)
			.status(status)
			.author(NAMI)
			.body(body)
			.createdAt(CREATED)
			.updatedAt(CREATED)
			.build();
	}

}
