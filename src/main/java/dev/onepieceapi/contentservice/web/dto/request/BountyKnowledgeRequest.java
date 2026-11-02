package dev.onepieceapi.contentservice.web.dto.request;

/**
 * What the reader believes of a bounty at its first appearance (workflow 3.7.1), for the
 * fields some reveal names.
 *
 * @param inactive left out when not part of it
 */
public record BountyKnowledgeRequest(Boolean inactive) {

}
