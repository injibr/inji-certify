package io.mosip.certify.vcformatters;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class VelocityTemplatingEngineFactory {
    @Autowired
    private Map<String, VCFormatter> velocityMap;

    public VCFormatter getVelocityInstance(String version) {
        VCFormatter vcFormatter = velocityMap.get(version);
        if (vcFormatter == null) {
            throw new IllegalArgumentException("Invalid instance value: " + version);
        }
        return vcFormatter;
    }

}
