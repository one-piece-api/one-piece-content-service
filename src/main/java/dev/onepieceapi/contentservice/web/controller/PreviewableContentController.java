package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.readertime.VersionPreview;
import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.service.content.ContentPreviewer;
import dev.onepieceapi.contentservice.service.content.ContentService;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.PreviewRequest;
import dev.onepieceapi.contentservice.web.dto.response.PreviewResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.mapper.ContentRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.ImageResponseMapper;
import dev.onepieceapi.contentservice.web.mapper.PreviewResponseMapper;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;
import java.util.function.Function;

/**
 * A section whose items the reader meets along the story, with the preview at N
 * (UF-CNT-23; reveals plan R6, D1): every endpoint of {@link ContentController}, plus a
 * version as a reader at chapter or episode N will see it. Read-only, secured like
 * reading the version: one the caller does not see answers {@code 404}. Volumes, chapters
 * and episodes are the axis itself and have none.
 *
 * @param <T> what a version of the entity says
 * @param <R> how a version's content is answered
 * @param <S> how a content is answered in the list
 */
abstract class PreviewableContentController<T extends ContentBody<T>, R, S> extends ContentController<T, R, S> {

	private final ContentService<T, ?> service;

	private final ContentPreviewer<T> previewer;

	private final ImageResponseMapper images;

	PreviewableContentController(ContentService<T, ?> service,
			Function<VersionAccess<T>, VersionResponse<R>> toVersionResponse, ContentPreviewer<T> previewer,
			ImageResponseMapper images) {
		super(service, toVersionResponse);
		this.service = service;
		this.previewer = previewer;
		this.images = images;
	}

	/**
	 * The resolved body in the section's shape. By default the same mapping as a version;
	 * an entity whose answer looks up other contents overrides it, to read them at N.
	 */
	protected R previewBody(UUID contentId, VersionPreview<T> preview, AuthenticatedCaller caller) {
		return versionResponse(contentId, preview.resolved(), caller).body();
	}

	@GetMapping(ApiPaths.CONTENT_VERSION_PREVIEW)
	PreviewResponse<R> preview(@PathVariable UUID id, @PathVariable int number,
			@ParameterObject @Valid PreviewRequest request, @AuthenticationPrincipal AuthenticatedCaller caller) {
		VersionPreview<T> preview = this.service.preview(caller.permissions(), id, number,
				ContentRequestMapper.toPosition(request), this.previewer);
		R body = preview.preview().visible() ? previewBody(id, preview, caller) : null;
		return PreviewResponseMapper.toResponse(preview.preview(), body, this.images);
	}

}
