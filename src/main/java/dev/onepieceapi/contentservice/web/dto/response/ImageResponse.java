package dev.onepieceapi.contentservice.web.dto.response;

/**
 * An image of a version (implementation plan of the Devil Fruit, D6, refinement 2).
 *
 * @param id the SHA-256 of the image: two versions with the same id have the same image
 * @param url where to get it, used as it is: built by the service, so where images are
 * served from can change without clients knowing
 */
public record ImageResponse(String id, String url) {

}
