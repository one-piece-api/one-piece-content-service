package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.service.story.ArcLinks;
import dev.onepieceapi.contentservice.service.story.ChapterService;
import dev.onepieceapi.contentservice.service.story.VolumeLinks;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.ChapterListRequest;
import dev.onepieceapi.contentservice.web.dto.request.ChapterRequest;
import dev.onepieceapi.contentservice.web.dto.response.ChapterNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.ChapterResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.mapper.ContentResponseMapper;
import dev.onepieceapi.contentservice.web.mapper.StoryRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.StoryResponseMapper;
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

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The chapter section: every endpoint of {@link ContentController}, plus creating a
 * content and editing its draft (UF-CNT-01, UF-CNT-02), which take {@code content:write},
 * and the list narrowed to the chapters of an arc or a volume. The arc and the volume a
 * chapter points to are answered as they are today, looked up once for the whole page.
 */
@RestController
@RequestMapping(ApiPaths.CHAPTERS)
@Tag(name = "Chapters")
class ChapterController extends ContentController<Chapter, ChapterResponse, ChapterNamesResponse> {

	private final ChapterService service;

	private final ArcLinks arcs;

	private final VolumeLinks volumes;

	@Autowired
	ChapterController(ChapterService service, ArcLinks arcs, VolumeLinks volumes) {
		super(service,
				access -> StoryResponseMapper.toVersionResponse(access,
						arcs.referencesFor(List.of(access.body()), Chapter::arcContentId),
						volumes.referencesFor(List.of(access.body()), Chapter::volumeContentId)));
		this.service = service;
		this.arcs = arcs;
		this.volumes = volumes;
	}

	/**
	 * One page of the list, narrowed to the chapters of an arc or a volume when asked;
	 * {@code sort=number} orders it by number. A sort by {@code name} reads the title in
	 * the language of the standard {@code Accept-Language} header.
	 */
	@GetMapping(ApiPaths.CONTENT_LIST)
	PageResponse<ContentSummaryResponse<ChapterNamesResponse>> list(@ParameterObject @Valid ChapterListRequest request,
			@ParameterObject @SortableBy(ContentSortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller, Locale locale) {
		return listPage(StoryRequestMapper.toFilter(request), pageable, caller, locale);
	}

	@Override
	protected List<ContentSummaryResponse<ChapterNamesResponse>> summaryResponses(List<ContentSummary<Chapter>> rows,
			AuthenticatedCaller caller) {
		var bodies = rows.stream().map(row -> row.version().body()).toList();
		var arcReferences = this.arcs.referencesFor(bodies, Chapter::arcContentId);
		var volumeReferences = this.volumes.referencesFor(bodies, Chapter::volumeContentId);
		return rows.stream()
			.map(row -> StoryResponseMapper.toSummaryResponse(row, arcReferences, volumeReferences))
			.toList();
	}

	/**
	 * A new content with its first draft, written by the caller (UF-CNT-01). Answers with
	 * the content, so its id is known.
	 */
	@PostMapping(ApiPaths.CONTENT_LIST)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse create(@RequestBody @Valid ChapterRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var content = this.service.create(caller.permissions(), caller.user(), StoryRequestMapper.toDomain(request));
		return ContentResponseMapper.toContentResponse(content);
	}

	/** Replaces what a draft says (UF-CNT-02); answers with the version as it now is. */
	@PutMapping(ApiPaths.CONTENT_VERSION)
	VersionResponse<ChapterResponse> edit(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid ChapterRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		var version = this.service.edit(caller.permissions(), caller.user(), id, number,
				StoryRequestMapper.toDomain(request));
		return versionResponse(version);
	}

}
