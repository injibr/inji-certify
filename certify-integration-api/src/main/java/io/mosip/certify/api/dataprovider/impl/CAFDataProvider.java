package io.mosip.certify.api.dataprovider.impl;

import io.mosip.certify.api.dataprovider.DataProviderService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Iterator;

@Slf4j
@Component
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
        return flattenCaf(new JSONObject(response));
    }

    // INJIBR-CUSTOM: flattens CAF response to match cm-sql-populate-base.yaml template.
    // API returns: { "caf": { "numero", "entidadeEmissora": {...}, ... }, "membros": [...], "areas": [...] }
    // Template expects: numero, situacao, cnpj, razaoSocial, emissor flat + membros/areas as JSON strings
    private JSONObject flattenCaf(JSONObject root) {
        JSONObject result = new JSONObject();
        // flatten caf object: simple fields go to root, entidadeEmissora nested fields go to root
        if (!root.isNull("caf")) {
            JSONObject caf = root.getJSONObject("caf");
            Iterator<String> cafKeys = caf.keys();
            while (cafKeys.hasNext()) {
                String key = cafKeys.next();
                if (caf.isNull(key)) {
                    result.put(key, "");
                } else if (caf.get(key) instanceof JSONObject) {
                    // INJIBR-CUSTOM: flatten entidadeEmissora -> cnpj, razaoSocial, emissor
                    JSONObject nested = caf.getJSONObject(key);
                    Iterator<String> nk = nested.keys();
                    while (nk.hasNext()) {
                        String nestedKey = nk.next();
                        result.put(nestedKey, nested.isNull(nestedKey) ? "" : nested.get(nestedKey).toString());
                    }
                } else {
                    result.put(key, caf.get(key).toString());
                }
            }
        }
        // INJIBR-CUSTOM: serialize membros/areas arrays as escaped JSON string
        for (String arrayKey : new String[]{"membros", "areas"}) {
            if (!root.isNull(arrayKey) && root.get(arrayKey) instanceof JSONArray) {
                String quoted = JSONObject.quote(serializeArray(root.getJSONArray(arrayKey)));
                result.put(arrayKey, quoted.substring(1, quoted.length() - 1));
            }
        }
        return result;
    }

    private String serializeArray(JSONArray array) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) continue;
            JSONObject obj = (JSONObject) item;
            JSONObject flat = new JSONObject();
            Iterator<String> k = obj.keys();
            while (k.hasNext()) {
                String key = k.next();
                flat.put(key, obj.isNull(key) ? "" : obj.get(key).toString());
            }
            out.put(flat);
        }
        return out.toString();
    }
}
