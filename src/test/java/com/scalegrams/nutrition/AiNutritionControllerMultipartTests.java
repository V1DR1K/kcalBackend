package com.scalegrams.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import com.scalegrams.auth.CentralAuthClient;
import com.scalegrams.auth.CentralAuthClient.CentralUser;
import com.scalegrams.auth.CentralAuthClient.TokenResponse;
import com.scalegrams.auth.CentralJwtService;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateResponse;
import com.scalegrams.user.AppUser;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiNutritionControllerMultipartTests {
	@Autowired
	TestRestTemplate rest;

	@MockBean
	AiNutritionService aiNutritionService;

	@MockBean
	CentralAuthClient centralAuth;

	@MockBean
	CentralJwtService centralJwt;

	@BeforeEach
	void setUp() {
		reset(aiNutritionService, centralAuth, centralJwt);
		when(centralAuth.login(anyString(), anyString())).thenAnswer(invocation -> centralToken(invocation.getArgument(0, String.class)));
		when(centralAuth.refresh(anyString())).thenReturn(centralToken("alex"));
		when(centralJwt.subject(anyString())).thenAnswer(invocation ->
				UUID.nameUUIDFromBytes(invocation.getArgument(0, String.class).getBytes()));
	}

	@Test
	void analyze_acceptsBrowserMultipartTargetTypes() {
		HttpHeaders headers = authHeaders();
		for (AiCaptureTarget target : List.of(AiCaptureTarget.FOOD, AiCaptureTarget.RECIPE)) {
			when(aiNutritionService.analyze(any(AppUser.class), any(MultipartFile.class), nullable(String.class), eq(target)))
					.thenReturn(aiEstimateResponse(target));

			ResponseEntity<String> response = rest.exchange("/api/nutrition/ai-estimates", HttpMethod.POST,
					aiEstimateMultipart(headers, target.name()), String.class);

			assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
			assertThat(response.getBody()).contains("\"targetType\":\"" + target.name() + "\"");
			verify(aiNutritionService).analyze(any(AppUser.class), any(MultipartFile.class), nullable(String.class), eq(target));
		}
	}

	@Test
	void analyze_defaultsMissingTargetToRecipe_andRejectsUnknownTarget() {
		HttpHeaders headers = authHeaders();
		when(aiNutritionService.analyze(any(AppUser.class), any(MultipartFile.class), nullable(String.class), eq(AiCaptureTarget.RECIPE)))
				.thenReturn(aiEstimateResponse(AiCaptureTarget.RECIPE));

		ResponseEntity<String> defaultResponse = rest.exchange("/api/nutrition/ai-estimates", HttpMethod.POST,
				aiEstimateMultipart(headers, null), String.class);

		assertThat(defaultResponse.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(defaultResponse.getBody()).contains("\"targetType\":\"RECIPE\"");
		verify(aiNutritionService).analyze(any(AppUser.class), any(MultipartFile.class), nullable(String.class), eq(AiCaptureTarget.RECIPE));

		ResponseEntity<String> invalidResponse = rest.exchange("/api/nutrition/ai-estimates", HttpMethod.POST,
				aiEstimateMultipart(headers, "PLATE"), String.class);

		assertThat(invalidResponse.getStatusCode().value()).isEqualTo(400);
		assertThat(invalidResponse.getBody()).contains("\"code\":\"INVALID_PARAMETER\"");
	}

	@Test
	void refine_acceptsBrowserMultipartTargetTypes() {
		HttpHeaders headers = authHeaders();
		for (AiCaptureTarget target : List.of(AiCaptureTarget.FOOD, AiCaptureTarget.RECIPE)) {
			when(aiNutritionService.refine(any(AppUser.class), any(MultipartFile.class), nullable(String.class), any(), eq(target)))
					.thenReturn(aiEstimateResponse(target));

			ResponseEntity<String> response = rest.exchange("/api/nutrition/ai-estimates/refinements", HttpMethod.POST,
					aiRefinementMultipart(headers, target.name()), String.class);

			assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
			assertThat(response.getBody()).contains("\"targetType\":\"" + target.name() + "\"");
			verify(aiNutritionService).refine(any(AppUser.class), any(MultipartFile.class), nullable(String.class), any(), eq(target));
		}
	}

	@Test
	void unsupportedMultipartPart_returns415InApiErrorFormat() {
		ResponseEntity<String> response = rest.exchange("/api/nutrition/ai-estimates/refinements", HttpMethod.POST,
				aiRefinementMultipartWithBinaryRequest(authHeaders()), String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(415);
		assertThat(response.getBody()).contains("\"code\":\"UNSUPPORTED_MEDIA_TYPE\"");
		verifyNoInteractions(aiNutritionService);
	}

	private AiEstimateResponse aiEstimateResponse(AiCaptureTarget target) {
		return new AiEstimateResponse(null, target, "Estimación de prueba", "", 90, List.of(), List.of(), null, null);
	}

	private HttpEntity<MultiValueMap<String, Object>> aiEstimateMultipart(HttpHeaders headers, String targetType) {
		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		parts.add("image", imagePart());
		if (targetType != null) parts.add("targetType", targetType);
		return multipartRequest(headers, parts);
	}

	private HttpEntity<MultiValueMap<String, Object>> aiRefinementMultipart(HttpHeaders headers, String targetType) {
		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		parts.add("image", imagePart());
		parts.add("targetType", targetType);
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		parts.add("request", new HttpEntity<>("{\"currentEstimate\":{\"name\":\"Plato\",\"description\":\"\",\"confidence\":80,\"assumptions\":[],\"items\":[{\"name\":\"Arroz\",\"estimatedGrams\":100,\"category\":\"CEREAL\",\"preparation\":\"COOKED\",\"proteinGrams\":2,\"carbsGrams\":28,\"fatGrams\":0}]},\"correction\":\"más arroz\"}", requestHeaders));
		return multipartRequest(headers, parts);
	}

	private HttpEntity<MultiValueMap<String, Object>> aiRefinementMultipartWithBinaryRequest(HttpHeaders headers) {
		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		parts.add("image", imagePart());
		parts.add("targetType", "RECIPE");
		parts.add("request", new ByteArrayResource(new byte[] { 1, 2, 3 }) {
			@Override
			public String getFilename() {
				return "request.bin";
			}
		});
		return multipartRequest(headers, parts);
	}

	private HttpEntity<ByteArrayResource> imagePart() {
		HttpHeaders imageHeaders = new HttpHeaders();
		imageHeaders.setContentType(MediaType.IMAGE_JPEG);
		imageHeaders.setContentDispositionFormData("image", "meal.jpg");
		ByteArrayResource image = new ByteArrayResource(new byte[] { 1, 2, 3 }) {
			@Override
			public String getFilename() {
				return "meal.jpg";
			}
		};
		return new HttpEntity<>(image, imageHeaders);
	}

	private HttpEntity<MultiValueMap<String, Object>> multipartRequest(HttpHeaders headers, MultiValueMap<String, Object> parts) {
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		return new HttpEntity<>(parts, headers);
	}

	private HttpHeaders authHeaders() {
		ResponseEntity<LoginResponse> login = rest.postForEntity("/api/auth/login",
				new LoginRequest("alex", "central-password"), LoginResponse.class);
		assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
				.map(cookie -> cookie.substring(0, cookie.indexOf(';'))).reduce((left, right) -> left + "; " + right).orElse(""));
		return headers;
	}

	private TokenResponse centralToken(String username) {
		String token = "central-token-" + username;
		return new TokenResponse(token, "central-refresh-" + username, "Bearer",
				new CentralUser(UUID.nameUUIDFromBytes(token.getBytes()), username, false));
	}

	private record LoginRequest(String username, String password) {
	}

	private record LoginResponse(String accessToken, String refreshToken, Object user) {
	}
}
