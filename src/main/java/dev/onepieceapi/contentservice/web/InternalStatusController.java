package dev.onepieceapi.contentservice.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Placeholder-only (see {@link ApiPaths#INTERNAL_STATUS}) - proves the permission-gated
 * security chain end-to-end (Phase 0's Definition of Done,
 * {@code docs/implementation-plan-content.md}) before any real domain endpoint exists.
 * Deleted once Step 1 lands.
 */
@RestController
class InternalStatusController {

	@GetMapping(ApiPaths.INTERNAL_STATUS)
	Map<String, String> status() {
		return Map.of("status", "ok");
	}

}
