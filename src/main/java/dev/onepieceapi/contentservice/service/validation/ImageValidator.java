package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.image.ImageProfile;
import dev.onepieceapi.contentservice.service.exception.ContentErrorCode;
import dev.onepieceapi.contentservice.service.exception.ImageRefusedException;
import dev.onepieceapi.contentservice.service.image.ImageFormat;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;

/**
 * The checks an upload must pass against its entity's profile (implementation plan of the
 * Devil Fruit, D6), cheapest first: file size, format by its leading bytes, then pixels,
 * size and ratio from the header - before decoding, so a small file that would decode
 * into a huge image never does - and last the transparency of the decoded pixels. Answers
 * the decoded image, for {@code ImageNormalizer}; refuses with one
 * {@link ImageRefusedException} per reason. A file that looks like a PNG but does not
 * decode is an unsupported format.
 */
@Component
public class ImageValidator {

	private static final int FULLY_TRANSPARENT = 0;

	public BufferedImage validate(byte[] bytes, ImageProfile profile) {
		requireWithinSize(bytes, profile);
		ImageReader reader = ImageFormat.of(bytes).orElseThrow(ImageRefusedException::formatUnsupported).newReader();
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			reader.setInput(input, true, true);
			int width = reader.getWidth(0);
			int height = reader.getHeight(0);
			requireWithinPixels(width, height, profile);
			requireLargeEnough(width, height, profile);
			requireRatio(width, height, profile);
			BufferedImage image = reader.read(0);
			requireTransparency(image, profile);
			return image;
		}
		catch (IOException | IllegalArgumentException | IllegalStateException ex) {
			throw ImageRefusedException.formatUnsupported();
		}
		finally {
			reader.dispose();
		}
	}

	private void requireWithinSize(byte[] bytes, ImageProfile profile) {
		if (bytes.length > profile.maxBytes()) {
			throw new ImageRefusedException(ContentErrorCode.IMAGE_TOO_LARGE, "The image file is too large",
					Map.of("sizeBytes", bytes.length, "maxBytes", profile.maxBytes()));
		}
	}

	private void requireWithinPixels(int width, int height, ImageProfile profile) {
		if ((long) width * height > profile.maxPixels()) {
			throw new ImageRefusedException(ContentErrorCode.IMAGE_TOO_LARGE, "The image has too many pixels",
					Map.of("width", width, "height", height, "maxPixels", profile.maxPixels()));
		}
	}

	private void requireLargeEnough(int width, int height, ImageProfile profile) {
		if (width < profile.width() || height < profile.height()) {
			throw new ImageRefusedException(ContentErrorCode.IMAGE_TOO_SMALL, "The image is too small", Map.of("width",
					width, "height", height, "minWidth", profile.width(), "minHeight", profile.height()));
		}
	}

	private void requireRatio(int width, int height, ImageProfile profile) {
		double offBy = Math.abs((double) width / height / profile.ratio() - 1);
		if (offBy > profile.ratioTolerance()) {
			int divisor = BigInteger.valueOf(profile.width()).gcd(BigInteger.valueOf(profile.height())).intValue();
			throw new ImageRefusedException(ContentErrorCode.IMAGE_WRONG_RATIO,
					"The image is too far from the expected ratio",
					Map.of("width", width, "height", height, "ratioWidth", profile.width() / divisor, "ratioHeight",
							profile.height() / divisor, "tolerancePercent",
							Math.round(profile.ratioTolerance() * 100)));
		}
	}

	private void requireTransparency(BufferedImage image, ImageProfile profile) {
		long total = (long) image.getWidth() * image.getHeight();
		long transparent = image.getColorModel().hasAlpha() ? countFullyTransparent(image) : 0;
		if (transparent * 100 < total * profile.minTransparentPercent()) {
			throw new ImageRefusedException(ContentErrorCode.IMAGE_NOT_TRANSPARENT,
					"The image must have a transparent background", Map.of("transparentPercent",
							transparent * 100 / total, "minPercent", profile.minTransparentPercent()));
		}
	}

	private long countFullyTransparent(BufferedImage image) {
		int width = image.getWidth();
		int[] row = new int[width];
		long count = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			image.getRGB(0, y, width, 1, row, 0, width);
			for (int argb : row) {
				if (argb >>> 24 == FULLY_TRANSPARENT) {
					count++;
				}
			}
		}
		return count;
	}

}
