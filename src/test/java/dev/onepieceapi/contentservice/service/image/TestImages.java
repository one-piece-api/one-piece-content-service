package dev.onepieceapi.contentservice.service.image;

import dev.onepieceapi.contentservice.domain.image.ImageProfile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Uploads for the tests of images, drawn in memory - a red disc, on a transparent
 * background or on white.
 */
public final class TestImages {

	/** The Devil Fruit profile as configured. */
	public static final ImageProfile PROFILE = ImageProfile.builder()
		.width(640)
		.height(800)
		.ratioTolerance(0.1)
		.maxBytes(5L * 1024 * 1024)
		.maxPixels(25_000_000)
		.minTransparentPercent(5)
		.build();

	private static final int END_OF_PNG_HEADER = 8 + 4 + 4 + 13 + 4;

	private TestImages() {
	}

	/** A red disc on a transparent background: what the profile asks for. */
	public static byte[] transparentPng(int width, int height) {
		return png(drawn(width, height, null));
	}

	/** The same disc, on white: no transparency at all. */
	public static byte[] opaquePng(int width, int height) {
		return png(drawn(width, height, Color.WHITE));
	}

	/** A red disc on this background; null for a transparent one. */
	public static BufferedImage drawn(int width, int height, Color background) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		if (background != null) {
			graphics.setColor(background);
			graphics.fillRect(0, 0, width, height);
		}
		graphics.setColor(Color.RED);
		int diameter = Math.min(width, height) * 2 / 3;
		graphics.fillOval((width - diameter) / 2, (height - diameter) / 2, diameter, diameter);
		graphics.dispose();
		return image;
	}

	public static byte[] png(BufferedImage image) {
		return encode(image, "png");
	}

	public static byte[] jpeg(int width, int height) {
		BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = rgb.createGraphics();
		graphics.drawImage(drawn(width, height, Color.WHITE), 0, 0, null);
		graphics.dispose();
		return encode(rgb, "jpg");
	}

	/**
	 * This PNG made animated: an {@code acTL} chunk right after the header, as an APNG
	 * has (its CRC is not checked by the format detection).
	 */
	public static byte[] animated(byte[] png) {
		byte[] chunk = ByteBuffer.allocate(4 + 4 + 8 + 4)
			.putInt(8)
			.put("acTL".getBytes(StandardCharsets.US_ASCII))
			.putInt(2)
			.putInt(0)
			.array();
		byte[] apng = Arrays.copyOf(png, png.length + chunk.length);
		System.arraycopy(png, END_OF_PNG_HEADER, apng, END_OF_PNG_HEADER + chunk.length,
				png.length - END_OF_PNG_HEADER);
		System.arraycopy(chunk, 0, apng, END_OF_PNG_HEADER, chunk.length);
		return apng;
	}

	private static byte[] encode(BufferedImage image, String format) {
		try {
			var output = new ByteArrayOutputStream();
			ImageIO.write(image, format, output);
			return output.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
