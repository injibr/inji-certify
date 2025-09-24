package io.mosip.certify.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.certify.entity.CertifyKeys;
import io.mosip.certify.repository.ConfigurationRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.LinkedHashMap;
import java.util.List;

@Service
public class CertifyKeysService {
    private final ConfigurationRepository repository;

    private final ObjectMapper objectMapper;

    private final Environment environment;

    public CertifyKeysService(ConfigurationRepository repository, ObjectMapper objectMapper, Environment environment) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    public LinkedHashMap<String, LinkedHashMap<String, Object>> getIssuerMetadata() {
        LinkedHashMap<String, LinkedHashMap<String, Object>> result = new LinkedHashMap<>();
        List<CertifyKeys> allConfigs = repository.findAll();

        try {
            for (CertifyKeys config : allConfigs) {
                String resolvedJson = resolvePlaceholders(config.getValue());

                LinkedHashMap<String, LinkedHashMap<String, Object>> singleEntry =
                        objectMapper.readValue(resolvedJson, new TypeReference<>() {
                        });

                result.putAll(singleEntry); // Each JSON will have 1 top-level key like "CAR_mock"
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Invalid JSON format in DB", e);
        }
    }

    public String resolvePlaceholders(String rawJson) {
        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}");
        return helper.replacePlaceholders(rawJson, environment::getProperty);
    }
}
