package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.image.NormalizedImage;
import dev.onepieceapi.contentservice.service.image.ImageService;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * The images of versions, for whoever sees a version using them (implementation plan of
 * the Devil Fruit, D5); {@code content:read}. An image never changes under its id - the
 * hash of its bytes - so the browser may keep it as long as it likes, but only for the
 * caller: {@code private}, never in a shared cache, since who may see it depends on who
 * asks. PNG, the one format images are stored in today ({@code PngImageEncoder}): another
 * encoder changes it here too.
 */
@RestController
@Tag(name = "Images")
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class ImageController {

	private static final CacheControl PRIVATE_IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365))
		.cachePrivate()
		.immutable();

	private final ImageService service;

	@GetMapping(path = ApiPaths.IMAGE_BY_ID, produces = MediaType.IMAGE_PNG_VALUE)
	ResponseEntity<byte[]> get(@PathVariable String id, @AuthenticationPrincipal AuthenticatedCaller caller) {
		NormalizedImage image = this.service.get(caller.permissions(), id);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(image.contentType()))
			.cacheControl(PRIVATE_IMMUTABLE)
			.eTag(image.id())
			.body(image.bytes());
	}

}
