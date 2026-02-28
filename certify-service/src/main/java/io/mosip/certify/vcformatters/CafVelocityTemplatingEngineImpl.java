package io.mosip.certify.vcformatters;

import io.mosip.certify.core.constants.Constants;
import io.mosip.certify.core.constants.ErrorConstants;
import io.mosip.certify.core.constants.VCDM2Constants;
import io.mosip.certify.core.constants.VCDMConstants;
import io.mosip.certify.core.exception.CertifyException;
import io.mosip.certify.core.exception.RenderingTemplateException;
import io.mosip.certify.services.CredentialUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.tools.generic.DateTool;
import org.apache.velocity.tools.generic.EscapeTool;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service("velocityEngineCaf")
public class CafVelocityTemplatingEngineImpl extends VelocityTemplatingEngineImpl {

    @SneakyThrows
    @Override
    public String format(JSONObject valueMap, Map<String, Object> templateSettings) {
        String templateName = templateSettings.get(Constants.TEMPLATE_NAME).toString();
        String vcTemplateString = getCachedCredentialConfig(templateName).getVcTemplate();
        if (vcTemplateString == null) {
            log.error("Template {} not found (vcTemplate is null)", templateName);
            throw new CertifyException(ErrorConstants.EXPECTED_TEMPLATE_NOT_FOUND);
        }
        vcTemplateString = new String(Base64.decodeBase64(vcTemplateString));
        String issuer = templateSettings.get(Constants.DID_URL).toString();
        StringWriter writer = new StringWriter();
        Map<String, Object> finalTemplate = new HashMap<>();
        Iterator<String> keys = valueMap.keys();
        while(keys.hasNext()) {
            String key = keys.next();
            Object value = Objects.equals(valueMap.get(key),null)?"":valueMap.get(key);
            if (value instanceof List) {
                finalTemplate.put(key, new JSONArray((List<Object>) value));
            } else if (value.getClass().isArray()) {
                finalTemplate.put(key, new JSONArray(List.of(value)));
            } else if (value instanceof Integer | value instanceof Float | value instanceof Long | value instanceof Double| value instanceof BigDecimal) {
                finalTemplate.put(key, value);
            } else if (value instanceof String){
                finalTemplate.put(key, JSONObject.wrap(value));
            } else if (value instanceof JSONObject) {
                JSONObject jsonObject = (JSONObject)value;
                Iterator<String> jsonKeys = jsonObject.keys();
                while (jsonKeys.hasNext()){
                    String jsonKey = jsonKeys.next();
                    if (jsonObject.get(jsonKey) instanceof JSONObject) {
                        JSONObject jsonObjectInner =  new JSONObject(jsonObject.getString(jsonKey));
                        Iterator<String> jsonKeysInner = jsonObjectInner.keys();
                        while (jsonKeysInner.hasNext()){
                             String jsonKeyInner = jsonKeysInner.next();
                            finalTemplate.put(jsonKeyInner, Objects.equals(jsonObjectInner.get(jsonKeyInner),null)?"":jsonObjectInner.get(jsonKeyInner));
                        }
                    }else {
                    finalTemplate.put(jsonKey, Objects.equals(jsonObject.get(jsonKey),null)?"":jsonObject.get(jsonKey));
                    }
                }
            } else if(value instanceof JSONArray){
                Map<String,String> allData = new HashMap<>();
                List<String> allObjects = new ArrayList<>();
                for (int i = 0; i < ((JSONArray) value).length(); i++) {
                    Map<String,String> theObject = new HashMap<>();
                    JSONObject jsonObject = (JSONObject) ((JSONArray) value).get(i);
                    Iterator<String> jsonKeys = jsonObject.keys();
                    while (jsonKeys.hasNext()){
                        String jsonKey = jsonKeys.next();
                        if (allData.containsKey(jsonKey)){
                            String prevValue = Objects.equals(allData.get(jsonKey),null)?"":allData.get(jsonKey);
                            String newValue = jsonObject.get(String.valueOf(jsonKey)).toString().equals("null")?"":jsonObject.get(String.valueOf(jsonKey)).toString();
                            String combinedValue = prevValue +","+newValue;
                            allData.put(jsonKey,combinedValue);
                            theObject.put(jsonKey,newValue);
                        }else {
                            allData.put(jsonKey, jsonObject.get(jsonKey).toString());
                            theObject.put(jsonKey,jsonObject.get(jsonKey).toString());
                        }
                    }
                    allObjects.add(theObject.toString());
                }
                finalTemplate.put(key, allObjects.toString());
            }
        }
        finalTemplate.put("_dateTool", new DateTool());
        finalTemplate.put("_esc", new EscapeTool());
        finalTemplate.put("_issuer", issuer);
        if (templateSettings.containsKey(Constants.RENDERING_TEMPLATE_ID) && templateName.contains(VCDM2Constants.URL)) {
            try {
                finalTemplate.put("_renderMethodSVGdigest",
                        CredentialUtils.getDigestMultibase(renderingTemplateService.getTemplate(
                                (String) templateSettings.get(Constants.RENDERING_TEMPLATE_ID)).getTemplate()));
            } catch (RenderingTemplateException e) {
                log.error("SVG Template: " + templateSettings.get(Constants.RENDERING_TEMPLATE_ID) + " not available in DB", e);
            }
        }
        if (!valueMap.has(VCDM2Constants.VALID_UNTIL) && StringUtils.isNotEmpty(defaultExpiryDuration)) {
            Duration duration;
            try {
                duration = Duration.parse(defaultExpiryDuration);
            } catch (DateTimeParseException e) {
                duration = Duration.parse("P730D");
            }
            String expiryTime = ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(duration.getSeconds()).format(DateTimeFormatter.ofPattern(Constants.UTC_DATETIME_PATTERN));
            finalTemplate.put(VCDM2Constants.VALID_UNTIL, expiryTime);
        }
        if (!valueMap.has(VCDM2Constants.VALID_FROM)) {
            finalTemplate.put(VCDM2Constants.VALID_FROM, ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(Constants.UTC_DATETIME_PATTERN)));
        }
        VelocityContext context = new VelocityContext(finalTemplate);
        engine.evaluate(context, writer, templateName, vcTemplateString);
        if (StringUtils.isNotEmpty(idPrefix)) {
            JSONObject j = new JSONObject(writer.toString());
            j.put(VCDMConstants.ID, idPrefix + UUID.randomUUID());
            return j.toString();
        }
        return writer.toString();
    }
}
