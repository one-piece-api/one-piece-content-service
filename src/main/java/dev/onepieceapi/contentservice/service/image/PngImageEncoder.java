package dev.onepieceapi.contentservice.service.image;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * PNG through the JDK's own writer: lossless, keeps the alpha channel, and carries none
 * of the upload's metadata (EXIF, GPS, text chunks) since it writes pixels only.
 */
@Component
public class PngImageEncoder implements ImageEncoder {

	@Override
	public String contentType() {
		return MediaType.IMAGE_PNG_VALUE;
	}

	@Override
	public byte[] encode(BufferedImage image) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", output);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return output.toByteArray();
	}

}
