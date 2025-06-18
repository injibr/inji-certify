package io.mosip.certify;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import io.mosip.certify.dataprovider234.DataProviderService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.Map;

//@ConditionalOnProperty(value = "mosip.certify.integration.vci-plugin", havingValue = "TestVCIPluginImpl")
@Component
@Slf4j
public class TestMockDataProviderPluginImpl implements DataProviderPlugin {
    private final DataProviderService dataProviderService;

    public TestMockDataProviderPluginImpl(DataProviderService dataProviderService) {
        this.dataProviderService = dataProviderService;
    }

    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        JSONObject data = null;
        try {
            data = dataProviderService.getData();
            return data;
        }catch (Exception ex){
            log.info("Exception while fetching data from TestMockDataProviderPluginImpl", ex);
            return null;
        }
    }
}
