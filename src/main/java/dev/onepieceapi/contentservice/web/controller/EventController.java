package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.event.Event;
import dev.onepieceapi.contentservice.domain.event.EventSortField;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitLinks;
import dev.onepieceapi.contentservice.service.event.EventService;
import dev.onepieceapi.contentservice.service.story.ChapterLinks;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.EventListRequest;
import dev.onepieceapi.contentservice.web.dto.request.EventRequest;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.EventNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.EventResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.mapper.ContentResponseMapper;
import dev.onepieceapi.contentservice.web.mapper.EventRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.EventResponseMapper;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import dev.onepieceapi.contentservice.web.validation.SortableBy;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The event section (implementation plan of the timeline, TL2): every endpoint of
 * {@link ContentController}, plus creating a content and editing its draft (UF-CNT-01,
 * UF-CNT-02), which take {@code content:write}, and the list narrowed to the events of a
 * Devil Fruit. An event has no natural key: it is addressed by its id only. The chapters
 * and the fruits it points to are answered as they are today, looked up once for the
 * whole page.
 */
@RestController
@RequestMapping(ApiPaths.EVENTS)
@Tag(name = "Events")
class EventController extends ContentController<Event, EventResponse, EventNamesResponse> {

	private final EventService service;

	private final ChapterLinks chapters;

	@Autowired
	EventController(EventService service, ChapterLinks chapters, DevilFruitLinks fruits) {
		super(service,
				access -> EventResponseMapper.toVersionResponse(access,
						chapters.referencesOf(chaptersOf(access.body())),
						fruits.referencesOf(access.body().devilFruitContentIds())));
		this.service = service;
		this.chapters = chapters;
	}

	/**
	 * One page of the list, narrowed to the events of a Devil Fruit when asked. A search
	 * reads the titles; with no sort asked for, the events come in the order of the world
	 * ({@code occursAt}). A sort by {@code name} reads the title in the language of the
	 * standard {@code Accept-Language} header.
	 */
	@GetMapping(ApiPaths.CONTENT_LIST)
	PageResponse<ContentSummaryResponse<EventNamesResponse>> list(@ParameterObject @Valid EventListRequest request,
			@ParameterObject @SortableBy(EventSortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller, Locale locale) {
		return listPage(EventRequestMapper.toFilter(request), pageable, caller, locale);
	}

	@Override
	protected List<ContentSummaryResponse<EventNamesResponse>> summaryResponses(List<ContentSummary<Event>> rows,
			AuthenticatedCaller caller) {
		var references = this.chapters.referencesFor(rows.stream().map(row -> row.version().body()).toList(),
				Event::occursAtChapterId);
		return rows.stream().map(row -> EventResponseMapper.toSummaryResponse(row, references)).toList();
	}

	/**
	 * A new content with its first draft, written by the caller (UF-CNT-01). Answers with
	 * the content, so its id is known.
	 */
	@PostMapping(ApiPaths.CONTENT_LIST)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse create(@RequestBody @Valid EventRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var content = this.service.create(caller.permissions(), caller.user(), EventRequestMapper.toDomain(request));
		return ContentResponseMapper.toContentResponse(content);
	}

	/** Replaces what a draft says (UF-CNT-02); answers with the version as it now is. */
	@PutMapping(ApiPaths.CONTENT_VERSION)
	VersionResponse<EventResponse> edit(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid EventRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		var version = this.service.edit(caller.permissions(), caller.user(), id, number,
				EventRequestMapper.toDomain(request));
		return versionResponse(version);
	}

	/** The chapters an event points to: where it occurs, ends and is revealed. */
	private static Collection<UUID> chaptersOf(Event event) {
		return Stream.of(event.occursAtChapterId(), event.endsAtChapterId(), event.revealedAtChapterId())
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
	}

}
