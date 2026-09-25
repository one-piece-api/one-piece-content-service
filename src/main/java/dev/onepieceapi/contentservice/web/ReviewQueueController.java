package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.web.dto.response.ReviewQueueItemResponse;
import dev.onepieceapi.contentservice.web.dto.response.WorkingRevisionDetailResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * UF-CNT-13+ (docs/user-flows/authentication-and-user-management.md): the shared "In
 * Revisione" queue - every working revision currently {@code IN_REVIEW}, across every
 * author, gated on {@code content:review} rather than ownership (unlike
 * {@link MyDraftsController}, which is the caller's own work only).
 */
@RestController
@Tag(name = "Review queue")
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class ReviewQueueController {

	private final DevilFruitTypeService service;

	@GetMapping(ApiPaths.REVIEW_QUEUE)
	List<ReviewQueueItemResponse> list(
			@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
		return this.service.reviewQueue()
			.stream()
			.map(revision -> DevilFruitTypeResponseMapper.toQueueItem(revision, acceptLanguage))
			.toList();
	}

	@GetMapping(ApiPaths.REVIEW_QUEUE_ITEM)
	WorkingRevisionDetailResponse get(@PathVariable UUID workingRevisionId) {
		var revision = this.service.getForReview(workingRevisionId);
		return DevilFruitTypeResponseMapper.toDetail(revision, this.service.everPublished(revision.itemId()));
	}

}
