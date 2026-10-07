package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.service.ContentService;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.NewVersionRequest;
import dev.onepieceapi.contentservice.web.dto.request.RejectVersionRequest;
import dev.onepieceapi.contentservice.web.dto.response.ContentListSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.UserResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionEventResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.mapper.ContentResponseMapper;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/**
 * The endpoints every entity section has, written once (implementation plan of the Devil
 * Fruit, D8). Reading (UF-CNT-12, UF-CNT-18) - the paginated list, a content with its
 * version chain, one version with what it says, and its history - takes
 * {@code content:read}; discarding a draft, sending it to review and taking it back,
 * returning a rejected one to draft, opening a new version (UF-CNT-03, 04, 08, 11, 15)
 * take {@code content:write}; claiming, releasing, approving and rejecting (UF-CNT-05,
 * 06, 13, 14) {@code content:review}; publishing, archiving, recovering and restoring
 * (UF-CNT-07, 09, 16, 17) {@code content:publish}, retiring (UF-CNT-10)
 * {@code content:retire} (see {@code ContentEndpoint}). What a caller then finds is
 * limited to the statuses their permissions make visible: whatever is not visible answers
 * {@code 404} like something that does not exist, a visible version the caller may not
 * change {@code 403}, one that is not in a state for it {@code 409}.
 * <p>
 * Each entity has a concrete controller extending this one: it fixes the section's path
 * and the types, so the API contract describes each section exactly, and adds creating
 * and editing a draft, whose body is the entity's own - e.g.
 * {@link DevilFruitTypeController}.
 *
 * @param <T> what a version of the entity says
 * @param <R> how a version's content is answered
 * @param <S> how a content is answered in the list
 */
@RequiredArgsConstructor
abstract class ContentController<T extends ContentBody<T>, R, S> {

	private final ContentService<T, ?> service;

	/**
	 * The entity's mapper, answering a version with its content in the entity's shape.
	 */
	private final Function<VersionAccess<T>, VersionResponse<R>> toVersionResponse;

	/**
	 * The rows of a page of the list, answered in the entity's shape - all at once, so an
	 * entity that must look something up for each row can do it for the page.
	 */
	protected abstract List<ContentSummaryResponse<S>> summaryResponses(List<ContentSummary<T>> rows,
			AuthenticatedCaller caller);

	/** A version as the entity answers it. */
	protected VersionResponse<R> versionResponse(VersionAccess<T> access) {
		return this.toVersionResponse.apply(access);
	}

	/**
	 * One page of the list, for the filter a section's own {@code list} built from its
	 * query parameters. A sort by {@code name} reads the name in the language of the
	 * standard {@code Accept-Language} header, the one the caller is reading in.
	 */
	protected PageResponse<ContentSummaryResponse<S>> listPage(ContentFilter filter, Pageable pageable,
			AuthenticatedCaller caller, Locale locale) {
		var page = this.service.list(caller.permissions(), caller.user(), filter, pageable, locale.getLanguage());
		var rows = summaryResponses(page.getContent(), caller);
		return PageResponse.from(new PageImpl<>(rows, page.getPageable(), page.getTotalElements()));
	}

	/** Powers the list's author filter, which cannot be derived from one loaded page. */
	@GetMapping(ApiPaths.CONTENT_AUTHORS)
	List<UserResponse> authors(@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.service.authors(caller.permissions()).stream().map(ContentResponseMapper::toUserResponse).toList();
	}

	/** The totals and the status options the list shows around its rows. */
	@GetMapping(ApiPaths.CONTENT_SUMMARY)
	ContentListSummaryResponse summary(@AuthenticationPrincipal AuthenticatedCaller caller) {
		return ContentResponseMapper.toListSummaryResponse(this.service.summary(caller.permissions(), caller.user()));
	}

	@GetMapping(ApiPaths.CONTENT_BY_ID)
	ContentResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedCaller caller) {
		return ContentResponseMapper.toContentResponse(this.service.get(caller.permissions(), caller.user(), id));
	}

	@GetMapping(ApiPaths.CONTENT_VERSION)
	VersionResponse<R> version(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.toVersionResponse.apply(this.service.getVersion(caller.permissions(), caller.user(), id, number));
	}

	@GetMapping(ApiPaths.CONTENT_VERSION_EVENTS)
	List<VersionEventResponse> events(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.service.events(caller.permissions(), id, number)
			.stream()
			.map(ContentResponseMapper::toEventResponse)
			.toList();
	}

	/**
	 * Opens the next version of a content from one of its closed versions (UF-CNT-08): a
	 * draft of the caller, saying what that one says. Answers with the new version.
	 */
	@PostMapping(ApiPaths.CONTENT_VERSIONS)
	@ResponseStatus(HttpStatus.CREATED)
	VersionResponse<R> openNewVersion(@PathVariable UUID id, @RequestBody @Valid NewVersionRequest request,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var version = this.service.openNewVersion(caller.permissions(), caller.user(), id, request.basedOn());
		return this.toVersionResponse.apply(version);
	}

	/** Discards a draft (UF-CNT-11) - and its content with it, when it was the first. */
	@DeleteMapping(ApiPaths.CONTENT_VERSION)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id, @PathVariable int number, @AuthenticationPrincipal AuthenticatedCaller caller) {
		this.service.delete(caller.permissions(), caller.user(), id, number);
	}

	/** Sends a draft to review (UF-CNT-03); answers with the version as it now is. */
	@PostMapping(ApiPaths.CONTENT_VERSION_SUBMIT)
	VersionResponse<R> submit(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.toVersionResponse.apply(this.service.submit(caller.permissions(), caller.user(), id, number));
	}

	/**
	 * Takes an unclaimed version back from review (UF-CNT-04); answers with it as it now
	 * is.
	 */
	@PostMapping(ApiPaths.CONTENT_VERSION_PULL_BACK)
	VersionResponse<R> pullBack(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.toVersionResponse.apply(this.service.pullBack(caller.permissions(), caller.user(), id, number));
	}

	/**
	 * Takes a version in review for the caller to decide on (UF-CNT-13); answers with it
	 * as it now is.
	 */
	@PostMapping(ApiPaths.CONTENT_VERSION_CLAIM)
	VersionResponse<R> claim(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.toVersionResponse.apply(this.service.claim(caller.permissions(), caller.user(), id, number));
	}

	/**
	 * Lets go of a version the caller holds (UF-CNT-14); answers with it as it now is.
	 */
	@PostMapping(ApiPaths.CONTENT_VERSION_RELEASE)
	VersionResponse<R> release(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.toVersionResponse.apply(this.service.release(caller.permissions(), caller.user(), id, number));
	}

	/** Passes the review of a version the caller holds (UF-CNT-05). */
	@PostMapping(ApiPaths.CONTENT_VERSION_APPROVE)
	VersionResponse<R> approve(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.toVersionResponse.apply(this.service.approve(caller.permissions(), caller.user(), id, number));
	}

	/** Fails the review of a version the caller holds, saying why (UF-CNT-06). */
	@PostMapping(ApiPaths.CONTENT_VERSION_REJECT)
	VersionResponse<R> reject(@PathVariable UUID id, @PathVariable int number,
			@RequestBody @Valid RejectVersionRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		var version = this.service.reject(caller.permissions(), caller.user(), id, number, request.reason());
		return this.toVersionResponse.apply(version);
	}

	/** Takes a rejected version of the caller back to draft (UF-CNT-15). */
	@PostMapping(ApiPaths.CONTENT_VERSION_RETURN_TO_DRAFT)
	VersionResponse<R> returnToDraft(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.toVersionResponse
			.apply(this.service.returnToDraft(caller.permissions(), caller.user(), id, number));
	}

	/**
	 * Puts a version ready to publish online (UF-CNT-07), superseding the one online
	 * until then; answers with it as it now is.
	 */
	@PostMapping(ApiPaths.CONTENT_VERSION_PUBLISH)
	VersionResponse<R> publish(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.toVersionResponse.apply(this.service.publish(caller.permissions(), caller.user(), id, number));
	}

	/**
	 * Sets a version ready to publish aside without putting it online (UF-CNT-16);
	 * answers with it as it now is.
	 */
	@PostMapping(ApiPaths.CONTENT_VERSION_ARCHIVE)
	VersionResponse<R> archive(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.toVersionResponse.apply(this.service.archive(caller.permissions(), caller.user(), id, number));
	}

	/**
	 * Brings an archived version back among those ready to publish (UF-CNT-17); answers
	 * with it as it now is.
	 */
	@PostMapping(ApiPaths.CONTENT_VERSION_RECOVER)
	VersionResponse<R> recover(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.toVersionResponse.apply(this.service.recover(caller.permissions(), caller.user(), id, number));
	}

	/**
	 * Takes the online version offline (UF-CNT-10); answers with it as it now is.
	 */
	@PostMapping(ApiPaths.CONTENT_VERSION_RETIRE)
	VersionResponse<R> retire(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.toVersionResponse.apply(this.service.retire(caller.permissions(), caller.user(), id, number));
	}

	/**
	 * Puts a version that was online back online as it was (UF-CNT-09), superseding the
	 * one online until then; answers with it as it now is.
	 */
	@PostMapping(ApiPaths.CONTENT_VERSION_RESTORE)
	VersionResponse<R> restore(@PathVariable UUID id, @PathVariable int number,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.toVersionResponse.apply(this.service.restore(caller.permissions(), caller.user(), id, number));
	}

}
