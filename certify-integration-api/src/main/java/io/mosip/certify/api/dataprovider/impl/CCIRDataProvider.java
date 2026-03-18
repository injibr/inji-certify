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
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@Component
public class CCIRDataProvider implements DataProviderService {
    private final SncrCpfCnpjClient sncrCpfCnpjClient;

    private final WebClient webClient;

    private final String scope = "";

    private final String apiUrl;

    private final String xcpfuser;

    private final CCIRTokenClient ccirTokenClient;

    public CCIRDataProvider(SncrCpfCnpjClient sncrCpfCnpjClient, WebClient webClient, @Value("${ccir.document.api.url}")String apiUrl,@Value("${ccir.xcpf.user}")String xcpfuser, CCIRTokenClient ccirTokenClient) {
        this.webClient = webClient;
        this.sncrCpfCnpjClient = sncrCpfCnpjClient;
        this.apiUrl = apiUrl;
        this.ccirTokenClient = ccirTokenClient;
        this.xcpfuser = xcpfuser;
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
        String registrationNumber = sncrCpfCnpjClient.getRegistrationNumber(cpfNumber, accessToken, xcpfuser);
        log.info("Registration Number: {}", registrationNumber);
        // Step 2: Call protected API with Bearer token
        String response =  webClient.get()
                .uri(String.format(apiUrl, registrationNumber))
                .headers(headers -> {
                     headers.setBearerAuth(accessToken);
                     headers.add("x-cpf-usuario", xcpfuser);
                 })
                .retrieve()
                .bodyToMono(String.class)
                .block();
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No data found for CCIR for Cpf:"+cpfNumber);
        }
        return new JSONObject(response);
    }

}
