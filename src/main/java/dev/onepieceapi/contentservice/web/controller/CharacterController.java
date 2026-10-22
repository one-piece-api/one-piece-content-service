package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.service.affiliation.AffiliationLinks;
import dev.onepieceapi.contentservice.service.bounty.BountyLinks;
import dev.onepieceapi.contentservice.service.character.CharacterLinks;
import dev.onepieceapi.contentservice.service.character.CharacterService;
import dev.onepieceapi.contentservice.service.species.SpeciesLinks;
import dev.onepieceapi.contentservice.service.story.FirstAppearanceLinks;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.CharacterListRequest;
import dev.onepieceapi.contentservice.web.dto.request.CharacterRequest;
import dev.onepieceapi.contentservice.web.dto.request.LinkableTypesRequest;
import dev.onepieceapi.contentservice.web.dto.response.CharacterNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.CharacterReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.CharacterResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.mapper.CharacterRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.CharacterResponseMapper;
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
import java.util.Map;
import java.util.UUID;

/**
 * The Character section: every endpoint of {@link ContentController}, plus creating a
 * content and editing its draft (UF-CNT-01, UF-CNT-02), which take {@code content:write},
 * with or without its portrait, and the characters a bounty may be linked to. Its list is
 * narrowed by status, species, affiliation and affiliation kind too (implementation plan
 * of the Character, D16).
 */
@RestController
@RequestMapping(ApiPaths.CHARACTERS)
@Tag(name = "Characters")
class CharacterController extends ContentController<Character, CharacterResponse, CharacterNamesResponse> {

	/** The parts of a save with a portrait: the version as JSON, and the file. */
	private static final String VERSION_PART = "version";

	private static final String IMAGE_PART = "image";

	private final CharacterService service;

	private final CharacterLinks links;

	private final BountyLinks bounties;

	@Autowired
	CharacterController(CharacterService service, CharacterLinks links, BountyLinks bounties, SpeciesLinks species,
			AffiliationLinks affiliations, FirstAppearanceLinks firstAppearances, ImageResponseMapper images) {
		super(service,
				access -> CharacterResponseMapper.toVersionResponse(access,
						species.referencesOf(access.body().speciesIds()),
						affiliations.referencesOf(
								access.body().affiliations().stream().map(Membership::affiliationContentId).toList()),
						firstAppearances.referencesFor(List.of(access.body())), images));
		this.service = service;
		this.links = links;
		this.bounties = bounties;
	}

	/**
	 * One page of the list. A sort by {@code name} reads the name in the language of the
	 * standard {@code Accept-Language} header, the one the caller is reading in.
	 */
	@GetMapping(ApiPaths.CONTENT_LIST)
	PageResponse<ContentSummaryResponse<CharacterNamesResponse>> list(
			@ParameterObject @Valid CharacterListRequest request,
			@ParameterObject @SortableBy(ContentSortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller, Locale locale) {
		return listPage(CharacterRequestMapper.toFilter(request), pageable, caller, locale);
	}

	@Override
	protected List<ContentSummaryResponse<CharacterNamesResponse>> summaryResponses(
			List<ContentSummary<Character>> rows, AuthenticatedCaller caller) {
		Map<UUID, Long> bountyCounts = this.bounties.bountyCounts(caller.permissions(),
				rows.stream().map(ContentSummary::contentId).toList());
		return rows.stream()
			.map(row -> CharacterResponseMapper.toSummaryResponse(row, bountyCounts.getOrDefault(row.contentId(), 0L)))
			.toList();
	}

	/**
	 * The characters a bounty may be linked to: those with a version that passed review,
	 * by romaji, narrowed by what their romaji or a name contains.
	 */
	@GetMapping(ApiPaths.CONTENT_LINKABLE)
	PageResponse<CharacterReferenceResponse> linkable(@ParameterObject @Valid LinkableTypesRequest request) {
		var page = this.links.linkable(request.q(), request.pageOrDefault(), request.sizeOrDefault());
		return PageResponse.from(page.map(CharacterResponseMapper::toResponse));
	}

	/**
	 * A new content with its first draft, written by the caller (UF-CNT-01), without a
	 * portrait. Answers with the content, so its id is known.
	 */
	@PostMapping(path = ApiPaths.CONTENT_LIST, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse create(@RequestBody @Valid CharacterRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return create(request, null, caller);
	}

	/**
	 * The same, with a portrait (implementation plan of the Character, D7): part
	 * {@code version} is the JSON, part {@code image} the file. Text and portrait are
	 * saved together or not at all.
	 */
	@PostMapping(path = ApiPaths.CONTENT_LIST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse createWithImage(@RequestPart(VERSION_PART) @Valid CharacterRequest request,
			@RequestPart(name = IMAGE_PART, required = false) MultipartFile image,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return create(request, image, caller);
	}

	/**
	 * Replaces what a draft says (UF-CNT-02), keeping its portrait unless
	 * {@code removeImage} is set; answers with the version as it now is.
	 */
	@PutMapping(path = ApiPaths.CONTENT_VERSION, consumes = MediaType.APPLICATION_JSON_VALUE)
	VersionResponse<CharacterResponse> edit(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid CharacterRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		return edit(id, number, request, null, caller);
	}

	/**
	 * The same, with a portrait replacing the draft's: part {@code version} is the JSON,
	 * part {@code image} the file; without the file, as {@link #edit}.
	 */
	@PutMapping(path = ApiPaths.CONTENT_VERSION, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	VersionResponse<CharacterResponse> editWithImage(@PathVariable UUID id, @PathVariable int number,
			@RequestPart(VERSION_PART) @Valid CharacterRequest request,
			@RequestPart(name = IMAGE_PART, required = false) MultipartFile image,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return edit(id, number, request, image, caller);
	}

	private ContentResponse create(CharacterRequest request, MultipartFile image, AuthenticatedCaller caller) {
		Character written = CharacterRequestMapper.toDomain(request);
		ImageChange change = CharacterRequestMapper.toImageChange(request, image);
		var content = this.service.create(caller.permissions(), caller.user(), written, change);
		return ContentResponseMapper.toContentResponse(content);
	}

	private VersionResponse<CharacterResponse> edit(UUID id, int number, CharacterRequest request, MultipartFile image,
			AuthenticatedCaller caller) {
		Character written = CharacterRequestMapper.toDomain(request);
		ImageChange change = CharacterRequestMapper.toImageChange(request, image);
		VersionAccess<Character> version = this.service.edit(caller.permissions(), caller.user(), id, number, written,
				change);
		return versionResponse(version);
	}

}
