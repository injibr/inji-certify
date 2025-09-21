package io.mosip.certify.api.dataprovider;

import org.json.JSONObject;


public interface DataProviderService {
    String getDocumentType();
    JSONObject getData(String cpfNumber) throws Exception;
}
