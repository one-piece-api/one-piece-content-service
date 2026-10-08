package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.image.NormalizedImage;
import dev.onepieceapi.contentservice.persistence.entity.ImageEntity;
import lombok.experimental.UtilityClass;

import java.time.Instant;

@UtilityClass
public class ImageMapper {

	public ImageEntity toEntity(NormalizedImage image, Instant createdAt) {
		return ImageEntity.builder()
			.id(image.id())
			.contentType(image.contentType())
			.width(image.width())
			.height(image.height())
			.sizeBytes(image.sizeBytes())
			.bytes(image.bytes())
			.createdAt(createdAt)
			.build();
	}

	public NormalizedImage toDomain(ImageEntity entity) {
		return new NormalizedImage(entity.getId(), entity.getContentType(), entity.getWidth(), entity.getHeight(),
				entity.getBytes());
	}

}
