package io.mosip.certify.api.dataprovider.impl;

import io.mosip.certify.api.dataprovider.DataProviderService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;

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
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No data found for CCIR for Cpf:" + cpfNumber);
        }
        JSONObject jsonResponse = new JSONObject(response);
        if (jsonResponse.isNull("ccir")) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "CCIR data is null for property code: " + registrationNumber + ". Property may be cancelled.");
        }
        return flattenCcir(jsonResponse.getJSONObject("ccir"));
    }

    // INJIBR-CUSTOM: flattens ccir root object — extracts declarante fields to root,
    // serializes titulares array as escaped JSON string, matching cm-sql-populate-base.yaml template
    private JSONObject flattenCcir(JSONObject ccir) {
        JSONObject result = new JSONObject();
        Iterator<String> keys = ccir.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object val = ccir.isNull(key) ? "" : ccir.get(key);
            if (val instanceof JSONArray) {
                result.put(key, flattenTitulares(key, (JSONArray) val, result));
            } else if (val instanceof JSONObject) {
                // flatten nested objects (e.g. dadosUltimoCcir)
                Iterator<String> innerKeys = ((JSONObject) val).keys();
                while (innerKeys.hasNext()) {
                    String innerKey = innerKeys.next();
                    Object innerVal = ((JSONObject) val).isNull(innerKey) ? "" : ((JSONObject) val).get(innerKey);
                    result.put(innerKey, innerVal.toString());
                }
            } else {
                result.put(key, val.toString());
            }
        }
        return result;
    }

    // INJIBR-CUSTOM: extracts declarante fields to root level, serializes full array as escaped JSON string
    private String flattenTitulares(String arrayKey, JSONArray array, JSONObject result) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            if ("S".equals(item.optString("declarante")) || item.optInt("declarante", 0) == 1) {
                Iterator<String> arrKeys = item.keys();
                while (arrKeys.hasNext()) {
                    String arrKey = arrKeys.next();
                    String strVal = item.isNull(arrKey) ? "" : item.get(arrKey).toString();
                    if ("declarante".equals(arrKey)) strVal = item.optString("nomeTitular", strVal);
                    result.put(arrKey, strVal);
                }
                break;
            }
        }
        // serialize array without declarante/nacionalidade fields
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            JSONObject filteredItem = new JSONObject();
            Iterator<String> arrKeys = item.keys();
            while (arrKeys.hasNext()) {
                String arrKey = arrKeys.next();
                if ("declarante".equals(arrKey) || "nacionalidade".equals(arrKey)) continue;
                filteredItem.put(arrKey, item.isNull(arrKey) ? "" : item.get(arrKey).toString());
            }
            filtered.put(filteredItem);
        }
        String quoted = JSONObject.quote(filtered.toString());
        return quoted.substring(1, quoted.length() - 1);
    }

}
