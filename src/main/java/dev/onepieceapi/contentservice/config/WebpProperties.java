package dev.onepieceapi.contentservice.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The lossy WebP every image is stored as, from {@code content.images.webp.*}
 * (implementation plan WebP, W-D2); its alpha channel stays lossless whatever the
 * quality.
 *
 * @param quality 0 smallest file .. 100 best picture
 * @param method 0 fastest .. 6 smallest file for the same quality
 */
@Validated
@ConfigurationProperties("content.images.webp")
public record WebpProperties(@PositiveOrZero @Max(100) int quality, @PositiveOrZero @Max(6) int method) {
}
