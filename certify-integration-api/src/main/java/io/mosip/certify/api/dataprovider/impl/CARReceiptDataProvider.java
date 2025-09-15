package io.mosip.certify.api.dataprovider.impl;

import io.mosip.certify.api.dataprovider.DataProviderService;
import io.mosip.certify.api.service.MapImageGeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class CARReceiptDataProvider implements DataProviderService {

    private final SicarCpfCnpjClient sicarCpfCnpjClient;

    private final WebClient webClient;

    // Optional scope (empty string if not needed)
    private final String scope = "";

    @Value("${car.receipt.api.url}")
    private final String apiUrl;

    private final CarTokenClient carTokenClient;

    public CARReceiptDataProvider( SicarCpfCnpjClient sicarCpfCnpjClient,
                                  WebClient.Builder webClientBuilder,
                                  @Value("${car.receipt.api.url}") String apiUrl,
                                  CarTokenClient carTokenClient) {
        this.sicarCpfCnpjClient = sicarCpfCnpjClient;
        this.webClient = webClientBuilder.build();
        this.apiUrl = apiUrl;
        this.carTokenClient = carTokenClient;
    }

    /**
     * Returns the document type for this data provider.
     * This is used to identify the type of data being provided.
     *
     * @return The document type as a String.
     */
    @Override
    public String getDocumentType() {
        //Changed the data provider type to CARReceipt to match with certify json key to integrate with govbr
        return "CARReceipt";
    }

    /**
     * Main method to be called after your validations.
     * It fetches the OAuth2 token, then calls the protected API using the token.
     */
    public JSONObject getData() throws Exception {
        // Step 1: Get access token
        String accessToken = carTokenClient.getAccessToken();
        String registrationNumber = sicarCpfCnpjClient.getRegistrationNumber("06005017951", accessToken);
        log.info("Registration Number: {}", registrationNumber);
        // Step 2: Call protected API with Bearer token
        String response = webClient.get()
                .uri(String.format(apiUrl,registrationNumber))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        if (response == null) {
            throw new RuntimeException("Failed to retrieve data from the API");
        }
        JSONObject jsonObject = new JSONObject(response);
        return new JSONObject((jsonObject.getJSONArray("result").get(0)).toString());
    }
}
