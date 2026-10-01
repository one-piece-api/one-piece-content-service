package dev.onepieceapi.contentservice.web.dto.response;

import java.util.UUID;

/** An author, claimant or actor: the id to filter by, the username to show. */
public record UserResponse(UUID id, String username, String email) {

}
