package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.config.ImageProperties;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.response.ImageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * An image id as clients get it: with the URL to fetch it, built here only, from the
 * configured base (implementation plan of the Devil Fruit, D6, refinement 2). A bean, not
 * a static mapper like the others, since the base comes from configuration.
 */
@Component
@RequiredArgsConstructor
public class ImageResponseMapper {

	private final ImageProperties properties;

	/** Null for no image. */
	public ImageResponse toResponse(String imageId) {
		return imageId == null ? null
				: new ImageResponse(imageId, this.properties.baseUrl() + ApiPaths.IMAGES + "/" + imageId);
	}

}
