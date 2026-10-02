package com.scalegrams.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

class GeminiNutritionClientRetryTests {
    private static final String VALID_RESPONSE = providerResponse();

    private static String providerResponse() {
        ObjectMapper mapper = new ObjectMapper();
        var estimate = mapper.createObjectNode().put("name", "Plato").put("confidence", 80);
        estimate.putArray("items").addObject().put("name", "Avena").put("estimatedGrams", 100)
                .put("proteinGrams", 10).put("carbsGrams", 20).put("fatGrams", 5);
        var response = mapper.createObjectNode();
        response.putArray("candidates").addObject().putObject("content")
                .putArray("parts").addObject().put("text", estimate.toString());
        return response.toString();
    }

    private AiNutritionProperties properties;
    private MockRestServiceServer server;
    private GeminiNutritionClient client;

    @BeforeEach
    void setUp() {
        properties = new AiNutritionProperties();
        properties.setGeminiApiKey("test-key");
        properties.setModel("test-model");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiNutritionClient(builder.build(), new ObjectMapper(), properties);
    }

    @Test
    void retriesTransportFailureOnceAndReturnsSuccessfulEstimate() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/models/test-model:")))
                .andRespond(withException(new IOException("network")));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/models/test-model:")))
                .andRespond(withSuccess(VALID_RESPONSE, MediaType.APPLICATION_JSON));

        var result = client.analyze(new byte[] {1}, "image/jpeg", "");

        assertThat(result.items()).hasSize(1);
        server.verify();
    }

    @Test
    void exhaustedTransportRetryReturnsProviderUnavailable() {
        for (int attempt = 0; attempt < 2; attempt++) {
            server.expect(requestTo(org.hamcrest.Matchers.containsString("/models/test-model:")))
                    .andRespond(withException(new IOException("network")));
        }

        assertThatThrownBy(() -> client.analyze(new byte[] {1}, "image/jpeg", ""))
                .isInstanceOf(AiProviderException.class);
        server.verify();
    }

    @Test
    void serviceUnavailableStillFallsBackToNextModel() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/models/test-model:")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/models/gemini-3.5-flash:")))
                .andRespond(withSuccess(VALID_RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(client.analyze(new byte[] {1}, "image/jpeg", "").items()).hasSize(1);
        server.verify();
    }

    @Test
    void quotaAndOtherClientErrorsAreNotRetried() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/models/test-model:")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{}"));
        assertThatThrownBy(() -> client.analyze(new byte[] {1}, "image/jpeg", ""))
                .isInstanceOf(AiQuotaExceededException.class);
        server.verify();

        setUp();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/models/test-model:")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{}"));
        assertThatThrownBy(() -> client.analyze(new byte[] {1}, "image/jpeg", ""))
                .isInstanceOf(AiProviderException.class);
        server.verify();
    }
}
