package com.scalegrams.externalfood;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class UsdaFoodDataProviderTests {
    private final FoodLookupProperties properties = new FoodLookupProperties(
            true,
            Duration.ofSeconds(3),
            new FoodLookupProperties.OpenFoodFacts("https://example.test", "ScaleGrams test"),
            new FoodLookupProperties.Usda("https://api.nal.usda.gov/fdc/v1", "test-key"));

    private final RestTemplate restTemplate = new RestTemplate();
    private final UsdaFoodDataProvider provider = new UsdaFoodDataProvider(properties, restTemplate);

    @Test
    void candidateParsesSearchAndDetailNutrientFormats() {
        String search = """
                {"foods":[{"fdcId":2707191,"description":"Egg, whole, raw","foodNutrients":[
                  {"nutrientId":1008,"value":143},{"nutrientId":1003,"value":12.56}]}]}""";
        String detail = """
                {"fdcId":2707191,"description":"Egg, whole, raw","foodNutrients":[
                  {"id":34253614,"nutrient":{"id":1008,"name":"Energy"},"amount":143},
                  {"id":34253615,"nutrient":{"id":1003,"name":"Protein"},"amount":12.56}]}""";

        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://api.nal.usda.gov/fdc/v1/foods/search?api_key=test-key&query=egg&pageSize=1"))
                .andRespond(withSuccess(search, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.nal.usda.gov/fdc/v1/food/2707191?api_key=test-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(detail, MediaType.APPLICATION_JSON));

        Optional<ExternalFoodCandidate> found = provider.searchOne("egg");

        assertThat(found).isPresent();
        assertThat(found.get().calories()).isEqualTo(143);
        assertThat(found.get().nutrients()).containsEntry("CALORIES", new BigDecimal("143"));
        assertThat(found.get().nutrients()).containsEntry("PROTEIN", new BigDecimal("12.56"));
    }

    @Test
    void candidateUsesSearchFormatWhenNoDetail() {
        String search = """
                {"foods":[{"fdcId":2707191,"description":"Egg, whole, raw","foodNutrients":[
                  {"nutrientId":1008,"value":143},{"nutrientId":1003,"value":12.56}]}]}""";

        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://api.nal.usda.gov/fdc/v1/foods/search?api_key=test-key&query=egg&pageSize=1"))
                .andRespond(withSuccess(search, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.nal.usda.gov/fdc/v1/food/2707191?api_key=test-key"))
                .andRespond(withServerError());

        Optional<ExternalFoodCandidate> found = provider.searchOne("egg");

        assertThat(found).isPresent();
        assertThat(found.get().nutrients()).containsEntry("CALORIES", new BigDecimal("143"));
        assertThat(found.get().nutrients()).containsEntry("PROTEIN", new BigDecimal("12.56"));
    }
}