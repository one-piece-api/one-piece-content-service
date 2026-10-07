package dev.onepieceapi.contentservice.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The tunables of the entities' own rules, from {@code content.rules.*}.
 *
 * @param blockingPreviewSize how many of the fruits that keep a type online a refused
 * retirement names; the count it also gives is always the whole
 */
@Validated
@ConfigurationProperties("content.rules")
public record RulesProperties(@Positive int blockingPreviewSize) {

}
