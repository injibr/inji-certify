package io.mosip.certify.api.dataprovider.impl;

import io.mosip.certify.api.dataprovider.DataProviderService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class EcaDataProvider implements DataProviderService {

    private final WebClient webClient;
    private final String apiUrl;
    private final EcaTokenClient ecaTokenClient;

    public EcaDataProvider(WebClient webClient,
                           @Value("${eca.api.url}") String apiUrl,
                           EcaTokenClient ecaTokenClient) {
        this.webClient = webClient;
        this.apiUrl = apiUrl;
        this.ecaTokenClient = ecaTokenClient;
    }

    @Override
    public String getDocumentType() {
        return "ECACredential";
    }

    @Override
    public JSONObject getData(String cpfNumber) throws Exception {
        String accessToken = ecaTokenClient.getAccessToken();

        String response = webClient.get()
                .uri(String.format(apiUrl, cpfNumber))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (response == null) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No data found for ECA for CPF: " + cpfNumber);
        }

        JSONObject ecaData = new JSONObject(response);
        String dataNascimento = ecaData.getString("DataNascimento");
        boolean is18orOlder = isAdult(dataNascimento);
        log.info("CPF {} - DataNascimento: {}, is18orOlder: {}", cpfNumber, dataNascimento, is18orOlder);

        JSONObject result = new JSONObject();
        result.put("is18orOlder", is18orOlder);
        return result;
    }

    private boolean isAdult(String dataNascimento) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate birthDate = LocalDate.parse(dataNascimento, formatter);
        return Period.between(birthDate, LocalDate.now()).getYears() >= 18;
    }
}
