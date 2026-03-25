package io.mosip.certify.services;

import foundation.identity.jsonld.JsonLDObject;
import io.mosip.certify.api.dto.VCResult;
import io.mosip.certify.api.spi.AuditPlugin;
import io.mosip.certify.api.spi.DataProviderPlugin;
import io.mosip.certify.core.dto.*;
import io.mosip.certify.core.spi.RenderingTemplateService;
import io.mosip.certify.core.util.SecurityHelperService;
import io.mosip.certify.entity.CredentialTemplate;
import io.mosip.certify.proof.ProofValidator;
import io.mosip.certify.proof.ProofValidatorFactory;
import io.mosip.certify.repository.CredentialTemplateRepository;
import io.mosip.certify.vcformatters.EcaVelocityTemplatingEngineImpl;
import io.mosip.certify.vcformatters.VelocityTemplatingEngineFactory;
import io.mosip.certify.vcsigners.VCSigner;
import lombok.SneakyThrows;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileWriter;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CertifyIssuanceECAFlowTest {

    @InjectMocks
    private CertifyIssuanceServiceImpl issuanceService;

    @Mock
    private ParsedAccessToken parsedAccessToken;

    @Mock
    private VCSigner vcSigner;

    @Mock
    private DataProviderPlugin dataProviderPlugin;

    @Mock
    private ProofValidatorFactory proofValidatorFactory;

    @Mock
    private ProofValidator proofValidator;

    @Mock
    private VCICacheService vciCacheService;

    @Mock
    private SecurityHelperService securityHelperService;

    @Mock
    private AuditPlugin auditWrapper;

    @Mock
    private VelocityTemplatingEngineFactory velocityTemplatingEngineFactory;

    @Mock
    private CertifyKeysService certifyKeysService;

    @Mock
    private CredentialTemplateRepository credentialTemplateRepository;

    @Mock
    private RenderingTemplateService renderingTemplateService;

    @Mock
    private io.mosip.kernel.keymanagerservice.service.KeymanagerService keymanagerService;

    private EcaVelocityTemplatingEngineImpl realVelocityEngine;

    private static final String ISSUER_URI = "https://injicertify.credenciaisverificaveis-hml.dataprev.gov.br";
    private static final String TEST_ACCESS_TOKEN_HASH = "test-token-hash";

    private static final String ECA_TEMPLATE = "{" +
            "\"@context\": [\"https://www.w3.org/2018/credentials/v1\"]," +
            "\"issuer\": \"${_issuer}\"," +
            "\"type\": [\"VerifiableCredential\",\"ECACredential\"]," +
            "\"issuanceDate\": \"${validFrom}\"," +
            "\"expirationDate\": \"${validUntil}\"," +
            "\"credentialSubject\": {" +
            "\"id\": \"${_holderId}\"," +
            "\"isOver18\": ${isOver18}" +
            "}}";

    private static final String ECA_DATA_OVER18_JSON = """
            {
                "isOver18": true
            }
            """;

    private static final String ECA_DATA_UNDER18_JSON = """
            {
                "isOver18": false
            }
            """;

    private static final String OUTPUT_DIR = "target/test-output";

    private void writeJsonOutput(String filename, String json) {
        try {
            File dir = new File(OUTPUT_DIR);
            dir.mkdirs();
            try (FileWriter fw = new FileWriter(new File(dir, filename))) {
                fw.write(json);
            }
            System.out.println("Output saved to: " + OUTPUT_DIR + "/" + filename);
        } catch (Exception e) {
            System.err.println("Failed to write output: " + e.getMessage());
        }
    }

    @SneakyThrows
    private String buildFinalOutput(JSONObject vcJson) {
        JSONObject vcMetadata = new JSONObject().put("issuer", "MGI");
        JSONObject inner = new JSONObject()
                .put("vcMetadata", vcMetadata)
                .put("verifiableCredential", new JSONObject().put("credential", vcJson));
        JSONObject finalOutput = new JSONObject();
        finalOutput.put("verifiableCredential", new org.json.JSONArray().put(inner));
        return finalOutput.toString(2);
    }

    @SneakyThrows
    @Before
    public void setUp() {
        realVelocityEngine = new EcaVelocityTemplatingEngineImpl();
        ReflectionTestUtils.setField(realVelocityEngine, "credentialTemplateRepository", credentialTemplateRepository);
        ReflectionTestUtils.setField(realVelocityEngine, "renderingTemplateService", renderingTemplateService);
        ReflectionTestUtils.setField(realVelocityEngine, "defaultExpiryDuration", "P90D");
        ReflectionTestUtils.setField(realVelocityEngine, "idPrefix", "");
        realVelocityEngine.initialize();

        CredentialTemplate ecaTemplate = new CredentialTemplate();
        ecaTemplate.setTemplate(ECA_TEMPLATE);
        ecaTemplate.setCredentialType("ECACredential,VerifiableCredential");
        ecaTemplate.setContext("https://www.w3.org/2018/credentials/v1");
        when(credentialTemplateRepository.findByCredentialTypeAndContext(
                "ECACredential,VerifiableCredential", "https://www.w3.org/2018/credentials/v1"))
                .thenReturn(Optional.of(ecaTemplate));

        LinkedHashMap<String, LinkedHashMap<String, Object>> issuerMetadata = new LinkedHashMap<>();
        LinkedHashMap<String, Object> mgiMetadata = new LinkedHashMap<>();
        LinkedHashMap<String, Object> credConfigs = new LinkedHashMap<>();
        LinkedHashMap<String, Object> ecaConfig = new LinkedHashMap<>();
        ecaConfig.put("format", "ldp_vc");
        ecaConfig.put("scope", "openid");
        LinkedHashMap<String, Object> credDef = new LinkedHashMap<>();
        credDef.put("type", Arrays.asList("VerifiableCredential", "ECACredential"));
        ecaConfig.put("credential_definition", credDef);
        ecaConfig.put("proof_types_supported", Map.of("jwt", Map.of("proof_signing_alg_values_supported", List.of("RS256"))));
        credConfigs.put("ECACredential", ecaConfig);
        mgiMetadata.put("credential_configurations_supported", credConfigs);
        mgiMetadata.put("credential_issuer", ISSUER_URI);
        mgiMetadata.put("credential_endpoint", ISSUER_URI + "/v1/certify/issuance/credential");
        issuerMetadata.put("MGI", mgiMetadata);

        ReflectionTestUtils.setField(issuanceService, "issuerMetadata", issuerMetadata);
        ReflectionTestUtils.setField(issuanceService, "vcSignAlgorithm", "Ed25519Signature2020");
        ReflectionTestUtils.setField(issuanceService, "cNonceExpireSeconds", 300);
        ReflectionTestUtils.setField(issuanceService, "issuerURI", ISSUER_URI);
        ReflectionTestUtils.setField(issuanceService, "issuerPublicKeyURI", ISSUER_URI + "#key-0");
        ReflectionTestUtils.setField(issuanceService, "renderTemplateId", "");

        when(parsedAccessToken.isActive()).thenReturn(true);
        when(parsedAccessToken.getAccessTokenHash()).thenReturn(TEST_ACCESS_TOKEN_HASH);
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "openid");
        claims.put("client_id", "test-client");
        when(parsedAccessToken.getClaims()).thenReturn(claims);

        when(proofValidatorFactory.getProofValidator(any())).thenReturn(proofValidator);
        when(proofValidator.validate(any(), any())).thenReturn(true);
        when(proofValidator.getKeyMaterial(any())).thenReturn("did:jwk:test-holder-key");

        when(velocityTemplatingEngineFactory.getVelocityInstance("velocityEngineEca")).thenReturn(realVelocityEngine);
    }

    @SneakyThrows
    @Test
    public void getCredential_ECACredential_over18() {
        when(dataProviderPlugin.fetchData(any())).thenReturn(new JSONObject(ECA_DATA_OVER18_JSON));

        ArgumentCaptor<String> unsignedVcCaptor = ArgumentCaptor.forClass(String.class);
        VCResult<JsonLDObject> vcResult = new VCResult<>();
        vcResult.setCredential(new JsonLDObject());
        when(vcSigner.attachSignature(unsignedVcCaptor.capture(), any(Map.class))).thenReturn(vcResult);

        CredentialRequest request = new CredentialRequest();
        request.setFormat("ldp_vc");
        request.setDoctype("ECACredential");
        request.setIssuerId("MGI");
        CredentialDefinition credDef = new CredentialDefinition();
        credDef.setContext(List.of("https://www.w3.org/2018/credentials/v1"));
        credDef.setType(List.of("VerifiableCredential", "ECACredential"));
        credDef.setCredentialSubject(new HashMap<>());
        request.setCredential_definition(credDef);
        CredentialProof proof = new CredentialProof();
        proof.setProof_type("jwt");
        proof.setJwt("test-jwt");
        request.setProof(proof);

        CredentialResponse<?> response = issuanceService.getCredential(request);
        assertNotNull(response);

        verify(vcSigner, times(1)).attachSignature(any(), any());
        String unsignedVC = unsignedVcCaptor.getValue();
        JSONObject vcJson = new JSONObject(unsignedVC);
        JSONObject cs = vcJson.getJSONObject("credentialSubject");

        assertTrue(cs.getBoolean("isOver18"));

        String output = buildFinalOutput(vcJson);
        writeJsonOutput("eca-over18-output.json", output);
        System.out.println(output);
    }

    @SneakyThrows
    @Test
    public void getCredential_ECACredential_under18() {
        when(dataProviderPlugin.fetchData(any())).thenReturn(new JSONObject(ECA_DATA_UNDER18_JSON));

        ArgumentCaptor<String> unsignedVcCaptor = ArgumentCaptor.forClass(String.class);
        VCResult<JsonLDObject> vcResult = new VCResult<>();
        vcResult.setCredential(new JsonLDObject());
        when(vcSigner.attachSignature(unsignedVcCaptor.capture(), any(Map.class))).thenReturn(vcResult);

        CredentialRequest request = new CredentialRequest();
        request.setFormat("ldp_vc");
        request.setDoctype("ECACredential");
        request.setIssuerId("MGI");
        CredentialDefinition credDef = new CredentialDefinition();
        credDef.setContext(List.of("https://www.w3.org/2018/credentials/v1"));
        credDef.setType(List.of("VerifiableCredential", "ECACredential"));
        credDef.setCredentialSubject(new HashMap<>());
        request.setCredential_definition(credDef);
        CredentialProof proof = new CredentialProof();
        proof.setProof_type("jwt");
        proof.setJwt("test-jwt");
        request.setProof(proof);

        CredentialResponse<?> response = issuanceService.getCredential(request);
        assertNotNull(response);

        verify(vcSigner, times(1)).attachSignature(any(), any());
        String unsignedVC = unsignedVcCaptor.getValue();
        JSONObject vcJson = new JSONObject(unsignedVC);
        JSONObject cs = vcJson.getJSONObject("credentialSubject");

        assertFalse(cs.getBoolean("isOver18"));

        String output = buildFinalOutput(vcJson);
        writeJsonOutput("eca-under18-output.json", output);
        System.out.println(output);
    }
}
