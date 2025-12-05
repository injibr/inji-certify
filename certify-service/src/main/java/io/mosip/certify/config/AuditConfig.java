package io.mosip.certify.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class AuditConfig {

    @Value("${audit.enabled:true}")
    private boolean auditEnabled;
}
