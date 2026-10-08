package dev.onepieceapi.contentservice.config;

import dev.onepieceapi.contentservice.domain.image.ImageProfile;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
import java.util.Optional;

/**
 * The image profile of each entity that has images, from
 * {@code content.images.profiles.<entity>.*} (implementation plan of the Devil Fruit,
 * D6), and where clients reach them. An entity without a profile takes no image.
 *
 * @param baseUrl the base of the image URLs answered to clients, without a trailing
 * slash: this service as the gateway exposes it today, another host if images ever move
 * (refinement 2)
 */
@Validated
@ConfigurationProperties("content.images")
public record ImageProperties(@NotBlank String baseUrl, Map<EntityType, @Valid Profile> profiles) {

	public ImageProperties {
		profiles = (profiles != null) ? Map.copyOf(profiles) : Map.of();
	}

	public Optional<ImageProfile> profileOf(EntityType entityType) {
		return Optional.ofNullable(this.profiles.get(entityType)).map(Profile::toDomain);
	}

	/**
	 * One entity's profile as configured; see {@link ImageProfile} for each value.
	 */
	public record Profile(@Positive int width, @Positive int height,
			@DecimalMin("0.0") @DecimalMax("1.0") double ratioTolerance, @NotNull DataSize maxSize,
			@Positive long maxPixels, @Positive @Max(100) int minTransparentPercent) {

		ImageProfile toDomain() {
			return ImageProfile.builder()
				.width(this.width)
				.height(this.height)
				.ratioTolerance(this.ratioTolerance)
				.maxBytes(this.maxSize.toBytes())
				.maxPixels(this.maxPixels)
				.minTransparentPercent(this.minTransparentPercent)
				.build();
		}

	}

}
