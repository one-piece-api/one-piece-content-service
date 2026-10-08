package dev.onepieceapi.contentservice.service.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;

import static dev.onepieceapi.contentservice.service.image.TestImages.PROFILE;
import static dev.onepieceapi.contentservice.service.image.TestImages.drawn;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What is stored of an upload (implementation plan of the Devil Fruit, D6): always the
 * canvas of the profile, as a PNG named by its hash, the upload scaled to fit and
 * centered on transparency.
 */
class ImageNormalizerTest {

	private static final int OPAQUE = 255;

	private final ImageNormalizer normalizer = new ImageNormalizer(new PngImageEncoder());

	@Test
	void aLargerUploadIsScaledDownToTheCanvasAsAPng() throws IOException {
		var image = this.normalizer.normalize(drawn(1280, 1600, null), PROFILE);

		assertThat(image.contentType()).isEqualTo("image/png");
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
	void theIdIsTheSha256OfTheStoredBytesSoTheSameUploadIsTheSameImage() throws Exception {
		var first = this.normalizer.normalize(drawn(640, 800, null), PROFILE);
		var second = this.normalizer.normalize(drawn(640, 800, null), PROFILE);

		assertThat(first.id())
			.isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(first.bytes())))
			.isEqualTo(second.id());
	}

	private static BufferedImage decoded(byte[] png) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(png));
	}

	private static int alphaAt(BufferedImage image, int x, int y) {
		return image.getRGB(x, y) >>> 24;
	}

}
