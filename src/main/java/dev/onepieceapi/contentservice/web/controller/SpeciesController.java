package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.service.species.SpeciesLinks;
import dev.onepieceapi.contentservice.service.species.SpeciesService;
import dev.onepieceapi.contentservice.web.dto.request.LinkableTypesRequest;
import dev.onepieceapi.contentservice.web.dto.response.SpeciesReferenceResponse;
import dev.onepieceapi.contentservice.service.story.FirstAppearanceLinks;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.SpeciesRequest;
import dev.onepieceapi.contentservice.web.dto.request.ContentListRequest;
import dev.onepieceapi.contentservice.web.dto.response.SpeciesNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.SpeciesResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.mapper.SpeciesRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.SpeciesResponseMapper;
import dev.onepieceapi.contentservice.web.mapper.ContentRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.ContentResponseMapper;
import dev.onepieceapi.contentservice.web.mapper.ImageResponseMapper;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import dev.onepieceapi.contentservice.web.validation.SortableBy;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The Species section: every endpoint of {@link ContentController}, plus creating a
 * content and editing its draft (UF-CNT-01, UF-CNT-02), which take {@code content:write},
 * with or without its picture. Plus the linkable species a character can name (D7).
 */
@RestController
@RequestMapping(ApiPaths.SPECIES)
@Tag(name = "Species")
class SpeciesController extends ContentController<Species, SpeciesResponse, SpeciesNamesResponse> {

	/** The parts of a save with a picture: the version as JSON, and the file. */
	private static final String VERSION_PART = "version";

	private static final String IMAGE_PART = "image";

	private final SpeciesService service;

	private final SpeciesLinks links;

	@Autowired
	SpeciesController(SpeciesService service, SpeciesLinks links, FirstAppearanceLinks firstAppearances,
			ImageResponseMapper images) {
		super(service, access -> SpeciesResponseMapper.toVersionResponse(access,
				firstAppearances.referencesFor(List.of(access.body())), images));
		this.service = service;
		this.links = links;
	}

	/**
	 * One page of the list. A sort by {@code name} reads the name in the language of the
	 * standard {@code Accept-Language} header, the one the caller is reading in.
	 */
	@GetMapping(ApiPaths.CONTENT_LIST)
	PageResponse<ContentSummaryResponse<SpeciesNamesResponse>> list(@ParameterObject @Valid ContentListRequest request,
			@ParameterObject @SortableBy(ContentSortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller, Locale locale) {
		return listPage(ContentRequestMapper.toFilter(request), pageable, caller, locale);
	}

	@Override
	protected List<ContentSummaryResponse<SpeciesNamesResponse>> summaryResponses(List<ContentSummary<Species>> rows,
			AuthenticatedCaller caller) {
		return rows.stream().map(SpeciesResponseMapper::toSummaryResponse).toList();
	}

	/**
	 * The species a character may be linked to: those with a version that passed review,
	 * by romaji, narrowed by what their romaji or a name contains.
	 */
	@GetMapping(ApiPaths.CONTENT_LINKABLE)
	PageResponse<SpeciesReferenceResponse> linkable(@ParameterObject @Valid LinkableTypesRequest request) {
		var page = this.links.linkable(request.q(), request.pageOrDefault(), request.sizeOrDefault());
		return PageResponse.from(page.map(SpeciesResponseMapper::toResponse));
	}

	/**
	 * A new content with its first draft, written by the caller (UF-CNT-01), without an
	 * picture. Answers with the content, so its id is known.
	 */
	@PostMapping(path = ApiPaths.CONTENT_LIST, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse create(@RequestBody @Valid SpeciesRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return create(request, null, caller);
	}

	/**
	 * The same, with a picture (implementation plan of the Species, D5): part
	 * {@code version} is the JSON, part {@code image} the file. Text and picture are
	 * saved together or not at all.
	 */
	@PostMapping(path = ApiPaths.CONTENT_LIST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse createWithImage(@RequestPart(VERSION_PART) @Valid SpeciesRequest request,
			@RequestPart(name = IMAGE_PART, required = false) MultipartFile image,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return create(request, image, caller);
	}

	/**
	 * Replaces what a draft says (UF-CNT-02), keeping its picture unless
	 * {@code removeImage} is set; answers with the version as it now is.
	 */
	@PutMapping(path = ApiPaths.CONTENT_VERSION, consumes = MediaType.APPLICATION_JSON_VALUE)
	VersionResponse<SpeciesResponse> edit(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid SpeciesRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		return edit(id, number, request, null, caller);
	}

	/**
	 * The same, with a picture replacing the draft's: part {@code version} is the JSON,
	 * part {@code image} the file; without the file, as {@link #edit}.
	 */
	@PutMapping(path = ApiPaths.CONTENT_VERSION, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	VersionResponse<SpeciesResponse> editWithImage(@PathVariable UUID id, @PathVariable int number,
			@RequestPart(VERSION_PART) @Valid SpeciesRequest request,
			@RequestPart(name = IMAGE_PART, required = false) MultipartFile image,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return edit(id, number, request, image, caller);
	}

	private ContentResponse create(SpeciesRequest request, MultipartFile image, AuthenticatedCaller caller) {
		Species written = SpeciesRequestMapper.toDomain(request);
		ImageChange change = SpeciesRequestMapper.toImageChange(request, image);
		var content = this.service.create(caller.permissions(), caller.user(), written, change);
		return ContentResponseMapper.toContentResponse(content);
	}

	private VersionResponse<SpeciesResponse> edit(UUID id, int number, SpeciesRequest request, MultipartFile image,
			AuthenticatedCaller caller) {
		Species written = SpeciesRequestMapper.toDomain(request);
		ImageChange change = SpeciesRequestMapper.toImageChange(request, image);
		VersionAccess<Species> version = this.service.edit(caller.permissions(), caller.user(), id, number, written,
				change);
		return versionResponse(version);
	}

}
