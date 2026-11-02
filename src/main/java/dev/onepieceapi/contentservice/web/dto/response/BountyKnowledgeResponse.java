package dev.onepieceapi.contentservice.web.dto.response;

/**
 * What the reader believes of a bounty at its first appearance (workflow 3.7.1).
 *
 * @param inactive null when not part of it
 */
public record BountyKnowledgeResponse(Boolean inactive) {

}
