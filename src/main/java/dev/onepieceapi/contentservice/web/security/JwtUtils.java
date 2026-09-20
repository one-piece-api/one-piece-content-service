package dev.onepieceapi.contentservice.web.security;

import lombok.experimental.UtilityClass;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

/**
 * Same claim-walking utility as {@code one-piece-user-service}'s {@code JwtUtils} - kept
 * separate per-repository since the two services share no library, only the pattern (this
 * project's multi-repo convention, see {@code CLAUDE.md}).
 */
@UtilityClass
public class JwtUtils {

	/**
	 * Walks {@code path} as successive nested-map keys from the token's top-level claims
	 * (e.g. {@code "resource_access", clientId, "roles"} for a client's
	 * {@code resource_access.<clientId>.roles}) and returns the string list found at that
	 * path, or an empty list if any step along the way is missing or not shaped as
	 * expected.
	 */
	public List<String> getNestedStringListClaim(Jwt jwt, String... path) {
		Object current = jwt.getClaims();

		for (String key : path) {
			if (!(current instanceof Map<?, ?> map)) {
				return List.of();
			}
			current = map.get(key);
		}

		if (!(current instanceof List<?> values)) {
			return List.of();
		}

		return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
	}

}
