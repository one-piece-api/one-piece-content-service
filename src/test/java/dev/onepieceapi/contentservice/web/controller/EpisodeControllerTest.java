package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.story.Release;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.service.exception.EpisodeNotFoundException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.story.ArcLinks;
import dev.onepieceapi.contentservice.service.story.EpisodeLinks;
import dev.onepieceapi.contentservice.service.story.EpisodeService;
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
import static org.mockito.ArgumentMatchers.anyInt;
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
 * The episode section over HTTP: what the workflow endpoints it shares with every entity
 * are tested by {@link DevilFruitTypeControllerTest}, here what is its own - the arc an
 * episode points to, if any, as it is today, in a version and in a row; the list narrowed
 * to an arc; the extras (Japanese title, broadcasts); a link refused or an action
 * blocked; the limits of what a draft is saved with; and who may write.
 */
@WebMvcTest(EpisodeController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class, ConcurrentModificationExceptionHandler.class })
class EpisodeControllerTest {

	private static final UUID CONTENT_ID = UUID.fromString("9c1d2e3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f");

	private static final UUID ARC_ID = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final Instant CREATED = Instant.parse("2026-10-13T08:00:00Z");

	private static final User NAMI = new User(UUID.fromString("11111111-1111-1111-1111-111111111111"), "nami",
			"nami@onepiece.local");

	private static final NumberedReference ROMANCE_DAWN = new NumberedReference(ARC_ID, 1,
			Map.of("it", "Romance Dawn"));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EpisodeService service;

	@MockitoBean
	private ArcLinks arcs;

	@MockitoBean
	private EpisodeLinks links;

	@Test
	void theEpisodesAFirstAppearanceMayNameAreListedForWhoeverWrites() throws Exception {
		var luffysPast = new NumberedReference(CONTENT_ID, 1, Map.of("it", "Il passato di Luffy"));
		when(this.links.linkable(any(), anyInt(), anyInt()))
			.thenReturn(new PageImpl<>(List.of(luffysPast), PageRequest.of(1, 5), 6));

		this.mockMvc
			.perform(get("/episodes/linkable").param("q", "luffy")
				.param("page", "1")
				.param("size", "5")
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(6))
			.andExpect(jsonPath("$.content[0].id").value(CONTENT_ID.toString()))
			.andExpect(jsonPath("$.content[0].number").value(1))
			.andExpect(jsonPath("$.content[0].names.it").value("Il passato di Luffy"));
		verify(this.links).linkable("luffy", 1, 5);
	}

	@Test
	void theLinkableEpisodesAreFirstPageTwentyByDefaultAndNotForWhoOnlyReads() throws Exception {
		when(this.links.linkable(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

		this.mockMvc.perform(get("/episodes/linkable").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk());
		verify(this.links).linkable(null, 0, 20);
		this.mockMvc.perform(get("/episodes/linkable").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(get("/episodes/linkable")).andExpect(status().isUnauthorized());
	}

	@Test
	void aVersionSaysItsArcAsItIsTodayAndItsBroadcasts() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, ARC_ID), Set.of());
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1))).thenReturn(access);
		when(this.arcs.referencesFor(any(), any())).thenReturn(Map.of(ARC_ID, ROMANCE_DAWN));

		this.mockMvc.perform(get("/episodes/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.number").value(1))
			.andExpect(jsonPath("$.body.arc.id").value(ARC_ID.toString()))
			.andExpect(jsonPath("$.body.arc.number").value(1))
			.andExpect(jsonPath("$.body.arc.names.it").value("Romance Dawn"))
			.andExpect(jsonPath("$.body.japaneseTitle").value("Ore wa Luffy!"))
			.andExpect(jsonPath("$.body.broadcasts[0].country").value("JP"))
			.andExpect(jsonPath("$.body.broadcasts[0].date").value("1999-10-20"))
			.andExpect(jsonPath("$.body.translations.it.title").value("Sono Rufy!"));
	}

	@Test
	void aFillerSaysNoArc() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, null), Set.of());
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1))).thenReturn(access);
		when(this.arcs.referencesFor(any(), any())).thenReturn(Map.of());

		this.mockMvc.perform(get("/episodes/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.arc").doesNotExist());
	}

	@Test
	void theListLooksUpTheArcsOfAWholePageAtOnce() throws Exception {
		var rows = List.of(new ContentSummary<>(CONTENT_ID, version(VersionStatus.PUBLISHED, ARC_ID), 1, Set.of()),
				new ContentSummary<>(UUID.randomUUID(), version(VersionStatus.DRAFT, null), null, Set.of()));
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), 2));
		when(this.arcs.referencesFor(any(), any())).thenReturn(Map.of(ARC_ID, ROMANCE_DAWN));

		this.mockMvc.perform(get("/episodes").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].body.number").value(1))
			.andExpect(jsonPath("$.content[0].body.names.it").value("Sono Rufy!"))
			.andExpect(jsonPath("$.content[0].body.arc.number").value(1))
			.andExpect(jsonPath("$.content[1].body.arc").doesNotExist());
		verify(this.arcs).referencesFor(any(), any());
	}

	@Test
	void theListIsNarrowedToAnArcByItsIdAndCanBeSortedByNumber() throws Exception {
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		this.mockMvc
			.perform(get("/episodes").param("arc", ARC_ID.toString())
				.param("q", "rufy")
				.param("sort", "number,asc")
				.with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk());

		var filter = ArgumentCaptor.forClass(ContentFilter.class);
		var pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(this.service).list(any(), any(), filter.capture(), pageable.capture(), any());
		assertThat(filter.getValue().related()).containsExactly(Map.entry("arc", ARC_ID));
		assertThat(filter.getValue().query()).isEqualTo("rufy");
		assertThat(pageable.getValue().getSort().getOrderFor("number")).isNotNull();
	}

	@Test
	void anArcThatIsNotAnIdIsABadRequest() throws Exception {
		this.mockMvc.perform(get("/episodes").param("arc", "romance-dawn").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(this.service);
	}

	@Test
	void anEpisodeTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.get(any(), any(), eq(CONTENT_ID))).thenThrow(new EpisodeNotFoundException(CONTENT_ID));

		this.mockMvc.perform(get("/episodes/" + CONTENT_ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_EPISODE_NOT_FOUND"));
	}

	@Test
	void creatingAnEpisodePassesOnItsArcAndBroadcastsAndAnswersWithTheContent() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, ARC_ID), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));

		this.mockMvc
			.perform(post("/episodes").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"number\":1,\"arc\":\"" + ARC_ID + "\",\"japaneseTitle\":\"Ore wa Luffy!\","
						+ "\"broadcasts\":[{\"country\":\"JP\",\"date\":\"1999-10-20\"}],"
						+ "\"translations\":{\"it\":{\"title\":\"Sono Rufy!\"}}}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()));

		var written = ArgumentCaptor.forClass(Episode.class);
		verify(this.service).create(eq(Set.of(Permission.CONTENT_WRITE)), any(), written.capture());
		assertThat(written.getValue().number()).isEqualTo(1);
		assertThat(written.getValue().arcContentId()).isEqualTo(ARC_ID);
		assertThat(written.getValue().japaneseTitle()).isEqualTo("Ore wa Luffy!");
		assertThat(written.getValue().broadcasts()).containsExactly(new Release("JP", LocalDate.of(1999, 10, 20)));
		assertThat(written.getValue().translations().get("it")).isEqualTo(new StoryTranslation("Sono Rufy!", null));
	}

	@Test
	void anEpisodeWithoutAnArcIsCreatedWithNone() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));

		this.mockMvc.perform(create().with(callerWith(Permission.CONTENT_WRITE))).andExpect(status().isCreated());

		var written = ArgumentCaptor.forClass(Episode.class);
		verify(this.service).create(any(), any(), written.capture());
		assertThat(written.getValue().arcContentId()).isNull();
		assertThat(written.getValue().broadcasts()).isEmpty();
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

	@Test
	void anArcThatCannotBeLinkedIsUnprocessableAndNamesTheArc() throws Exception {
		var invalid = List.of(new FieldViolation("arc", "must be an Arc with an approved version"));
		when(this.service.create(any(), any(), any())).thenThrow(new ValueInvalidException(invalid));

		this.mockMvc.perform(create().with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_INVALID"))
			.andExpect(jsonPath("$.errors[0].field").value("arc"));
	}

	@Test
	void aRepeatedCountryIsUnprocessableAndNamesItsPosition() throws Exception {
		var invalid = List
			.of(new FieldViolation("broadcasts[1].country", "has more than one date for the same country"));
		when(this.service.edit(any(), any(), any(), eq(1), any())).thenThrow(new ValueInvalidException(invalid));

		this.mockMvc.perform(edit("{\"number\":1}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("broadcasts[1].country"));
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
			.perform(post("/episodes/" + CONTENT_ID + "/versions/1/publish")
				.with(callerWith(Permission.CONTENT_PUBLISH)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_ACTION_BLOCKED"))
			.andExpect(jsonPath("$.reason").value("RELATED_NOT_ONLINE"))
			.andExpect(jsonPath("$.detail.arcId").value(ARC_ID.toString()));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "{\"number\":-1}|number",
			"{\"japaneseTitle\":\"TOO_LONG_TITLE\"}|japaneseTitle",
			"{\"broadcasts\":[{\"date\":\"1999-10-20\"}]}|broadcasts[0].country",
			"{\"broadcasts\":[{\"country\":\"JP\"}]}|broadcasts[0].date",
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
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), any(), eq(1), any())).thenReturn(draft);
		var body = "{\"number\":0,\"japaneseTitle\":\"%s\","
				+ "\"translations\":{\"en\":{\"title\":\"%s\",\"description\":\"%s\"}}}".formatted("x".repeat(200),
						"x".repeat(200), "x".repeat(2000));

		this.mockMvc.perform(edit(body).with(callerWith(Permission.CONTENT_WRITE))).andExpect(status().isOk());
	}

	/** A creation, not signed in yet. */
	private static MockHttpServletRequestBuilder create() {
		return post("/episodes").contentType("application/json").content("{}");
	}

	/** A save of version 1 of the content, not signed in yet. */
	private static MockHttpServletRequestBuilder edit(String body) {
		return put("/episodes/" + CONTENT_ID + "/versions/1").contentType("application/json").content(body);
	}

	private static Version<Episode> version(VersionStatus status, UUID arc) {
		var body = Episode.builder()
			.number(1)
			.arcContentId(arc)
			.japaneseTitle("Ore wa Luffy!")
			.broadcasts(List.of(new Release("JP", LocalDate.of(1999, 10, 20))))
			.translations(Map.of("it", new StoryTranslation("Sono Rufy!", "Inizio")))
			.build();
		return Version.<Episode>builder()
			.number(1)
			.status(status)
			.author(NAMI)
			.body(body)
			.createdAt(CREATED)
			.updatedAt(CREATED)
			.build();
	}

}
