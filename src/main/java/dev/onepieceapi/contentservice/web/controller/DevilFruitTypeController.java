package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSortField;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.ContentListRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeRequest;
import dev.onepieceapi.contentservice.web.dto.response.ContentListSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTypeNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTypeResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.UserResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionEventResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.mapper.ContentRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.ContentResponseMapper;
import dev.onepieceapi.contentservice.web.mapper.DevilFruitTypeRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.DevilFruitTypeResponseMapper;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import dev.onepieceapi.contentservice.web.validation.SortableBy;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The Devil Fruit Type section. Reading (UF-CNT-12, UF-CNT-18) - the paginated list, a
 * content with its version chain, one version with what it says, and its history - takes
 * {@code content:read}; creating a content and editing its draft (UF-CNT-01, UF-CNT-02)
 * take {@code content:write} (see {@code SecuredEndpoint}). What a caller then finds is
 * limited to the statuses their permissions make visible: whatever is not visible answers
 * {@code 404} like something that does not exist, a visible version the caller may not
 * change {@code 403}, one that is not in a state for it {@code 409}.
 */
@RestController
@Tag(name = "Devil Fruit Types")
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class DevilFruitTypeController {

	private final DevilFruitTypeService service;

	@GetMapping(ApiPaths.DEVIL_FRUIT_TYPES)
	PageResponse<ContentSummaryResponse<DevilFruitTypeNamesResponse>> list(
			@ParameterObject @Valid ContentListRequest request,
			@ParameterObject @SortableBy(DevilFruitTypeSortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		Page<ContentSummary<DevilFruitType>> page = this.service.list(caller.permissions(), caller.user(),
				ContentRequestMapper.toFilter(request), pageable);
		return PageResponse.from(page.map(DevilFruitTypeResponseMapper::toSummaryResponse));
	}

	/** Powers the list's author filter, which cannot be derived from one loaded page. */
	@GetMapping(ApiPaths.DEVIL_FRUIT_TYPE_AUTHORS)
	List<UserResponse> authors(@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.service.authors(caller.permissions()).stream().map(ContentResponseMapper::toUserResponse).toList();
	}

	/** The totals and the status options the list shows around its rows. */
	@GetMapping(ApiPaths.DEVIL_FRUIT_TYPE_SUMMARY)
	ContentListSummaryResponse summary(@AuthenticationPrincipal AuthenticatedCaller caller) {
		return ContentResponseMapper.toListSummaryResponse(this.service.summary(caller.permissions(), caller.user()));
	}

	@GetMapping(ApiPaths.DEVIL_FRUIT_TYPE_BY_ID)
	ContentResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedCaller caller) {
		return ContentResponseMapper.toContentResponse(this.service.get(caller.permissions(), caller.user(), id));
	}

	@GetMapping(ApiPaths.DEVIL_FRUIT_TYPE_VERSION)
	VersionResponse<DevilFruitTypeResponse> version(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var version = this.service.getVersion(caller.permissions(), caller.user(), id, number);
		return DevilFruitTypeResponseMapper.toVersionResponse(version);
	}

	@GetMapping(ApiPaths.DEVIL_FRUIT_TYPE_VERSION_EVENTS)
	List<VersionEventResponse> events(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.service.events(caller.permissions(), id, number)
			.stream()
			.map(ContentResponseMapper::toEventResponse)
			.toList();
	}

	/**
	 * A new content with its first draft, written by the caller (UF-CNT-01). Answers with
	 * the content, so its id is known.
	 */
	@PostMapping(ApiPaths.DEVIL_FRUIT_TYPES)
	@ResponseStatus(HttpStatus.CREATED)
	ContentResponse create(@RequestBody @Valid DevilFruitTypeRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		DevilFruitType written = DevilFruitTypeRequestMapper.toDomain(request);
		var content = this.service.create(caller.permissions(), caller.user(), written);
		return ContentResponseMapper.toContentResponse(content);
	}

	/** Replaces what a draft says (UF-CNT-02); answers with the version as it now is. */
	@PutMapping(ApiPaths.DEVIL_FRUIT_TYPE_VERSION)
	VersionResponse<DevilFruitTypeResponse> edit(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid DevilFruitTypeRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		DevilFruitType written = DevilFruitTypeRequestMapper.toDomain(request);
		var version = this.service.edit(caller.permissions(), caller.user(), id, number, written);
		return DevilFruitTypeResponseMapper.toVersionResponse(version);
	}

}
