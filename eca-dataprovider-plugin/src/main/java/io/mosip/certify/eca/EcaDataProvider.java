package io.mosip.certify.eca;

import io.mosip.certify.api.spi.DataProviderPlugin;
import io.mosip.certify.api.exception.DataProviderExchangeException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Component
public class EcaDataProvider implements DataProviderPlugin {

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
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        // Try multiple claim names for CPF (Gov.br uses 'sub', MOSIP uses 'individual_id')
        String cpfNumber = (String) identityDetails.get("individual_id");
        if (cpfNumber == null) {
            cpfNumber = (String) identityDetails.get("sub");
        }
        if (cpfNumber == null) {
            throw new DataProviderExchangeException("CPF not found in identity details");
        }

        try {
            String accessToken = ecaTokenClient.getAccessToken();

            String response = webClient.get()
                    .uri(String.format(apiUrl, cpfNumber))
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null) {
                throw new DataProviderExchangeException("No data found for ECA for CPF: " + cpfNumber);
            }

            JSONObject ecaData = new JSONObject(response);
            String dataNascimento = ecaData.getString("dataNascimento");
            int age = getAge(dataNascimento);
            log.info("CPF {} - dataNascimento: {}, age: {}", cpfNumber, dataNascimento, age);

            JSONObject result = new JSONObject();
            result.put("isOver12", age >= 12);
            result.put("isOver14", age >= 14);
            result.put("isOver16", age >= 16);
            result.put("isOver18", age >= 18);
            return result;
        } catch (JSONException e) {
            log.error("Error parsing ECA response", e);
            throw new DataProviderExchangeException("Error parsing ECA response: " + e.getMessage());
        }
    }

    private int getAge(String dataNascimento) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate birthDate = LocalDate.parse(dataNascimento, formatter);
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
