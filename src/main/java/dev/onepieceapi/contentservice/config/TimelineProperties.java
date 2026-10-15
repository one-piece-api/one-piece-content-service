package dev.onepieceapi.contentservice.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The world axis (implementation plan of the timeline, T4), from
 * {@code content.timeline.*}.
 *
 * @param storyStartYear the year of the One Piece calendar chapter 1 happens in: a world
 * date is before the story, so it may not end after it
 */
@Validated
@ConfigurationProperties("content.timeline")
public record TimelineProperties(@NotNull Integer storyStartYear) {

}
