package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.service.story.ArcLinks;
import dev.onepieceapi.contentservice.service.story.EpisodeService;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.EpisodeListRequest;
import dev.onepieceapi.contentservice.web.dto.request.EpisodeRequest;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.EpisodeNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.EpisodeResponse;
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
 * The episode section: every endpoint of {@link ContentController}, plus creating a
 * content and editing its draft (UF-CNT-01, UF-CNT-02), which take {@code content:write},
 * and the list narrowed to the episodes of an arc. The arc an episode points to, if any,
 * is answered as it is today, looked up once for the whole page.
 */
@RestController
@RequestMapping(ApiPaths.EPISODES)
@Tag(name = "Episodes")
class EpisodeController extends ContentController<Episode, EpisodeResponse, EpisodeNamesResponse> {

	private final EpisodeService service;

	private final ArcLinks arcs;

	@Autowired
	EpisodeController(EpisodeService service, ArcLinks arcs) {
		super(service, access -> StoryResponseMapper.toEpisodeVersionResponse(access,
				arcs.referencesFor(List.of(access.body()), Episode::arcContentId)));
		this.service = service;
		this.arcs = arcs;
	}

	/**
	 * One page of the list, narrowed to the episodes of an arc when asked;
	 * {@code sort=number} orders it by number. A sort by {@code name} reads the title in
	 * the language of the standard {@code Accept-Language} header.
	 */
	@GetMapping(ApiPaths.CONTENT_LIST)
	PageResponse<ContentSummaryResponse<EpisodeNamesResponse>> list(@ParameterObject @Valid EpisodeListRequest request,
			@ParameterObject @SortableBy(ContentSortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller, Locale locale) {
		return listPage(StoryRequestMapper.toFilter(request), pageable, caller, locale);
	}

	@Override
	protected List<ContentSummaryResponse<EpisodeNamesResponse>> summaryResponses(List<ContentSummary<Episode>> rows,
			AuthenticatedCaller caller) {
		var references = this.arcs.referencesFor(rows.stream().map(row -> row.version().body()).toList(),
				Episode::arcContentId);
		return rows.stream().map(row -> StoryResponseMapper.toEpisodeSummaryResponse(row, references)).toList();
	}

	/**
	 * A new content with its first draft, written by the caller (UF-CNT-01). Answers with
	 * the content, so its id is known.
	 */
	@PostMapping(ApiPaths.CONTENT_LIST)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse create(@RequestBody @Valid EpisodeRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var content = this.service.create(caller.permissions(), caller.user(), StoryRequestMapper.toDomain(request));
		return ContentResponseMapper.toContentResponse(content);
	}

	/** Replaces what a draft says (UF-CNT-02); answers with the version as it now is. */
	@PutMapping(ApiPaths.CONTENT_VERSION)
	VersionResponse<EpisodeResponse> edit(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid EpisodeRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		var version = this.service.edit(caller.permissions(), caller.user(), id, number,
				StoryRequestMapper.toDomain(request));
		return versionResponse(version);
	}

}
