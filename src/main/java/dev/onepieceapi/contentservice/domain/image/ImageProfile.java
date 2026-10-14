package dev.onepieceapi.contentservice.domain.image;

import lombok.Builder;

/**
 * What an entity asks of its images (implementation plan of the Devil Fruit, D6): the
 * canvas every image is normalized to - never upscaled, so by default the least size
 * accepted - how far from the canvas' ratio an upload may be, its limits, and how much of
 * it must be fully transparent ("an image without background"), 0 for none.
 *
 * @param width the canvas width
 * @param height the canvas height
 * @param minWidth the least width accepted; the canvas width when 0 or less
 * @param minHeight the least height accepted; the canvas height when 0 or less
 * @param ratioTolerance how far the upload's ratio may be from the canvas', as a fraction
 * (0.1 = 10 %)
 * @param maxBytes the largest upload accepted
 * @param maxPixels the most pixels accepted, read from the header before decoding
 * @param minTransparentPercent the least share of fully transparent pixels
 */
@Builder
public record ImageProfile(int width, int height, int minWidth, int minHeight, double ratioTolerance, long maxBytes,
		long maxPixels, int minTransparentPercent) {

	public ImageProfile {
		minWidth = minWidth > 0 ? minWidth : width;
		minHeight = minHeight > 0 ? minHeight : height;
	}

	public double ratio() {
		return (double) this.width / this.height;
	}

}
