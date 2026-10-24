package dev.onepieceapi.contentservice.service.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static dev.onepieceapi.contentservice.service.image.TestImages.ENCODER;
import static dev.onepieceapi.contentservice.service.image.TestImages.PROFILE;
import static dev.onepieceapi.contentservice.service.image.TestImages.VOLUME_PROFILE;
import static dev.onepieceapi.contentservice.service.image.TestImages.drawn;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What is stored of an upload (implementation plan of the Devil Fruit, D6; implementation
 * plan WebP, W-D2): always the canvas of the profile, as a lossy WebP named by its hash,
 * the upload scaled to fit and centered on transparency that stays exact.
 */
class ImageNormalizerTest {

	private static final int OPAQUE = 255;

	private final ImageNormalizer normalizer = new ImageNormalizer(ENCODER);

	@Test
	void aLargerUploadIsScaledDownToTheCanvasAsAWebp() throws IOException {
		var image = this.normalizer.normalize(drawn(1280, 1600, null), PROFILE);

		assertThat(image.contentType()).isEqualTo("image/webp");
		assertThat(new String(image.bytes(), 0, 12, StandardCharsets.US_ASCII)).startsWith("RIFF").endsWith("WEBP");
		assertThat(image.width()).isEqualTo(640);
		assertThat(image.height()).isEqualTo(800);
		BufferedImage stored = decoded(image.bytes());
		assertThat(stored.getWidth()).isEqualTo(640);
		assertThat(stored.getHeight()).isEqualTo(800);
		assertThat(alphaAt(stored, 0, 0)).isZero();
		assertThat(alphaAt(stored, 320, 400)).isEqualTo(OPAQUE);
	}

	@Test
	void anUploadOfAnotherRatioIsCenteredBetweenTransparentBands() throws IOException {
		// 704x800 fits as 640x727: a band of 36 pixels above and one below.
		var stored = decoded(this.normalizer.normalize(drawn(704, 800, Color.WHITE), PROFILE).bytes());

		assertThat(alphaAt(stored, 320, 10)).isZero();
		assertThat(alphaAt(stored, 320, 790)).isZero();
		assertThat(alphaAt(stored, 0, 400)).isEqualTo(OPAQUE);
	}

	@Test
	void theTransparentBackgroundStaysExactlyTransparentThroughTheLossyEncoding() throws IOException {
		BufferedImage upload = drawn(640, 800, null);

		var stored = decoded(this.normalizer.normalize(upload, PROFILE).bytes());

		for (int y = 0; y < 800; y++) {
			for (int x = 0; x < 640; x++) {
				assertThat(alphaAt(stored, x, y)).as("alpha at %d,%d", x, y).isEqualTo(alphaAt(upload, x, y));
			}
		}
	}

	@Test
	void theIdIsTheSha256OfTheStoredBytesSoTheSameUploadIsTheSameImage() throws Exception {
		var first = this.normalizer.normalize(drawn(640, 800, null), PROFILE);
		var second = this.normalizer.normalize(drawn(640, 800, null), PROFILE);

		assertThat(first.id())
			.isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(first.bytes())))
			.isEqualTo(second.id());
	}

	private static BufferedImage decoded(byte[] webp) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(webp));
	}

	private static int alphaAt(BufferedImage image, int x, int y) {
		return image.getRGB(x, y) >>> 24;
	}

	@Test
	void aVolumeCoverIsStoredAsTheCanvasOfItsProfileWithoutAnyMetadata() throws IOException {
		var image = this.normalizer.normalize(drawn(1008, 1596, Color.WHITE), VOLUME_PROFILE);

		BufferedImage stored = decoded(image.bytes());
		assertThat(stored.getWidth()).isEqualTo(420);
		assertThat(stored.getHeight()).isEqualTo(665);
		assertThat(image.width()).isEqualTo(420);
		assertThat(image.height()).isEqualTo(665);
		assertThat(alphaAt(stored, 0, 0)).isEqualTo(OPAQUE);
		assertThat(alphaAt(stored, 419, 664)).isEqualTo(OPAQUE);
		String chunks = new String(image.bytes(), StandardCharsets.ISO_8859_1);
		assertThat(chunks).doesNotContain("EXIF", "XMP ", "ICCP");
	}

}
