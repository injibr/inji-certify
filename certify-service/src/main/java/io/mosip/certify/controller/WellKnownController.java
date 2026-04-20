package io.mosip.certify.controller;

import io.mosip.certify.core.dto.CredentialIssuerMetadataDTO;
import io.mosip.certify.core.spi.CredentialConfigurationService;
import io.mosip.certify.core.spi.VCIssuanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/.well-known")
public class WellKnownController {

    @Autowired
    private CredentialConfigurationService credentialConfigurationService;

    @Autowired
    private VCIssuanceService vcIssuanceService;

    @GetMapping(value = "/openid-credential-issuer", produces = "application/json")
    public CredentialIssuerMetadataDTO getCredentialIssuerMetadata(
            @RequestParam(name = "version", required = false, defaultValue = "latest") String version,
            // INJIBR-CUSTOM: issuer_id param para lookup multi-issuer govbr
            @RequestParam(name = "issuer_id", required = false) String issuerId) {
        if (issuerId != null && !issuerId.isBlank()) {
            return credentialConfigurationService.fetchCredentialIssuerMetadataByIssuerId(issuerId);
        }
        return credentialConfigurationService.fetchCredentialIssuerMetadata(version);
    }

    @GetMapping(value = "/did.json")
    public Map<String, Object> getDIDDocument() {
        return vcIssuanceService.getDIDDocument();
    }
}

