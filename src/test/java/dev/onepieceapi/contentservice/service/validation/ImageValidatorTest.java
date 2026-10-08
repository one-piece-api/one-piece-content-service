package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.image.ImageProfile;
import dev.onepieceapi.contentservice.service.exception.ContentErrorCode;
import dev.onepieceapi.contentservice.service.exception.ImageRefusedException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static dev.onepieceapi.contentservice.service.image.TestImages.PROFILE;
import static dev.onepieceapi.contentservice.service.image.TestImages.animated;
import static dev.onepieceapi.contentservice.service.image.TestImages.jpeg;
import static dev.onepieceapi.contentservice.service.image.TestImages.opaquePng;
import static dev.onepieceapi.contentservice.service.image.TestImages.transparentPng;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The checks of an upload against the Devil Fruit profile (implementation plan of the
 * Devil Fruit, D6): one code per reason, with the numbers that explain it.
 */
class ImageValidatorTest {

	private final ImageValidator validator = new ImageValidator();

	@Test
	void aTransparentPngOfTheProfileIsDecoded() {
		var image = this.validator.validate(transparentPng(640, 800), PROFILE);

		assertThat(image.getWidth()).isEqualTo(640);
		assertThat(image.getHeight()).isEqualTo(800);
	}

	@Test
	void aLargerUploadWithinTheRatioToleranceIsAccepted() {
		// 1000x1200 is 0.833 against 0.8: about 4 % off.
		assertThat(this.validator.validate(transparentPng(1000, 1200), PROFILE).getWidth()).isEqualTo(1000);
	}

	@Test
	void aJpegIsAnUnsupportedFormat() {
		assertThat(refused(jpeg(640, 800), PROFILE).getErrorCode())
			.isEqualTo(ContentErrorCode.IMAGE_FORMAT_UNSUPPORTED);
	}

	@Test
	void aWebpIsAnUnsupportedFormatForNow() {
		byte[] webp = "RIFF\0\0\0\0WEBPVP8L\0\0\0\0\0\0\0\0".getBytes(StandardCharsets.US_ASCII);

		assertThat(refused(webp, PROFILE).getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_FORMAT_UNSUPPORTED);
	}

	@Test
	void anAnimatedPngIsAnUnsupportedFormat() {
		assertThat(refused(animated(transparentPng(640, 800)), PROFILE).getErrorCode())
			.isEqualTo(ContentErrorCode.IMAGE_FORMAT_UNSUPPORTED);
	}

	@Test
	void aFileThatOnlyLooksLikeAPngIsAnUnsupportedFormat() {
		byte[] truncated = Arrays.copyOf(transparentPng(640, 800), 60);

		var refused = refused(truncated, PROFILE);

		assertThat(refused.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_FORMAT_UNSUPPORTED);
		assertThat(refused.getDetails()).containsEntry("field", "image");
	}

	@Test
	void aFileOverTheSizeLimitIsTooLargeWithItsSize() {
		byte[] upload = transparentPng(640, 800);
		var tiny = ImageProfile.builder()
			.width(640)
			.height(800)
			.ratioTolerance(0.1)
			.maxBytes(100)
			.maxPixels(25_000_000)
			.minTransparentPercent(5)
			.build();

		var refused = refused(upload, tiny);

		assertThat(refused.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_TOO_LARGE);
		assertThat(refused.getDetails()).containsEntry("field", "image")
			.containsEntry("sizeBytes", upload.length)
			.containsEntry("maxBytes", 100L);
	}

	@Test
	void anImageWithTooManyPixelsIsTooLargeBeforeBeingDecoded() {
		var refused = refused(transparentPng(6000, 7500), PROFILE);

		assertThat(refused.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_TOO_LARGE);
		assertThat(refused.getDetails()).containsEntry("width", 6000)
			.containsEntry("height", 7500)
			.containsEntry("maxPixels", 25_000_000L);
	}

	@Test
	void anImageSmallerThanTheCanvasIsTooSmall() {
		var refused = refused(transparentPng(320, 400), PROFILE);

		assertThat(refused.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_TOO_SMALL);
		assertThat(refused.getDetails()).containsEntry("minWidth", 640).containsEntry("minHeight", 800);
	}

	@Test
	void anImageTooFarFromTheRatioIsRefusedWithTheRatioAsked() {
		var refused = refused(transparentPng(800, 800), PROFILE);

		assertThat(refused.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_WRONG_RATIO);
		assertThat(refused.getDetails()).containsEntry("ratioWidth", 4)
			.containsEntry("ratioHeight", 5)
			.containsEntry("tolerancePercent", 10L);
	}

	@Test
	void anImageWithoutTransparencyIsRefusedWithItsShare() {
		var refused = refused(opaquePng(640, 800), PROFILE);

		assertThat(refused.getErrorCode()).isEqualTo(ContentErrorCode.IMAGE_NOT_TRANSPARENT);
		assertThat(refused.getDetails()).containsEntry("transparentPercent", 0L).containsEntry("minPercent", 5);
	}

	private ImageRefusedException refused(byte[] upload, ImageProfile profile) {
		return catchThrowableOfType(ImageRefusedException.class, () -> this.validator.validate(upload, profile));
	}

}
