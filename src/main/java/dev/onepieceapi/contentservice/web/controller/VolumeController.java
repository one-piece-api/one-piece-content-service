package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.story.Volume;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.service.story.VolumeLinks;
import dev.onepieceapi.contentservice.service.story.VolumeService;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.ContentListRequest;
import dev.onepieceapi.contentservice.web.dto.request.LinkableNumberedRequest;
import dev.onepieceapi.contentservice.web.dto.request.VolumeRequest;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.NumberedReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.dto.response.VolumeNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.VolumeResponse;
import dev.onepieceapi.contentservice.web.mapper.ContentResponseMapper;
import dev.onepieceapi.contentservice.web.mapper.ContentRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.ImageRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.ImageResponseMapper;
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
 * The volume section: every endpoint of {@link ContentController}, plus the volumes a
 * chapter may be linked to, and creating a content and editing its draft (UF-CNT-01,
 * UF-CNT-02), which take {@code content:write} - with a cover when asked, like a Devil
 * Fruit with its image.
 */
@RestController
@RequestMapping(ApiPaths.VOLUMES)
@Tag(name = "Volumes")
class VolumeController extends ContentController<Volume, VolumeResponse, VolumeNamesResponse> {

	/** The parts of a save with a cover: the version as JSON, and the file. */
	private static final String VERSION_PART = "version";

	private static final String COVER_PART = "cover";

	private final VolumeService service;

	private final VolumeLinks links;

	private final ImageResponseMapper images;

	@Autowired
	VolumeController(VolumeService service, VolumeLinks links, ImageResponseMapper images) {
		super(service, access -> StoryResponseMapper.toVersionResponse(access, images));
		this.service = service;
		this.links = links;
		this.images = images;
	}

	/**
	 * One page of the list; {@code sort=number} orders it by number. A sort by
	 * {@code name} reads the title in the language of the standard
	 * {@code Accept-Language} header.
	 */
	@GetMapping(ApiPaths.CONTENT_LIST)
	PageResponse<ContentSummaryResponse<VolumeNamesResponse>> list(@ParameterObject @Valid ContentListRequest request,
			@ParameterObject @SortableBy(ContentSortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller, Locale locale) {
		return listPage(ContentRequestMapper.toFilter(request), pageable, caller, locale);
	}

	@Override
	protected List<ContentSummaryResponse<VolumeNamesResponse>> summaryResponses(List<ContentSummary<Volume>> rows,
			AuthenticatedCaller caller) {
		return rows.stream().map(row -> StoryResponseMapper.toSummaryResponse(row, this.images)).toList();
	}

	/**
	 * The volumes a chapter may be linked to: those with a version that passed review, by
	 * number, narrowed by what their number or a title contains.
	 */
	@GetMapping(ApiPaths.CONTENT_LINKABLE)
	PageResponse<NumberedReferenceResponse> linkable(@ParameterObject @Valid LinkableNumberedRequest request) {
		var page = this.links.linkable(request.q(), request.pageOrDefault(), request.sizeOrDefault());
		return PageResponse.from(page.map(StoryResponseMapper::toResponse));
	}

	/**
	 * A new content with its first draft, written by the caller (UF-CNT-01), without a
	 * cover. Answers with the content, so its id is known.
	 */
	@PostMapping(path = ApiPaths.CONTENT_LIST, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse create(@RequestBody @Valid VolumeRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return create(request, null, caller);
	}

	/**
	 * The same, with a cover: part {@code version} is the JSON, part {@code cover} the
	 * file. Text and cover are saved together or not at all.
	 */
	@PostMapping(path = ApiPaths.CONTENT_LIST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse createWithCover(@RequestPart(VERSION_PART) @Valid VolumeRequest request,
			@RequestPart(name = COVER_PART, required = false) MultipartFile cover,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return create(request, cover, caller);
	}

	/**
	 * Replaces what a draft says (UF-CNT-02), keeping its cover unless
	 * {@code removeImage} is set; answers with the version as it now is.
	 */
	@PutMapping(path = ApiPaths.CONTENT_VERSION, consumes = MediaType.APPLICATION_JSON_VALUE)
	VersionResponse<VolumeResponse> edit(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid VolumeRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		return edit(id, number, request, null, caller);
	}

	/**
	 * The same, with a cover replacing the draft's: part {@code version} is the JSON,
	 * part {@code cover} the file; without the file, as {@link #edit}.
	 */
	@PutMapping(path = ApiPaths.CONTENT_VERSION, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	VersionResponse<VolumeResponse> editWithCover(@PathVariable UUID id, @PathVariable int number,
			@RequestPart(VERSION_PART) @Valid VolumeRequest request,
			@RequestPart(name = COVER_PART, required = false) MultipartFile cover,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return edit(id, number, request, cover, caller);
	}

	private ContentResponse create(VolumeRequest request, MultipartFile cover, AuthenticatedCaller caller) {
		ImageChange change = ImageRequestMapper.toImageChange(request.removesImage(), cover);
		var content = this.service.create(caller.permissions(), caller.user(), StoryRequestMapper.toDomain(request),
				change);
		return ContentResponseMapper.toContentResponse(content);
	}

	private VersionResponse<VolumeResponse> edit(UUID id, int number, VolumeRequest request, MultipartFile cover,
			AuthenticatedCaller caller) {
		ImageChange change = ImageRequestMapper.toImageChange(request.removesImage(), cover);
		VersionAccess<Volume> version = this.service.edit(caller.permissions(), caller.user(), id, number,
				StoryRequestMapper.toDomain(request), change);
		return versionResponse(version);
	}

}
