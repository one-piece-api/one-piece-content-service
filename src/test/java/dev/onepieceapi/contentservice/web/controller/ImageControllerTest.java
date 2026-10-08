package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.image.NormalizedImage;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.service.ImageService;
import dev.onepieceapi.contentservice.service.exception.ImageNotFoundException;
import dev.onepieceapi.contentservice.web.security.SecurityConfig;
import dev.onepieceapi.exception.web.ApplicationExceptionHandler;
import dev.onepieceapi.exception.web.ConcurrentModificationExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static dev.onepieceapi.contentservice.web.controller.TestCallers.callerWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An image over HTTP (implementation plan of the Devil Fruit, D5): its bytes, kept by the
 * browser of the caller only and for good, or not found alike whether it does not exist
 * or the caller sees no version using it.
 */
@WebMvcTest(ImageController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class, ConcurrentModificationExceptionHandler.class })
class ImageControllerTest {

	private static final String ID = "a".repeat(64);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ImageService service;

	@Test
	void anImageIsItsBytesCachedPrivatelyForGood() throws Exception {
		byte[] bytes = { 1, 2, 3 };
		when(this.service.get(eq(Set.of(Permission.CONTENT_READ)), eq(ID)))
			.thenReturn(new NormalizedImage(ID, "image/png", 640, 800, bytes));

		this.mockMvc.perform(get("/images/" + ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/png"))
			.andExpect(content().bytes(bytes))
			.andExpect(header().string("Cache-Control", "max-age=31536000, private, immutable"))
			.andExpect(header().string("ETag", "\"" + ID + "\""));
	}

	@Test
	void anImageNotSeenIsNotFound() throws Exception {
		when(this.service.get(any(), eq(ID))).thenThrow(new ImageNotFoundException(ID));

		this.mockMvc.perform(get("/images/" + ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_IMAGE_NOT_FOUND"));
	}

	@Test
	void readingAnImageTakesContentRead() throws Exception {
		this.mockMvc.perform(get("/images/" + ID).with(callerWith(Permission.LANGUAGES_MANAGE)))
			.andExpect(status().isForbidden());
	}

}
