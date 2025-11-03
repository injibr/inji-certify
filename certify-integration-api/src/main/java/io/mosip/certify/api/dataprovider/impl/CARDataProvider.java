package io.mosip.certify.api.dataprovider.impl;

import io.mosip.certify.api.dataprovider.DataProviderService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

/* This class implements the DataProviderService to fetch data from the CAR API.
 * It retrieves an OAuth2 token and uses it to access protected resources.
 */
@Slf4j
@Component
public class CARDataProvider implements DataProviderService {

    private final SicarCpfCnpjClient sicarCpfCnpjClient;

    private final WebClient webClient;

    private final String scope = "";

    private final String apiUrl;

    private final CarTokenClient carTokenClient;

    public CARDataProvider(SicarCpfCnpjClient sicarCpfCnpjClient, WebClient.Builder webClientBuilder,
                           @Value("${car.document.api.url}")String apiUrl,
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
        //Changed the data provider type to CARDocument to match with certify json key to integrate with govbr
        return "CARDocument";
    }

    /**
     * Main method to be called after your validations.
     * It fetches the OAuth2 token, then calls the protected API using the token.
     *
     * @return JSONObject containing the data from the CAR API.
     * @throws JSONException if there is an error with JSON operations.
     */
    public JSONObject getData(String cpfNumber) throws JSONException {
        // Step 1: Get access token
        String accessToken = carTokenClient.getAccessToken();
        String registrationNumber = sicarCpfCnpjClient.getRegistrationNumber(cpfNumber, accessToken);
        log.info("Registration Number: {}", registrationNumber);
        // Step 2: Call protected API with Bearer token
        String response =  webClient.get()
                .uri(String.format(apiUrl, registrationNumber))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No data found for CAR Document for Cpf:"+cpfNumber);
        }
        JSONObject jsonObject = new JSONObject(response);
        return (JSONObject) jsonObject.getJSONArray("result").get(0);
    }
}
