package io.mosip.certify.api.dataprovider.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.mosip.certify.api.dataprovider.DataProviderService;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
public class CCIRDataProvider implements DataProviderService {
    private final SicarCpfCnpjClient sicarCpfCnpjClient;

    private final WebClient webClient;

    private final String scope = "";

    private final String apiUrl;

    private final CCIRTokenClient ccirTokenClient;

    public CCIRDataProvider(SicarCpfCnpjClient sicarCpfCnpjClient, WebClient webClient, @Value("${ccir.document.api.url}")String apiUrl, CCIRTokenClient ccirTokenClient) {
        this.webClient = webClient;
        this.sicarCpfCnpjClient = sicarCpfCnpjClient;
        this.apiUrl = apiUrl;
        this.ccirTokenClient = ccirTokenClient;
    }


    @Override
    public String getDocumentType() {
        //Changed the data provider type to CCIRCredential to match with certify json key to integrate with govbr
        return "CCIRCredential";
    }

    @Override
    public JSONObject getData(String cpfNumber) throws JSONException {
    // Step 1: Get access token
        String accessToken = ccirTokenClient.getAccessToken();
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
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No data found for CCIR for Cpf:"+cpfNumber);
        }
        JSONObject jsonObject = new JSONObject(response);
        return new JSONObject((jsonObject.getJSONArray("result").get(0)).toString());
    }

}
