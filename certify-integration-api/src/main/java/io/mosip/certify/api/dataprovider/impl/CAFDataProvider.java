package io.mosip.certify.api.dataprovider.impl;

import io.mosip.certify.api.dataprovider.DataProviderService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
@ConditionalOnProperty(value = "mosip.certify.dataprovider.enabled", havingValue = "true", matchIfMissing = false)
public class CAFDataProvider implements DataProviderService {
    private final SicarCpfCnpjClient sicarCpfCnpjClient;

    private final WebClient webClient;

    private final String scope = "";

    private final String apiUrl;

    private final CafTokenClient carTokenClient;

    public CAFDataProvider(SicarCpfCnpjClient sicarCpfCnpjClient, WebClient webClient, @Value("${caf.api.url}")String apiUrl, CafTokenClient carTokenClient) {
        this.sicarCpfCnpjClient = sicarCpfCnpjClient;
        this.webClient = webClient;
        this.apiUrl = apiUrl;
        this.carTokenClient = carTokenClient;
    }

    @Override
    public String getDocumentType() {
        return "CAFCredential";
    }

    /**
     * Main method to be called after your validations.
     * It fetches the OAuth2 token, then calls the protected API using the token.
     *
     * @return JSONObject containing the data from the CAR API.
     * @throws JSONException if there is an error with JSON operations.
     */
    @Override
    public JSONObject getData(String cpfNumber) throws JSONException {
        // Step 1: Get access token
        String accessToken = carTokenClient.getAccessToken();
//        String registrationNumber = sicarCpfCnpjClient.getRegistrationNumber(cpfNumber, accessToken);
//        log.info("Registration Number: {}", registrationNumber);
        // Step 2: Call protected API with Bearer token
        String response =  webClient.get()
                .uri(String.format(apiUrl, cpfNumber))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No data found for CAF for Cpf:"+cpfNumber);
        }
        return new JSONObject(response);
    }
}
