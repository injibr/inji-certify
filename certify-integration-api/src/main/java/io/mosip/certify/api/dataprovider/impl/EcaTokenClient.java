package io.mosip.certify.api.dataprovider.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
public class EcaTokenClient {
    private final WebClient webClient;

    private final String tokenUrl;

    private final String clientId;

    private final String clientSecret;

    private final boolean wireMockEnabled;

    public EcaTokenClient(
            WebClient webClient,
            @Value("${eca.token.url}") String tokenUrl,
            @Value("${eca.client.id}") String clientId,
            @Value("${eca.client.secret}") String clientSecret,
            @Value("${wiremock.enabled:false}") boolean wireMockEnabled) {
        this.webClient = webClient;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.wireMockEnabled = wireMockEnabled;
    }

    /**
     * Fetches an OAuth 2.0 access token using client credentials.
     *
     * @return The access token as a String.
     * @throws RuntimeException if the token cannot be retrieved.
     */
    public String getAccessToken() {
        Map tokenResponse = webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headers -> {
                    if (wireMockEnabled) {
                        headers.add(HttpHeaders.AUTHORIZATION, "Basic Og==");
                    }
                })
                .bodyValue("grant_type=client_credentials" +
                        "&client_id=" + clientId +
                        "&client_secret=" + clientSecret +
                        "&scope=default")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (tokenResponse == null || tokenResponse.get("access_token") == null) {
            throw new RuntimeException("Failed to retrieve ECA access token");
        }

        return (String) tokenResponse.get("access_token");
    }
}
