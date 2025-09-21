package io.mosip.certify.api.dataprovider.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Client for retrieving registration numbers based on CPF numbers from the Sicar service.
 * This client uses WebClient to make HTTP requests to the specified URL.
 */
@Slf4j
@Component
public class SicarCpfCnpjClient {
    private final WebClient webClient;
    private final String url;

    public SicarCpfCnpjClient(WebClient webClient,
                              @Value("${car.registration.number.url}") String url) {
        this.webClient = webClient;
        this.url = url;
    }

    /**
     * Retrieves the registration number for a given CPF number.
     *
     * @param cpfNo the CPF number to look up
     * @return the registration number associated with the CPF
     */
    public String getRegistrationNumber(String cpfNo, String accessToken) {
        log.info("Fetching registration number for CPF: {}", cpfNo);
        String response = webClient.get()
                .uri(String.format(url, cpfNo))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode;
        try {
            rootNode = mapper.readTree(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        JsonNode firstCodigoImovel = rootNode
                .path("result")
                .path(0)
                .path("codigoimovel");

        return firstCodigoImovel.asText();
    }
}