package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyChain;
import dev.onepieceapi.contentservice.domain.bounty.BountySortField;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.domain.workflow.VisibilityPolicy;
import dev.onepieceapi.contentservice.service.bounty.BountyLinks;
import dev.onepieceapi.contentservice.service.bounty.BountyService;
import dev.onepieceapi.contentservice.service.character.CharacterLinks;
import dev.onepieceapi.contentservice.service.story.FirstAppearanceLinks;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.BountyListRequest;
import dev.onepieceapi.contentservice.web.dto.request.BountyRequest;
import dev.onepieceapi.contentservice.web.dto.request.LinkableBountiesRequest;
import dev.onepieceapi.contentservice.web.dto.response.BountyNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.BountyReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.BountyResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.mapper.BountyRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.BountyResponseMapper;
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
import java.util.Set;
import java.util.UUID;

/**
 * The Bounty section: every endpoint of {@link ContentController}, plus creating a
 * content and editing its draft (UF-CNT-01, UF-CNT-02), which take {@code content:write},
 * the list narrowed by condition and validity and sortable by amount, and the bounties a
 * bounty may be linked to. A bounty is answered with what the caller sees of the bounties
 * around it: its validity, the one it replaces, the one replacing it and the whole chain
 * (implementation plan of the Bounty, D3), so each answer is made with the caller, looked
 * up once for the whole page.
 */
@RestController
@RequestMapping(ApiPaths.BOUNTIES)
@Tag(name = "Bounties")
class BountyController extends ContentController<Bounty, BountyResponse, BountyNamesResponse> {

	/** The parts of a save with a poster: the version as JSON, and the file. */
	private static final String VERSION_PART = "version";

	private static final String IMAGE_PART = "image";

	private final BountyService service;

	private final BountyLinks links;

	private final CharacterLinks characters;

	private final FirstAppearanceLinks firstAppearances;

	private final ImageResponseMapper images;

	@Autowired
	BountyController(BountyService service, BountyLinks links, CharacterLinks characters,
			FirstAppearanceLinks firstAppearances, ImageResponseMapper images) {
		super(service, BountyController::answeredWithTheCaller);
		this.service = service;
		this.links = links;
		this.characters = characters;
		this.firstAppearances = firstAppearances;
		this.images = images;
	}

	/**
	 * A bounty is never answered without knowing who asks:
	 * {@link #versionResponse(UUID, VersionAccess, AuthenticatedCaller)} is the one way,
	 * and this is what the generic controller is built with so that nothing can reach it
	 * any other.
	 */
	private static VersionResponse<BountyResponse> answeredWithTheCaller(VersionAccess<Bounty> access) {
		throw new UnsupportedOperationException("A bounty is answered with the caller");
	}

	@Override
	protected VersionResponse<BountyResponse> versionResponse(UUID contentId, VersionAccess<Bounty> access,
			AuthenticatedCaller caller) {
		Bounty bounty = access.body();
		Set<VersionStatus> visible = visibleTo(caller);
		BountyChain chain = this.links.chainOf(contentId, bounty.previousContentId(), visible);
		var bounties = this.links.referencesOf(chain.contentIds(), visible);
		var holders = this.characters.referencesOf(holderIdsOf(bounty));
		var storyItems = this.firstAppearances.referencesFor(List.of(bounty));
		return BountyResponseMapper.toVersionResponse(access, chain, bounties, holders, storyItems, this.images);
	}

	/**
	 * One page of the list, narrowed by condition and validity when asked, sortable by
	 * amount. A sort by {@code name} is by the key: a bounty has no name to read in a
	 * language.
	 */
	@GetMapping(ApiPaths.CONTENT_LIST)
	PageResponse<ContentSummaryResponse<BountyNamesResponse>> list(@ParameterObject @Valid BountyListRequest request,
			@ParameterObject @SortableBy(BountySortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller, Locale locale) {
		return listPage(BountyRequestMapper.toFilter(request), pageable, caller, locale);
	}

	@Override
	protected List<ContentSummaryResponse<BountyNamesResponse>> summaryResponses(List<ContentSummary<Bounty>> rows,
			AuthenticatedCaller caller) {
		Set<UUID> replaced = this.links.replacedAmong(rows.stream().map(ContentSummary::contentId).toList(),
				visibleTo(caller));
		return rows.stream().map(row -> BountyResponseMapper.toSummaryResponse(row, replaced)).toList();
	}

	/**
	 * The bounties a bounty may be linked to: those with a version that passed review, by
	 * key, narrowed by what their key contains.
	 */
	@GetMapping(ApiPaths.CONTENT_LINKABLE)
	PageResponse<BountyReferenceResponse> linkable(@ParameterObject @Valid LinkableBountiesRequest request) {
		var page = this.links.linkable(request.q(), request.pageOrDefault(), request.sizeOrDefault());
		return PageResponse.from(page.map(BountyResponseMapper::toResponse));
	}

	/**
	 * A new content with its first draft, written by the caller (UF-CNT-01), without a
	 * poster. Answers with the content, so its id is known.
	 */
	@PostMapping(path = ApiPaths.CONTENT_LIST, consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse create(@RequestBody @Valid BountyRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return create(request, null, caller);
	}

	/**
	 * The same, with a poster (implementation plan of the Bounty, D4): part
	 * {@code version} is the JSON, part {@code image} the file. Text and poster are saved
	 * together or not at all.
	 */
	@PostMapping(path = ApiPaths.CONTENT_LIST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse createWithImage(@RequestPart(VERSION_PART) @Valid BountyRequest request,
			@RequestPart(name = IMAGE_PART, required = false) MultipartFile image,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return create(request, image, caller);
	}

	/**
	 * Replaces what a draft says (UF-CNT-02), keeping its poster unless
	 * {@code removeImage} is set; answers with the version as it now is.
	 */
	@PutMapping(path = ApiPaths.CONTENT_VERSION, consumes = MediaType.APPLICATION_JSON_VALUE)
	VersionResponse<BountyResponse> edit(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid BountyRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		return edit(id, number, request, null, caller);
	}

	/**
	 * The same, with a poster replacing the draft's: part {@code version} is the JSON,
	 * part {@code image} the file; without the file, as {@link #edit}.
	 */
	@PutMapping(path = ApiPaths.CONTENT_VERSION, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	VersionResponse<BountyResponse> editWithImage(@PathVariable UUID id, @PathVariable int number,
			@RequestPart(VERSION_PART) @Valid BountyRequest request,
			@RequestPart(name = IMAGE_PART, required = false) MultipartFile image,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return edit(id, number, request, image, caller);
	}

	private ContentResponse create(BountyRequest request, MultipartFile image, AuthenticatedCaller caller) {
		Bounty written = BountyRequestMapper.toDomain(request);
		ImageChange change = BountyRequestMapper.toImageChange(request, image);
		var content = this.service.create(caller.permissions(), caller.user(), written, change);
		return ContentResponseMapper.toContentResponse(content);
	}

	private VersionResponse<BountyResponse> edit(UUID id, int number, BountyRequest request, MultipartFile image,
			AuthenticatedCaller caller) {
		Bounty written = BountyRequestMapper.toDomain(request);
		ImageChange change = BountyRequestMapper.toImageChange(request, image);
		VersionAccess<Bounty> version = this.service.edit(caller.permissions(), caller.user(), id, number, written,
				change);
		return versionResponse(id, version, caller);
	}

	/** The holder to look up, if the bounty has one. */
	private static List<UUID> holderIdsOf(Bounty bounty) {
		return bounty.holderContentId() == null ? List.of() : List.of(bounty.holderContentId());
	}

	private static Set<VersionStatus> visibleTo(AuthenticatedCaller caller) {
		Set<Permission> permissions = caller.permissions();
		return VisibilityPolicy.visibleStatuses(permissions);
	}

}
