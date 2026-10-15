package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReference;
import dev.onepieceapi.contentservice.domain.event.Event;
import dev.onepieceapi.contentservice.domain.event.EventTime.ChapterTime;
import dev.onepieceapi.contentservice.domain.event.EventTime.WorldDateTime;
import dev.onepieceapi.contentservice.domain.event.WorldDate;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
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
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitLinks;
import dev.onepieceapi.contentservice.service.event.EventService;
import dev.onepieceapi.contentservice.service.exception.EventNotFoundException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.story.ChapterLinks;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
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
 * The event section over HTTP (TL2, ADR-0009): what the workflow endpoints it shares with
 * every entity are tested by {@link DevilFruitTypeControllerTest}, here what is its own -
 * a time as a chapter or a world date, written and answered; every other shape refused
 * before the service; the participants; the list narrowed to a fruit; a link or an order
 * refused and an action blocked.
 */
@WebMvcTest(EventController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class, ConcurrentModificationExceptionHandler.class })
class EventControllerTest {

	private static final UUID CONTENT_ID = UUID.fromString("9c1d2e3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f");

	private static final UUID CHAPTER_ID = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID GOMU_ID = UUID.fromString("1b2c3d4e-5f60-4718-8a9b-0c1d2e3f4a5b");

	private static final Instant CREATED = Instant.parse("2026-10-15T08:00:00Z");

	private static final User NAMI = new User(UUID.fromString("11111111-1111-1111-1111-111111111111"), "nami",
			"nami@onepiece.local");

	private static final NumberedReference ROMANCE_DAWN = new NumberedReference(CHAPTER_ID, 1,
			Map.of("it", "Romance Dawn"));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EventService service;

	@MockitoBean
	private ChapterLinks chapters;

	@MockitoBean
	private DevilFruitLinks fruits;

	@Test
	void aVersionSaysItsTimesItsRevealAndItsParticipantsAsTheyAreToday() throws Exception {
		var event = event().toBuilder()
			.endsAt(new WorldDateTime(new WorldDate(1498, 1500, true)))
			.revealedAt(new StoryPoint(CHAPTER_ID))
			.devilFruitContentIds(List.of(GOMU_ID))
			.build();
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenReturn(new VersionAccess<>(version(event), Set.of()));
		when(this.chapters.referencesOf(any())).thenReturn(Map.of(CHAPTER_ID, ROMANCE_DAWN));
		when(this.fruits.referencesOf(any()))
			.thenReturn(Map.of(GOMU_ID, new DevilFruitReference(GOMU_ID, "Gomu Gomu", Map.of("it", "Gom Gom"))));

		this.mockMvc.perform(get("/events/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.occursAt.chapter.number").value(1))
			.andExpect(jsonPath("$.body.occursAt.worldDate").doesNotExist())
			.andExpect(jsonPath("$.body.endsAt.chapter").doesNotExist())
			.andExpect(jsonPath("$.body.endsAt.worldDate.yearFrom").value(1498))
			.andExpect(jsonPath("$.body.endsAt.worldDate.yearTo").value(1500))
			.andExpect(jsonPath("$.body.endsAt.worldDate.approximate").value(true))
			.andExpect(jsonPath("$.body.revealedAt.chapter.names.it").value("Romance Dawn"))
			.andExpect(jsonPath("$.body.participants.devilFruits[0].romaji").value("Gomu Gomu"))
			.andExpect(jsonPath("$.body.translations.en.title").value("Reverie"));
	}

	@Test
	void anEventWithNoRevealWrittenSaysNone() throws Exception {
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenReturn(new VersionAccess<>(version(event()), Set.of()));
		when(this.chapters.referencesOf(any())).thenReturn(Map.of(CHAPTER_ID, ROMANCE_DAWN));

		this.mockMvc.perform(get("/events/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.revealedAt").doesNotExist())
			.andExpect(jsonPath("$.body.endsAt").doesNotExist())
			.andExpect(jsonPath("$.body.participants.devilFruits").isEmpty());
	}

	@Test
	void aRowSaysItsTitlesAndWhenItHappens() throws Exception {
		var rows = List.of(new ContentSummary<>(CONTENT_ID, version(event()), 1, Set.of()));
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), 1));
		when(this.chapters.referencesFor(any(), any())).thenReturn(Map.of(CHAPTER_ID, ROMANCE_DAWN));

		this.mockMvc.perform(get("/events").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].body.names.en").value("Reverie"))
			.andExpect(jsonPath("$.content[0].body.occursAt.chapter.number").value(1));
	}

	@Test
	void theListIsNarrowedToTheEventsOfAFruit() throws Exception {
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		this.mockMvc
			.perform(get("/events").param("devilFruit", GOMU_ID.toString()).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk());

		var filter = ArgumentCaptor.forClass(ContentFilter.class);
		verify(this.service).list(any(), any(), filter.capture(), any(), any());
		assertThat(filter.getValue().related()).containsExactly(Map.entry("devilFruit", GOMU_ID));
	}

	@Test
	void anEventTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.get(any(), any(), eq(CONTENT_ID))).thenThrow(new EventNotFoundException(CONTENT_ID));

		this.mockMvc.perform(get("/events/" + CONTENT_ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_EVENT_NOT_FOUND"));
	}

	@Test
	void creatingAnEventPassesOnEveryPartAsWritten() throws Exception {
		var draft = new VersionAccess<>(version(event()), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));

		this.mockMvc
			.perform(post("/events").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"occursAt\":{\"worldDate\":{\"yearFrom\":1498}},\"endsAt\":{\"chapter\":\"" + CHAPTER_ID
						+ "\"},\"revealedAt\":{\"chapter\":\"" + CHAPTER_ID + "\"},"
						+ "\"participants\":{\"devilFruits\":[\"" + GOMU_ID + "\"]},"
						+ "\"translations\":{\"en\":{\"title\":\"Reverie\"}}}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()));

		var written = ArgumentCaptor.forClass(Event.class);
		verify(this.service).create(eq(Set.of(Permission.CONTENT_WRITE)), any(), written.capture());
		assertThat(written.getValue().occursAt()).isEqualTo(new WorldDateTime(new WorldDate(1498, null, false)));
		assertThat(written.getValue().endsAt()).isEqualTo(new ChapterTime(CHAPTER_ID));
		assertThat(written.getValue().revealedAt()).isEqualTo(new StoryPoint(CHAPTER_ID));
		assertThat(written.getValue().devilFruitContentIds()).containsExactly(GOMU_ID);
		assertThat(written.getValue().translations().get("en")).isEqualTo(new StoryTranslation("Reverie", null));
	}

	@Test
	void anEventWrittenWithNothingIsADraftWithNothing() throws Exception {
		var draft = new VersionAccess<>(version(event()), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));

		this.mockMvc.perform(create("{}").with(callerWith(Permission.CONTENT_WRITE))).andExpect(status().isCreated());

		var written = ArgumentCaptor.forClass(Event.class);
		verify(this.service).create(any(), any(), written.capture());
		assertThat(written.getValue().occursAt()).isNull();
		assertThat(written.getValue().revealedAt()).isEqualTo(StoryPoint.NONE);
		assertThat(written.getValue().devilFruitContentIds()).isEmpty();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "{\"occursAt\":{}}|occursAt",
					"{\"occursAt\":{\"chapter\":\"CHAPTER\",\"worldDate\":{\"yearFrom\":1}}}|occursAt",
					"{\"endsAt\":{}}|endsAt", "{\"occursAt\":{\"worldDate\":{}}}|occursAt.worldDate.yearFrom",
					"{\"occursAt\":{\"worldDate\":{\"yearFrom\":1500,\"yearTo\":1499}}}|occursAt.worldDate.yearTo",
					"{\"participants\":{\"devilFruits\":[null]}}|participants.devilFruits[0]" })
	void aTimeOfAnyOtherShapeIsABadRequestNamingItsField(String template, String field) throws Exception {
		var result = this.mockMvc
			.perform(
					edit(template.replace("CHAPTER", CHAPTER_ID.toString())).with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
			.andReturn();

		assertThat(result.getResponse().getContentAsString()).contains("\"" + field + "\"");
		verifyNoInteractions(this.service);
	}

	@Test
	void writingIsForbiddenWithoutContentWriteAndUnauthorizedWithoutAToken() throws Exception {
		var readOnly = callerWith(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

		this.mockMvc.perform(create("{}").with(readOnly)).andExpect(status().isForbidden());
		this.mockMvc.perform(edit("{}").with(readOnly)).andExpect(status().isForbidden());
		this.mockMvc.perform(create("{}")).andExpect(status().isUnauthorized());
		verifyNoInteractions(this.service);
	}

	@Test
	void aParticipantThatCannotBeLinkedIsUnprocessableAndNamesTheFruit() throws Exception {
		var field = "participants.devilFruits[" + GOMU_ID + "]";
		var invalid = List.of(new FieldViolation(field, "must be a Devil Fruit with an approved version"));
		when(this.service.edit(any(), any(), any(), eq(1), any())).thenThrow(new ValueInvalidException(invalid));

		this.mockMvc.perform(edit("{}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_INVALID"))
			.andExpect(jsonPath("$.errors[0].field").value(field));
	}

	@Test
	void publishingBeforeAParticipantFirstAppearsIsAConflictListingTheFruits() throws Exception {
		var listed = List.of(Map.of("id", GOMU_ID, "romaji", "Gomu Gomu"));
		var block = new ActionBlock(BlockReason.REVEALED_BEFORE_PARTICIPANT, Map.of("count", 1L, "fruits", listed));
		when(this.service.publish(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenThrow(new VersionActionBlockedException(VersionAction.PUBLISH, block));

		this.mockMvc
			.perform(post("/events/" + CONTENT_ID + "/versions/1/publish").with(callerWith(Permission.CONTENT_PUBLISH)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.reason").value("REVEALED_BEFORE_PARTICIPANT"))
			.andExpect(jsonPath("$.detail.fruits[0].romaji").value("Gomu Gomu"));
	}

	/** A creation, not signed in yet. */
	private static MockHttpServletRequestBuilder create(String body) {
		return post("/events").contentType("application/json").content(body);
	}

	/** A save of version 1 of the content, not signed in yet. */
	private static MockHttpServletRequestBuilder edit(String body) {
		return put("/events/" + CONTENT_ID + "/versions/1").contentType("application/json").content(body);
	}

	private static Event event() {
		return Event.builder()
			.occursAt(new ChapterTime(CHAPTER_ID))
			.translations(Map.of("en", new StoryTranslation("Reverie", null)))
			.build();
	}

	private static Version<Event> version(Event body) {
		return Version.<Event>builder()
			.number(1)
			.status(VersionStatus.DRAFT)
			.author(NAMI)
			.body(body)
			.createdAt(CREATED)
			.updatedAt(CREATED)
			.build();
	}

}
