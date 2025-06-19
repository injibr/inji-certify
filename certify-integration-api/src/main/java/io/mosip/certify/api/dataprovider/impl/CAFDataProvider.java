package io.mosip.certify.api.dataprovider.impl;

import io.mosip.certify.api.dataprovider.DataProviderService;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
public class CAFDataProvider implements DataProviderService {

    @Override
    public String getDocumentType() {
        return "CAFDocument";
    }

    @Override
    public JSONObject getData() throws JSONException {
        return new JSONObject()
                .put("name", "John Doe")
                .put("age", 30)
                .put("occupation", "Software Engineer")
                .put("location", "New York");
    }
}
