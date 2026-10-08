package dev.onepieceapi.contentservice.domain.image;

import lombok.Builder;

/**
 * What an entity asks of its images (implementation plan of the Devil Fruit, D6): the
 * canvas every image is normalized to - also the least size accepted, never upscaled -
 * how far from the canvas' ratio an upload may be, its limits, and how much of it must be
 * fully transparent ("an image without background").
 *
 * @param width the canvas width, and the least width accepted
 * @param height the canvas height, and the least height accepted
 * @param ratioTolerance how far the upload's ratio may be from the canvas', as a fraction
 * (0.1 = 10 %)
 * @param maxBytes the largest upload accepted
 * @param maxPixels the most pixels accepted, read from the header before decoding
 * @param minTransparentPercent the least share of fully transparent pixels
 */
@Builder
public record ImageProfile(int width, int height, double ratioTolerance, long maxBytes, long maxPixels,
		int minTransparentPercent) {

	public double ratio() {
		return (double) this.width / this.height;
	}

}
