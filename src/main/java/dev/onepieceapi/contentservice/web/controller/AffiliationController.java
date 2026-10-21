package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.service.affiliation.AffiliationService;
import dev.onepieceapi.contentservice.service.story.FirstAppearanceLinks;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.AffiliationListRequest;
import dev.onepieceapi.contentservice.web.dto.request.AffiliationRequest;
import dev.onepieceapi.contentservice.web.dto.response.AffiliationNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.AffiliationResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.mapper.AffiliationRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.AffiliationResponseMapper;
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
 * The Affiliation section: every endpoint of {@link ContentController}, plus creating a
 * content and editing its draft (UF-CNT-01, UF-CNT-02), which take {@code content:write},
 * with or without its emblem. No linkable endpoint: nothing points to an affiliation yet.
 */
@RestController
@RequestMapping(ApiPaths.AFFILIATIONS)
@Tag(name = "Affiliations")
class AffiliationController extends ContentController<Affiliation, AffiliationResponse, AffiliationNamesResponse> {

	/** The parts of a save with an emblem: the version as JSON, and the file. */
	private static final String VERSION_PART = "version";

	private static final String IMAGE_PART = "image";

	private final AffiliationService service;

	@Autowired
	AffiliationController(AffiliationService service, FirstAppearanceLinks firstAppearances,
			ImageResponseMapper images) {
		super(service, access -> AffiliationResponseMapper.toVersionResponse(access,
				firstAppearances.referencesFor(List.of(access.body())), images));
		this.service = service;
	}

	/**
	 * One page of the list. A sort by {@code name} reads the name in the language of the
	 * standard {@code Accept-Language} header, the one the caller is reading in.
	 */
	@GetMapping(ApiPaths.CONTENT_LIST)
	PageResponse<ContentSummaryResponse<AffiliationNamesResponse>> list(
			@ParameterObject @Valid AffiliationListRequest request,
			@ParameterObject @SortableBy(ContentSortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller, Locale locale) {
		return listPage(AffiliationRequestMapper.toFilter(request), pageable, caller, locale);
	}

	@Override
	protected List<ContentSummaryResponse<AffiliationNamesResponse>> summaryResponses(
			List<ContentSummary<Affiliation>> rows, AuthenticatedCaller caller) {
		return rows.stream().map(AffiliationResponseMapper::toSummaryResponse).toList();
	}

	/**
	 * A new content with its first draft, written by the caller (UF-CNT-01), without an
	 * emblem. Answers with the content, so its id is known.
	 */
	@PostMapping(path = ApiPaths.CONTENT_LIST, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse create(@RequestBody @Valid AffiliationRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return create(request, null, caller);
	}

	/**
	 * The same, with an emblem (implementation plan of the Affiliation, D5): part
	 * {@code version} is the JSON, part {@code image} the file. Text and emblem are saved
	 * together or not at all.
	 */
	@PostMapping(path = ApiPaths.CONTENT_LIST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse createWithImage(@RequestPart(VERSION_PART) @Valid AffiliationRequest request,
			@RequestPart(name = IMAGE_PART, required = false) MultipartFile image,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return create(request, image, caller);
	}

	/**
	 * Replaces what a draft says (UF-CNT-02), keeping its emblem unless
	 * {@code removeImage} is set; answers with the version as it now is.
	 */
	@PutMapping(path = ApiPaths.CONTENT_VERSION, consumes = MediaType.APPLICATION_JSON_VALUE)
	VersionResponse<AffiliationResponse> edit(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid AffiliationRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		return edit(id, number, request, null, caller);
	}

	/**
	 * The same, with an emblem replacing the draft's: part {@code version} is the JSON,
	 * part {@code image} the file; without the file, as {@link #edit}.
	 */
	@PutMapping(path = ApiPaths.CONTENT_VERSION, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	VersionResponse<AffiliationResponse> editWithImage(@PathVariable UUID id, @PathVariable int number,
			@RequestPart(VERSION_PART) @Valid AffiliationRequest request,
			@RequestPart(name = IMAGE_PART, required = false) MultipartFile image,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return edit(id, number, request, image, caller);
	}

	private ContentResponse create(AffiliationRequest request, MultipartFile image, AuthenticatedCaller caller) {
		Affiliation written = AffiliationRequestMapper.toDomain(request);
		ImageChange change = AffiliationRequestMapper.toImageChange(request, image);
		var content = this.service.create(caller.permissions(), caller.user(), written, change);
		return ContentResponseMapper.toContentResponse(content);
	}

	private VersionResponse<AffiliationResponse> edit(UUID id, int number, AffiliationRequest request,
			MultipartFile image, AuthenticatedCaller caller) {
		Affiliation written = AffiliationRequestMapper.toDomain(request);
		ImageChange change = AffiliationRequestMapper.toImageChange(request, image);
		VersionAccess<Affiliation> version = this.service.edit(caller.permissions(), caller.user(), id, number, written,
				change);
		return versionResponse(version);
	}

}
