package io.mosip.certify.eca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.Map;

@Component
public class EcaTokenClient {
    private final WebClient webClient;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;

    public EcaTokenClient(
            WebClient webClient,
            @Value("${eca.token.url}") String tokenUrl,
            @Value("${eca.client.id}") String clientId,
            @Value("${eca.client.secret}") String clientSecret) {
        this.webClient = webClient;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getAccessToken() {
        String credentials = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
        
        Map<String, Object> tokenResponse = webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "Basic " + credentials)
                .bodyValue("grant_type=client_credentials&scope=default")
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (tokenResponse == null || tokenResponse.get("access_token") == null) {
            throw new RuntimeException("Failed to retrieve ECA access token");
        }

        return (String) tokenResponse.get("access_token");
    }
}
