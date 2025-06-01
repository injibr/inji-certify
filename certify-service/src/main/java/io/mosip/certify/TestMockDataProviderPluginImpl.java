package io.mosip.certify;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

//@ConditionalOnProperty(value = "mosip.certify.integration.vci-plugin", havingValue = "TestVCIPluginImpl")
@Component
@Slf4j
public class TestMockDataProviderPluginImpl implements DataProviderPlugin {
    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
//        return Map.of();
        JSONObject obj = new JSONObject();
        try {
            obj.put("fullName","Kash");
            obj.put("mobile","1245235");
            obj.put("dob","1123");
            obj.put("email","asdfvd");
            obj.put("gender","df");
        }catch (Exception ex){
            log.info("Exception while fetching data from TestMockDataProviderPluginImpl", ex);
        }

        return obj;
    }
}
