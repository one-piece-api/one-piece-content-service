package dev.onepieceapi.contentservice.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Which version of the content a new one is opened from (UF-CNT-08): its number, among
 * the closed ones.
 */
public record NewVersionRequest(@NotNull @Positive Integer basedOn) {
}
