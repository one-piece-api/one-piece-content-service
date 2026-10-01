package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.UUID;

/**
 * A user as stored next to what they did: id, username and e-mail as they were on their
 * token - this service has no user directory to resolve an id against. A value with no
 * identity of its own, embedded wherever a row names a user (the author and the claimant
 * of a version, the actor of an audit record); each use gives the three columns its own
 * prefix.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class UserEmbeddable {

	private UUID userId;

	private String username;

	private String email;

}
