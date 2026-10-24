package dev.onepieceapi.contentservice.service.image;

import com.luciad.imageio.webp.CompressionType;
import com.luciad.imageio.webp.WebPWriteParam;
import dev.onepieceapi.contentservice.config.WebpProperties;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Lossy WebP through the webp-imageio plugin (libwebp; implementation plan WebP, W-D1,
 * W-D2): the alpha channel stays lossless, so a transparent background stays exactly
 * transparent, and only pixels are written - none of the upload's metadata. libwebp gives
 * the same bytes for the same pixels and settings, on x86_64 and ARM alike.
 * <p>
 * Built at startup with a first write parameter, which loads the native library: a
 * runtime that cannot load it fails to start instead of failing every upload.
 */
@Component
public class WebpImageEncoder implements ImageEncoder {

	public static final String CONTENT_TYPE = "image/webp";

	private static final float PERCENT = 100f;

	private final WebpProperties properties;

	public WebpImageEncoder(WebpProperties properties) {
		this.properties = properties;
		loadNativeLibrary();
	}

	@Override
	public String contentType() {
		return CONTENT_TYPE;
	}

	@Override
	public byte[] encode(BufferedImage image) {
		ImageWriter writer = newWriter();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
			writer.setOutput(output);
			writer.write(null, new IIOImage(image, null, null), writeParam(writer));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		finally {
			writer.dispose();
		}
		return bytes.toByteArray();
	}

	private WebPWriteParam writeParam(ImageWriter writer) {
		WebPWriteParam param = (WebPWriteParam) writer.getDefaultWriteParam();
		param.setCompressionType(CompressionType.Lossy);
		param.setCompressionQuality(this.properties.quality() / PERCENT);
		param.setMethod(this.properties.method());
		return param;
	}

	private static void loadNativeLibrary() {
		ImageWriter writer = newWriter();
		writer.getDefaultWriteParam();
		writer.dispose();
	}

	private static ImageWriter newWriter() {
		return ImageIO.getImageWritersByMIMEType(CONTENT_TYPE).next();
	}

}
