package io.mosip.certify.api.dataprovider.impl;

import io.mosip.certify.api.dataprovider.DataProviderService;
import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DataProviderPluginImpl implements DataProviderPlugin {
    private final List<DataProviderService> providers;
    private final Map<String, DataProviderService> instanceMap = new HashMap<>();

    @Autowired
    public DataProviderPluginImpl(List<DataProviderService> providers) {
        this.providers = providers;
    }

    @PostConstruct
    public void init() {
        for (DataProviderService provider : providers) {
            instanceMap.put(provider.getDocumentType(), provider);
        }
    }
    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        //Sending docType as certify key from mimoto that should match the instance Map key, to integrate with govbr
        DataProviderService dataProviderService = instanceMap.get((String) identityDetails.get("docType"));
        if (dataProviderService == null) {
            throw new IllegalArgumentException("No provider found for: " + "document");
        }
        try {
            JSONObject data = dataProviderService.getData((String) identityDetails.get("sub"));
            log.info("Data fetched from DataProviderService : {}", data);
            return data;
        } catch (Exception e) {
            log.info("Error while fetching data from DataProviderService: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "Error while fetching data from DataProviderService", e);
        }
    }
}
