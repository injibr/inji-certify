package io.mosip.certify.api.dataprovider.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.mosip.certify.api.dataprovider.DataProviderService;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
public class CARDataProvider implements DataProviderService {

    @Override
    public String getDocumentType() {
        return "CARDocument";
    }

    private final WebClient webClient;

    // OAuth2 token endpoint
    private final String tokenUrl = "https://pisrj.dataprev.gov.br/oauth2/token";

    // Client credentials (replace with your actual values)
    private final String clientId = "xdDsOG5vguj7QfxzMeRU1idlDd0a";
    private final String clientSecret = "ezvQJcrbWjO4gSZtHRHYoMQP7W8a";

    // Optional scope (empty string if not needed)
    private final String scope = "";

    // Protected API endpoint to call with token
    private final String apiUrl = "https://papirj.dataprev.gov.br/sicar/demonstrativoDegustacao/1.0/BA-2902005-AD06AC9BEE924477AA13EFE0908D15E0";

    public CARDataProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Main method to be called after your validations.
     * It fetches the OAuth2 token, then calls the protected API using the token.
     */
    public JSONObject getData() throws JsonProcessingException, JSONException {
        // Step 1: Get access token
        String accessToken = getAccessToken();

        // Step 2: Call protected API with Bearer token
        String response =  webClient.get()
                .uri(apiUrl)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        if (response == null) {
            throw new RuntimeException("Failed to retrieve data from the API");
        }
        JSONObject jsonObject = new JSONObject(response);
        return (JSONObject) jsonObject.getJSONArray("result").get(0);
    }
    /**
     * Retrieves the OAuth 2.0 token from the token endpoint using client credentials grant.
     */
    private String getAccessToken() {
        Map tokenResponse = webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("grant_type=client_credentials" +
                        "&client_id=" + clientId +
                        "&client_secret=" + clientSecret +
                        "&scope=default" )
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (tokenResponse == null || tokenResponse.get("access_token") == null) {
            throw new RuntimeException("Failed to retrieve access token");
        }

        return (String) tokenResponse.get("access_token");
    }

    /**
     * Class representing the OAuth token JSON response.
     * Maps JSON keys to Java fields.
     */
    public static class TokenResponse {
        private String access_token;
        private String token_type;
        private long expires_in;
        private String scope;

        public String getAccessToken() {
            return access_token;
        }

        public void setAccessToken(String access_token) {
            this.access_token = access_token;
        }
    }
}
