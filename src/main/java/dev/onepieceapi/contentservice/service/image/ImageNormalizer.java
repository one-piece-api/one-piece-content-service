package dev.onepieceapi.contentservice.service.image;

import dev.onepieceapi.contentservice.domain.image.ImageProfile;
import dev.onepieceapi.contentservice.domain.image.NormalizedImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Turns a validated upload into the image that is stored (implementation plan of the
 * Devil Fruit, D6): scaled down to fit the profile's canvas, centered on a transparent
 * canvas of exactly that size - no crop, no distortion, never upscaled - re-encoded by
 * the {@link ImageEncoder}, and named by the SHA-256 of the result. Re-encoding the
 * pixels is what drops the upload's metadata and anything else it carried.
 * <p>
 * The scaling halves the image step by step before the last resize: one large bilinear
 * step would skip most source pixels and alias.
 */
@Component
@RequiredArgsConstructor
public class ImageNormalizer {

	private final ImageEncoder encoder;

	public NormalizedImage normalize(BufferedImage upload, ImageProfile profile) {
		double scale = Math.min(1,
				Math.min((double) profile.width() / upload.getWidth(), (double) profile.height() / upload.getHeight()));
		BufferedImage scaled = scale(upload, (int) Math.round(upload.getWidth() * scale),
				(int) Math.round(upload.getHeight() * scale));
		BufferedImage canvas = new BufferedImage(profile.width(), profile.height(), BufferedImage.TYPE_INT_ARGB);
		draw(canvas, scaled, (profile.width() - scaled.getWidth()) / 2, (profile.height() - scaled.getHeight()) / 2,
				scaled.getWidth(), scaled.getHeight());
		byte[] bytes = this.encoder.encode(canvas);
		return new NormalizedImage(sha256(bytes), this.encoder.contentType(), profile.width(), profile.height(), bytes);
	}

	private BufferedImage scale(BufferedImage image, int width, int height) {
		BufferedImage current = image;
		do {
			BufferedImage step = new BufferedImage(Math.max(width, current.getWidth() / 2),
					Math.max(height, current.getHeight() / 2), BufferedImage.TYPE_INT_ARGB);
			draw(step, current, 0, 0, step.getWidth(), step.getHeight());
			current = step;
		}
		while (current.getWidth() != width || current.getHeight() != height);
		return current;
	}

	private void draw(BufferedImage target, BufferedImage source, int x, int y, int width, int height) {
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(source, x, y, width, height, null);
		}
		finally {
			graphics.dispose();
		}
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("Every Java runtime provides SHA-256", ex);
		}
	}

}
