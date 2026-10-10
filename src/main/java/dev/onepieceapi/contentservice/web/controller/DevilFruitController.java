package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitService;
import dev.onepieceapi.contentservice.service.devilfruittype.DevilFruitTypeLinks;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitListRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitRequest;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.mapper.ContentResponseMapper;
import dev.onepieceapi.contentservice.web.mapper.DevilFruitRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.DevilFruitResponseMapper;
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
 * The Devil Fruit section: every endpoint of {@link ContentController}, plus creating a
 * content and editing its draft (UF-CNT-01, UF-CNT-02), which take {@code content:write},
 * and the list narrowed to the fruits of a type. The type a fruit points to is answered
 * as it is today, looked up once for the whole page (implementation plan of the Devil
 * Fruit, D1, D4).
 */
@RestController
@RequestMapping(ApiPaths.DEVIL_FRUITS)
@Tag(name = "Devil Fruits")
class DevilFruitController extends ContentController<DevilFruit, DevilFruitResponse, DevilFruitNamesResponse> {

	/** The parts of a save with an image: the version as JSON, and the file. */
	private static final String VERSION_PART = "version";

	private static final String IMAGE_PART = "image";

	private final DevilFruitService service;

	private final DevilFruitTypeLinks links;

	@Autowired
	DevilFruitController(DevilFruitService service, DevilFruitTypeLinks links, ImageResponseMapper images) {
		super(service, access -> DevilFruitResponseMapper.toVersionResponse(access,
				links.referencesFor(List.of(access.body())), images));
		this.service = service;
		this.links = links;
	}

	/**
	 * One page of the list, narrowed to the fruits of a type when asked. A sort by
	 * {@code name} reads the name in the language of the standard {@code Accept-Language}
	 * header, the one the caller is reading in.
	 */
	@GetMapping(ApiPaths.CONTENT_LIST)
	PageResponse<ContentSummaryResponse<DevilFruitNamesResponse>> list(
			@ParameterObject @Valid DevilFruitListRequest request,
			@ParameterObject @SortableBy(ContentSortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller, Locale locale) {
		return listPage(DevilFruitRequestMapper.toFilter(request), pageable, caller, locale);
	}

	@Override
	protected List<ContentSummaryResponse<DevilFruitNamesResponse>> summaryResponses(
			List<ContentSummary<DevilFruit>> rows, AuthenticatedCaller caller) {
		var types = this.links.referencesFor(rows.stream().map(row -> row.version().body()).toList());
		return rows.stream().map(row -> DevilFruitResponseMapper.toSummaryResponse(row, types)).toList();
	}

	/**
	 * A new content with its first draft, written by the caller (UF-CNT-01), without an
	 * image. Answers with the content, so its id is known.
	 */
	@PostMapping(path = ApiPaths.CONTENT_LIST, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse create(@RequestBody @Valid DevilFruitRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return create(request, null, caller);
	}

	/**
	 * The same, with an image (implementation plan of the Devil Fruit, D5): part
	 * {@code version} is the JSON, part {@code image} the file. Text and image are saved
	 * together or not at all.
	 */
	@PostMapping(path = ApiPaths.CONTENT_LIST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse createWithImage(@RequestPart(VERSION_PART) @Valid DevilFruitRequest request,
			@RequestPart(name = IMAGE_PART, required = false) MultipartFile image,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return create(request, image, caller);
	}

	/**
	 * Replaces what a draft says (UF-CNT-02), keeping its image unless
	 * {@code removeImage} is set; answers with the version as it now is.
	 */
	@PutMapping(path = ApiPaths.CONTENT_VERSION, consumes = MediaType.APPLICATION_JSON_VALUE)
	VersionResponse<DevilFruitResponse> edit(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid DevilFruitRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		return edit(id, number, request, null, caller);
	}

	/**
	 * The same, with an image replacing the draft's: part {@code version} is the JSON,
	 * part {@code image} the file; without the file, as {@link #edit}.
	 */
	@PutMapping(path = ApiPaths.CONTENT_VERSION, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	VersionResponse<DevilFruitResponse> editWithImage(@PathVariable UUID id, @PathVariable int number,
			@RequestPart(VERSION_PART) @Valid DevilFruitRequest request,
			@RequestPart(name = IMAGE_PART, required = false) MultipartFile image,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return edit(id, number, request, image, caller);
	}

	private ContentResponse create(DevilFruitRequest request, MultipartFile image, AuthenticatedCaller caller) {
		DevilFruit written = DevilFruitRequestMapper.toDomain(request);
		ImageChange change = DevilFruitRequestMapper.toImageChange(request, image);
		var content = this.service.create(caller.permissions(), caller.user(), written, change);
		return ContentResponseMapper.toContentResponse(content);
	}

	private VersionResponse<DevilFruitResponse> edit(UUID id, int number, DevilFruitRequest request,
			MultipartFile image, AuthenticatedCaller caller) {
		DevilFruit written = DevilFruitRequestMapper.toDomain(request);
		ImageChange change = DevilFruitRequestMapper.toImageChange(request, image);
		VersionAccess<DevilFruit> version = this.service.edit(caller.permissions(), caller.user(), id, number, written,
				change);
		return versionResponse(version);
	}

}
