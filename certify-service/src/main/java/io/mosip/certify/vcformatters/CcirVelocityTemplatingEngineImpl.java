package io.mosip.certify.vcformatters;

import io.mosip.certify.core.constants.Constants;
import io.mosip.certify.core.constants.ErrorConstants;
import io.mosip.certify.core.constants.VCDM2Constants;
import io.mosip.certify.core.constants.VCDMConstants;
import io.mosip.certify.core.exception.CertifyException;
import io.mosip.certify.core.exception.RenderingTemplateException;
import io.mosip.certify.core.spi.RenderingTemplateService;
import io.mosip.certify.entity.CredentialTemplate;
import io.mosip.certify.repository.CredentialTemplateRepository;
import io.mosip.certify.services.CredentialUtils;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.tools.generic.DateTool;
import org.apache.velocity.tools.generic.EscapeTool;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
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
@Service("velocityEngineCcir")
public class CcirVelocityTemplatingEngineImpl  implements VCFormatter {
    VelocityEngine engine;
    public static final String DELIMITER = ":";
    public static final String TEMPLATE_CACHE = "templatecache";
    @Autowired
    CredentialTemplateRepository credentialTemplateRepository;
    @Autowired
    RenderingTemplateService renderingTemplateService;

    @Value("${mosip.certify.data-provider-plugin.vc-expiry-duration:P730d}")
    String defaultExpiryDuration;

    @Value("${mosip.certify.data-provider-plugin.id-field-prefix-uri:}")
    String idPrefix;

    @PostConstruct
    public void initialize() {
        engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.INPUT_ENCODING, "UTF-8");
        engine.setProperty(RuntimeConstants.OUTPUT_ENCODING, "UTF-8");
        engine.init();
    }

    @SneakyThrows
    @Override
    public String format(JSONObject valueMap, Map<String, Object> templateSettings) {
        String templateName = templateSettings.get(Constants.TEMPLATE_NAME).toString();
        String template = getTemplate(templateName);
        if (template == null) {
            log.error("Template {} not found", templateName);
            throw new CertifyException(ErrorConstants.EXPECTED_TEMPLATE_NOT_FOUND);
        }
        String issuer = templateSettings.get(Constants.ISSUER_URI).toString();
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
                flattenJsonObject((JSONObject) value, finalTemplate);
            } else if(value instanceof JSONArray){
                flattenJsonArray(key, (JSONArray) value, finalTemplate);
            }
        }
        finalTemplate.put("_dateTool", new DateTool());
        finalTemplate.put("_esc", new EscapeTool());
        finalTemplate.put("_issuer", issuer);
        if (templateSettings.containsKey(Constants.RENDERING_TEMPLATE_ID) && templateName.contains(VCDM2Constants.URL)) {
            try {
                finalTemplate.put("_renderMethodSVGdigest",
                        CredentialUtils.getDigestMultibase(renderingTemplateService.getSvgTemplate(
                                (String) templateSettings.get(Constants.RENDERING_TEMPLATE_ID)).getTemplate()));
            } catch (RenderingTemplateException e) {
                log.error("SVG Template: " + templateSettings.get(Constants.RENDERING_TEMPLATE_ID) + " not available in DB", e);
            }
        }
        if (!valueMap.has(VCDM2Constants.VALID_UNITL) && StringUtils.isNotEmpty(defaultExpiryDuration)) {
            Duration duration;
            try {
                duration = Duration.parse(defaultExpiryDuration);
            } catch (DateTimeParseException e) {
                duration = Duration.parse("P730D");
            }
            String expiryTime = ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(duration.getSeconds()).format(DateTimeFormatter.ofPattern(Constants.UTC_DATETIME_PATTERN));
            finalTemplate.put(VCDM2Constants.VALID_UNITL, expiryTime);
        }
        if (!valueMap.has(VCDM2Constants.VALID_FROM)) {
            finalTemplate.put(VCDM2Constants.VALID_FROM, ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(Constants.UTC_DATETIME_PATTERN)));
        }
        VelocityContext context = new VelocityContext(finalTemplate);
        engine.evaluate(context, writer, templateName, template);
        if (StringUtils.isNotEmpty(idPrefix)) {
            JSONObject j = new JSONObject(writer.toString());
            j.put(VCDMConstants.ID, idPrefix + UUID.randomUUID());
            return j.toString();
        }
        return writer.toString();
    }

    private String escapeJsonString(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @SneakyThrows
    private void flattenJsonObject(JSONObject jsonObject, Map<String, Object> finalTemplate) {
        Iterator<String> jsonKeys = jsonObject.keys();
        while (jsonKeys.hasNext()) {
            String jsonKey = jsonKeys.next();
            Object innerVal = jsonObject.get(jsonKey);
            if (innerVal instanceof JSONObject) {
                // Flatten nested JSONObject (e.g. dadosUltimoCcir)
                JSONObject jsonObjectInner = new JSONObject(jsonObject.getString(jsonKey));
                Iterator<String> jsonKeysInner = jsonObjectInner.keys();
                while (jsonKeysInner.hasNext()) {
                    String jsonKeyInner = jsonKeysInner.next();
                    finalTemplate.put(jsonKeyInner, Objects.equals(jsonObjectInner.get(jsonKeyInner), null) ? "" : escapeJsonString(jsonObjectInner.get(jsonKeyInner).toString()));
                }
            } else if (innerVal instanceof JSONArray) {
                // Nested JSONArray (e.g. ccir.titulares):
                // 1. Extract declarante fields to root level
                // 2. Put full array as string in the array key name
                flattenNestedJsonArray(jsonKey, (JSONArray) innerVal, finalTemplate);
            } else {
                finalTemplate.put(jsonKey, Objects.equals(innerVal, null) ? "" : escapeJsonString(innerVal.toString()));
            }
        }
    }

    @SneakyThrows
    private void flattenNestedJsonArray(String arrayKey, JSONArray array, Map<String, Object> finalTemplate) {
        // Find the declarante element and extract its fields to root
        for (int i = 0; i < array.length(); i++) {
            JSONObject arrObj = array.getJSONObject(i);
            if ("S".equals(arrObj.optString("declarante")) || arrObj.optInt("declarante", 0) == 1) {
                Iterator<String> arrKeys = arrObj.keys();
                while (arrKeys.hasNext()) {
                    String arrKey = arrKeys.next();
                    Object val = arrObj.get(arrKey);
                    String strVal = val == null || val.toString().equals("null") ? "" : val.toString();
                    // For "declarante" field, use the nomeTitular instead of "S"/1
                    if ("declarante".equals(arrKey)) {
                        strVal = arrObj.optString("nomeTitular", strVal);
                    }
                    finalTemplate.put(arrKey, escapeJsonString(strVal));
                }
                break;
            }
        }
        // Put the full array as string (without declarante/nacionalidade)
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            JSONObject arrObj = array.getJSONObject(i);
            JSONObject filteredObj = new JSONObject();
            Iterator<String> arrKeys = arrObj.keys();
            while (arrKeys.hasNext()) {
                String arrKey = arrKeys.next();
                if ("declarante".equals(arrKey) || "nacionalidade".equals(arrKey)) continue;
                Object val = arrObj.get(arrKey);
                filteredObj.put(arrKey, val == null || val.toString().equals("null") ? "" : val.toString());
            }
            jsonArray.put(filteredObj);
        }
        String quoted = JSONObject.quote(jsonArray.toString());
        finalTemplate.put(arrayKey, quoted.substring(1, quoted.length() - 1));
    }

    @SneakyThrows
    private void flattenJsonArray(String key, JSONArray array, Map<String, Object> finalTemplate) {
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            JSONObject jsonObject = array.getJSONObject(i);
            JSONObject theObject = new JSONObject();
            Iterator<String> jsonKeys = jsonObject.keys();
            while (jsonKeys.hasNext()) {
                String jsonKey = jsonKeys.next();
                String newValue = jsonObject.get(jsonKey).toString().equals("null") ? "" : jsonObject.get(jsonKey).toString();
                theObject.put(jsonKey, newValue);
            }
            jsonArray.put(theObject);
        }
        String quoted = JSONObject.quote(jsonArray.toString());
        finalTemplate.put(key, quoted.substring(1, quoted.length() - 1));
    }

    @Cacheable(cacheNames = TEMPLATE_CACHE, key = "#key")
    public String getTemplate(String key) {
        if (!key.contains(DELIMITER)) {
            return null;
        }
        String credentialType = key.split(DELIMITER)[0];
        String context = key.split(DELIMITER, 2)[1];
        CredentialTemplate template = credentialTemplateRepository.findByCredentialTypeAndContext(credentialType, context).orElse(null);
        if (template != null) {
            return template.getTemplate();
        } else
            return null;
    }
}
