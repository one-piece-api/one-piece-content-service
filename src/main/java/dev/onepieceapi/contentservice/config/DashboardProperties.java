package dev.onepieceapi.contentservice.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The dashboard's tunables, from {@code content.dashboard.*}.
 *
 * @param activitySize how many of the caller's latest actions "My latest activity" shows
 */
@Validated
@ConfigurationProperties("content.dashboard")
public record DashboardProperties(@Positive int activitySize) {

}
