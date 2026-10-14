package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.story.Release;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.BlockedAction;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.service.exception.ChapterNotFoundException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.story.ArcLinks;
import dev.onepieceapi.contentservice.service.story.ChapterService;
import dev.onepieceapi.contentservice.service.story.VolumeLinks;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The chapter section over HTTP: what the workflow endpoints it shares with every entity
 * are tested by {@link DevilFruitTypeControllerTest}, here what is its own - the arc and
 * the volume a chapter points to, as they are today, in a version and in a row; the list
 * narrowed to an arc or a volume; the extras (Japanese title, page count, releases); a
 * link refused or an action blocked; the limits of what a draft is saved with; and who
 * may write.
 */
@WebMvcTest(ChapterController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class, ConcurrentModificationExceptionHandler.class })
class ChapterControllerTest {

	private static final UUID CONTENT_ID = UUID.fromString("9c1d2e3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f");

	private static final UUID ARC_ID = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID VOLUME_ID = UUID.fromString("5a1d9c2e-77b4-4d3a-8e0f-2c6b1a9d4e22");

	private static final Instant CREATED = Instant.parse("2026-10-13T08:00:00Z");

	private static final User NAMI = new User(UUID.fromString("11111111-1111-1111-1111-111111111111"), "nami",
			"nami@onepiece.local");

	private static final NumberedReference ROMANCE_DAWN = new NumberedReference(ARC_ID, 1,
			Map.of("it", "Romance Dawn"));

	private static final NumberedReference VOLUME_ONE = new NumberedReference(VOLUME_ID, 1,
			Map.of("it", "Romance Dawn"));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ChapterService service;

	@MockitoBean
	private ArcLinks arcs;

	@MockitoBean
	private VolumeLinks volumes;

	@Test
	void aVersionSaysItsArcAndVolumeAsTheyAreTodayAndItsExtrasAndWhichActionsAreBlocked() throws Exception {
		var blocked = new BlockedAction(VersionAction.PUBLISH, BlockReason.RELATED_NOT_ONLINE,
				Map.of("arcId", ARC_ID, "arcNumber", 1));
		var access = new VersionAccess<>(version(VersionStatus.READY_TO_PUBLISH, ARC_ID, VOLUME_ID),
				EnumSet.of(VersionAction.PUBLISH), EnumSet.noneOf(VersionAction.class), List.of(blocked));
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1))).thenReturn(access);
		when(this.arcs.referencesFor(any(), any())).thenReturn(Map.of(ARC_ID, ROMANCE_DAWN));
		when(this.volumes.referencesFor(any(), any())).thenReturn(Map.of(VOLUME_ID, VOLUME_ONE));

		read("/chapters/" + CONTENT_ID + "/versions/1").andExpect(jsonPath("$.body.number").value(1))
			.andExpect(jsonPath("$.body.arc.id").value(ARC_ID.toString()))
			.andExpect(jsonPath("$.body.arc.number").value(1))
			.andExpect(jsonPath("$.body.arc.names.it").value("Romance Dawn"))
			.andExpect(jsonPath("$.body.volume.id").value(VOLUME_ID.toString()))
			.andExpect(jsonPath("$.body.japaneseTitle").value("ROMANCE DAWN"))
			.andExpect(jsonPath("$.body.pageCount").value(54))
			.andExpect(jsonPath("$.body.releases[0].country").value("JP"))
			.andExpect(jsonPath("$.body.releases[0].date").value("1997-07-22"))
			.andExpect(jsonPath("$.body.translations.it.title").value("L'alba"))
			.andExpect(jsonPath("$.allowedActions[0]").value("PUBLISH"))
			.andExpect(jsonPath("$.blockedActions[0].reason").value("RELATED_NOT_ONLINE"))
			.andExpect(jsonPath("$.blockedActions[0].detail.arcId").value(ARC_ID.toString()));
	}

	@Test
	void aVersionWithoutAVolumeSaysNoneAndALinkWithNothingApprovedIsAnsweredByItsIdAlone() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, ARC_ID, null), Set.of());
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1))).thenReturn(access);
		when(this.arcs.referencesFor(any(), any())).thenReturn(Map.of());
		when(this.volumes.referencesFor(any(), any())).thenReturn(Map.of());

		read("/chapters/" + CONTENT_ID + "/versions/1").andExpect(jsonPath("$.body.volume").doesNotExist())
			.andExpect(jsonPath("$.body.arc.id").value(ARC_ID.toString()))
			.andExpect(jsonPath("$.body.arc.number").doesNotExist());
	}

	@Test
	void theListLooksUpTheArcsAndVolumesOfAWholePageAtOnce() throws Exception {
		var rows = List.of(
				new ContentSummary<>(CONTENT_ID, version(VersionStatus.PUBLISHED, ARC_ID, VOLUME_ID), 1, Set.of()),
				new ContentSummary<>(UUID.randomUUID(), version(VersionStatus.DRAFT, ARC_ID, null), null, Set.of()));
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), 2));
		when(this.arcs.referencesFor(any(), any())).thenReturn(Map.of(ARC_ID, ROMANCE_DAWN));
		when(this.volumes.referencesFor(any(), any())).thenReturn(Map.of(VOLUME_ID, VOLUME_ONE));

		read("/chapters").andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].body.number").value(1))
			.andExpect(jsonPath("$.content[0].body.names.it").value("L'alba"))
			.andExpect(jsonPath("$.content[0].body.arc.number").value(1))
			.andExpect(jsonPath("$.content[0].body.volume.number").value(1))
			.andExpect(jsonPath("$.content[1].body.arc.number").value(1))
			.andExpect(jsonPath("$.content[1].body.volume").doesNotExist());
		verify(this.arcs).referencesFor(any(), any());
		verify(this.volumes).referencesFor(any(), any());
	}

	@Test
	void theListIsNarrowedToAnArcAndAVolumeByTheirIdsAndCanBeSortedByNumber() throws Exception {
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		this.mockMvc
			.perform(get("/chapters").param("arc", ARC_ID.toString())
				.param("volume", VOLUME_ID.toString())
				.param("q", "alba")
				.param("sort", "number,asc")
				.with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk());

		var filter = ArgumentCaptor.forClass(ContentFilter.class);
		var pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(this.service).list(any(), any(), filter.capture(), pageable.capture(), any());
		assertThat(filter.getValue().related()).containsOnly(Map.entry("arc", ARC_ID), Map.entry("volume", VOLUME_ID));
		assertThat(filter.getValue().query()).isEqualTo("alba");
		assertThat(pageable.getValue().getSort().getOrderFor("number")).isNotNull();
	}

	@Test
	void theListNarrowedToAVolumeAloneNamesNoArc() throws Exception {
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		this.mockMvc
			.perform(get("/chapters").param("volume", VOLUME_ID.toString()).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk());

		var filter = ArgumentCaptor.forClass(ContentFilter.class);
		verify(this.service).list(any(), any(), filter.capture(), any(), any());
		assertThat(filter.getValue().related()).containsExactly(Map.entry("volume", VOLUME_ID));
	}

	@Test
	void anArcOrAVolumeThatIsNotAnIdIsABadRequest() throws Exception {
		this.mockMvc.perform(get("/chapters").param("arc", "romance-dawn").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/chapters").param("volume", "1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(this.service);
	}

	@Test
	void aChapterTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.get(any(), any(), eq(CONTENT_ID))).thenThrow(new ChapterNotFoundException(CONTENT_ID));

		this.mockMvc.perform(get("/chapters/" + CONTENT_ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_CHAPTER_NOT_FOUND"));
	}

	@Test
	void creatingAChapterPassesOnItsLinksAndExtrasAndAnswersWithTheContent() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, ARC_ID, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));

		this.mockMvc
			.perform(post("/chapters").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"number\":1,\"arc\":\"" + ARC_ID + "\",\"volume\":\"" + VOLUME_ID
						+ "\",\"japaneseTitle\":\"ROMANCE DAWN\",\"pageCount\":54,"
						+ "\"releases\":[{\"country\":\"JP\",\"date\":\"1997-07-22\"}],"
						+ "\"translations\":{\"it\":{\"title\":\"L'alba\"}}}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()));

		var written = ArgumentCaptor.forClass(Chapter.class);
		verify(this.service).create(eq(Set.of(Permission.CONTENT_WRITE)), any(), written.capture());
		assertThat(written.getValue().number()).isEqualTo(1);
		assertThat(written.getValue().arcContentId()).isEqualTo(ARC_ID);
		assertThat(written.getValue().volumeContentId()).isEqualTo(VOLUME_ID);
		assertThat(written.getValue().japaneseTitle()).isEqualTo("ROMANCE DAWN");
		assertThat(written.getValue().pageCount()).isEqualTo(54);
		assertThat(written.getValue().releases()).containsExactly(new Release("JP", LocalDate.of(1997, 7, 22)));
		assertThat(written.getValue().translations().get("it")).isEqualTo(new StoryTranslation("L'alba", null));
	}

	@Test
	void editingAnswersWithTheVersionAndItsLinks() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, ARC_ID, VOLUME_ID),
				EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), eq(CONTENT_ID), eq(1), any())).thenReturn(access);
		when(this.arcs.referencesFor(any(), any())).thenReturn(Map.of(ARC_ID, ROMANCE_DAWN));
		when(this.volumes.referencesFor(any(), any())).thenReturn(Map.of(VOLUME_ID, VOLUME_ONE));

		this.mockMvc.perform(edit("{\"number\":1}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.arc.number").value(1))
			.andExpect(jsonPath("$.body.volume.number").value(1));
	}

	@Test
	void writingIsForbiddenWithoutContentWriteAndUnauthorizedWithoutAToken() throws Exception {
		var readOnly = callerWith(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

		this.mockMvc.perform(create().with(readOnly)).andExpect(status().isForbidden());
		this.mockMvc.perform(edit("{}").with(readOnly)).andExpect(status().isForbidden());
		this.mockMvc.perform(create()).andExpect(status().isUnauthorized());
		this.mockMvc.perform(edit("{}")).andExpect(status().isUnauthorized());
		verifyNoInteractions(this.service);
	}

	@ParameterizedTest
	@CsvSource({ "arc", "volume" })
	void aLinkThatCannotBeChosenIsUnprocessableAndNamesIt(String field) throws Exception {
		var invalid = List.of(new FieldViolation(field, "must be linkable"));
		when(this.service.create(any(), any(), any())).thenThrow(new ValueInvalidException(invalid));

		this.mockMvc.perform(create().with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_INVALID"))
			.andExpect(jsonPath("$.errors[0].field").value(field));
	}

	@Test
	void aRepeatedCountryIsUnprocessableAndNamesItsPosition() throws Exception {
		var invalid = List.of(new FieldViolation("releases[1].country", "has more than one date for the same country"));
		when(this.service.edit(any(), any(), any(), eq(1), any())).thenThrow(new ValueInvalidException(invalid));

		this.mockMvc.perform(edit("{\"number\":1}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("releases[1].country"));
	}

	@Test
	void aNumberAlreadyUsedIsUnprocessableAndNamesItsField() throws Exception {
		var taken = List.of(new FieldViolation("number", "is already used by another content"));
		when(this.service.edit(any(), any(), any(), eq(1), any())).thenThrow(new ValueAlreadyUsedException(taken));

		this.mockMvc.perform(edit("{\"number\":3}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_ALREADY_USED"))
			.andExpect(jsonPath("$.errors[0].field").value("number"));
	}

	@Test
	void publishingWhileTheArcIsNotOnlineIsAConflictNamingTheArc() throws Exception {
		var block = new ActionBlock(BlockReason.RELATED_NOT_ONLINE, Map.of("arcId", ARC_ID, "arcNumber", 1));
		when(this.service.publish(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenThrow(new VersionActionBlockedException(VersionAction.PUBLISH, block));

		this.mockMvc
			.perform(post("/chapters/" + CONTENT_ID + "/versions/1/publish")
				.with(callerWith(Permission.CONTENT_PUBLISH)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_ACTION_BLOCKED"))
			.andExpect(jsonPath("$.reason").value("RELATED_NOT_ONLINE"))
			.andExpect(jsonPath("$.detail.arcId").value(ARC_ID.toString()));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "{\"number\":-1}|number", "{\"pageCount\":0}|pageCount",
			"{\"pageCount\":-5}|pageCount", "{\"japaneseTitle\":\"TOO_LONG_TITLE\"}|japaneseTitle",
			"{\"releases\":[{\"date\":\"1997-07-22\"}]}|releases[0].country",
			"{\"releases\":[{\"country\":\"JP\"}]}|releases[0].date",
			"{\"translations\":{\"en\":{\"title\":\"TOO_LONG_TITLE\"}}}|translations[en].title",
			"{\"translations\":{\"en\":{\"description\":\"TOO_LONG_DESCRIPTION\"}}}|translations[en].description" })
	void aValueBeyondItsLimitIsABadRequestNamingItsField(String template, String field) throws Exception {
		var body = template.replace("TOO_LONG_TITLE", "x".repeat(201))
			.replace("TOO_LONG_DESCRIPTION", "x".repeat(2001));

		var result = this.mockMvc.perform(edit(body).with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
			.andReturn();

		assertThat(result.getResponse().getContentAsString()).contains(field);
		verifyNoInteractions(this.service);
	}

	@Test
	void aTextAsLongAsItsLimitIsAccepted() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, ARC_ID, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), any(), eq(1), any())).thenReturn(draft);
		var body = "{\"number\":0,\"pageCount\":1,\"japaneseTitle\":\"%s\","
				+ "\"translations\":{\"en\":{\"title\":\"%s\",\"description\":\"%s\"}}}".formatted("x".repeat(200),
						"x".repeat(200), "x".repeat(2000));

		this.mockMvc.perform(edit(body).with(callerWith(Permission.CONTENT_WRITE))).andExpect(status().isOk());
	}

	private ResultActions read(String path) throws Exception {
		return this.mockMvc.perform(get(path).with(callerWith(Permission.CONTENT_READ))).andExpect(status().isOk());
	}

	/** A creation, not signed in yet. */
	private static MockHttpServletRequestBuilder create() {
		return post("/chapters").contentType("application/json").content("{}");
	}

	/** A save of version 1 of the content, not signed in yet. */
	private static MockHttpServletRequestBuilder edit(String body) {
		return put("/chapters/" + CONTENT_ID + "/versions/1").contentType("application/json").content(body);
	}

	private static Version<Chapter> version(VersionStatus status, UUID arc, UUID volume) {
		var body = Chapter.builder()
			.number(1)
			.arcContentId(arc)
			.volumeContentId(volume)
			.japaneseTitle("ROMANCE DAWN")
			.pageCount(54)
			.releases(List.of(new Release("JP", LocalDate.of(1997, 7, 22))))
			.translations(Map.of("it", new StoryTranslation("L'alba", "Inizio")))
			.build();
		return Version.<Chapter>builder()
			.number(1)
			.status(status)
			.author(NAMI)
			.body(body)
			.createdAt(CREATED)
			.updatedAt(CREATED)
			.build();
	}

}
