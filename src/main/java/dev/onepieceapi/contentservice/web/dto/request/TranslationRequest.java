package dev.onepieceapi.contentservice.web.dto.request;

/**
 * Deliberately no {@code @Size}/{@code @NotBlank} constraints: a draft may be saved
 * incomplete or over the eventual submission length limit
 * (docs/user-flows/authentication-and-user-management.md 3.3) - that check belongs to
 * Step 2's submit-for-review endpoint, not this one.
 */
public record TranslationRequest(String name, String description) {

}
