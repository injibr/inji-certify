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
import io.mosip.certify.vcformatters.CcirVelocityTemplatingEngineImpl;
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

import io.mosip.certify.core.exception.CertifyException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CertifyIssuanceCCIRFlowTest {

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

    private CcirVelocityTemplatingEngineImpl realVelocityEngine;

    private static final String ISSUER_URI = "https://injicertify.credenciaisverificaveis-hml.dataprev.gov.br";
    private static final String TEST_ACCESS_TOKEN_HASH = "test-token-hash";

    private static final String CCIR_TEMPLATE = "{" +
            "\"@context\": [\"https://www.w3.org/2018/credentials/v1\"]," +
            "\"issuer\": \"${_issuer}\"," +
            "\"type\": [\"VerifiableCredential\",\"CCIRCredential\"]," +
            "\"issuanceDate\": \"${validFrom}\"," +
            "\"expirationDate\": \"${validUntil}\"," +
            "\"credentialSubject\": {" +
            "\"id\": \"${_holderId}\"," +
            "\"codigoImovelIncra\": \"${codigoImovelIncra}\"," +
            "\"denominacao\": \"${denominacao}\"," +
            "\"areaTotal\": \"${areaTotal}\"," +
            "\"classificacaoFundiaria\": \"${classificacaoFundiaria}\"," +
            "\"dataProcessamentoUltimaDeclaracao\": \"${dataProcessamentoUltimaDeclaracao}\"," +
            "\"areaCertificada\": \"${areaCertificada}\"," +
            "\"indicacoesLocalizacao\": \"${indicacoesLocalizacao}\"," +
            "\"municipioSede\": \"${municipioSede}\"," +
            "\"ufSede\": \"${ufSede}\"," +
            "\"areaModuloRural\": \"${areaModuloRural}\"," +
            "\"numeroModulosRurais\": \"${numeroModulosRurais}\"," +
            "\"areaModuloFiscal\": \"${areaModuloFiscal}\"," +
            "\"numeroModulosFiscais\": \"${numeroModulosFiscais}\"," +
            "\"fracaoMinimaParcelamento\": \"${fracaoMinimaParcelamento}\"," +
            "\"totalAreaRegistrada\": \"${totalAreaRegistrada}\"," +
            "\"totalAreaPosseJustoTitulo\": \"${totalAreaPosseJustoTitulo}\"," +
            "\"totalAreaPosseSimplesOcupacao\": \"${totalAreaPosseSimplesOcupacao}\"," +
            "\"areaMedida\": \"${areaMedida}\"," +
            "\"declarante\": \"${declarante}\"," +
            "\"cpfCnpj\": \"${cpfCnpj}\"," +
            "\"nacionalidade\": \"${nacionalidade}\"," +
            "\"totalPessoasRelacionadasImovel\": \"${totalPessoasRelacionadasImovel}\"," +
            "\"titulares\": \"${titulares}\"," +
            "\"dataLancamento\": \"${dataLancamento}\"," +
            "\"numeroCcir\": \"${numeroCcir}\"," +
            "\"dataGeracaoCcir\": \"${dataGeracaoCcir}\"," +
            "\"dataVencimentoCcir\": \"${dataVencimentoCcir}\"," +
            "\"debitosAnteriores\": \"${debitosAnteriores}\"," +
            "\"taxaServicosCadastrais\": \"${taxaServicosCadastrais}\"," +
            "\"valorCobrado\": \"${valorCobrado}\"," +
            "\"multa\": \"${multa}\"," +
            "\"juros\": \"${juros}\"," +
            "\"valorTotal\": \"${valorTotal}\"" +
            "}}";

    private static final String CCIR_NULL_JSON = """
            {
                "codigoImovel": 438112979,
                "nomeImovelRural": "Fazenda Sao Miguel",
                "localizacaoImovelRural": "Estrada Do Nativo",
                "situacaoImovel": "Cancelado",
                "motivoCancelamento": "Remembramento de Área Total",
                "dataCancelamento": "01/12/2021 19:39:11",
                "areaTotal": 94.9000,
                "qtdAraeModulosFiscais": 4.7400,
                "inibicao": null,
                "dataInibicao": null,
                "titulares": [
                    {
                        "cpfCnpj": "09693408772",
                        "nomeTitular": "Hernandez Favarato",
                        "declarante": 1,
                        "estadoCivilTitular": null,
                        "cpfConjuge": null,
                        "nomeConjuge": null
                    }
                ],
                "temporarios": [
                    {
                        "cpfCnpj": null,
                        "nomeTemporario": null,
                        "paisOrigem": null,
                        "estadoCivilTemporario": null,
                        "regimeBens": null,
                        "condicaoPessoa": null,
                        "atividadeExploracao": null,
                        "capitalNacional": null,
                        "capitalEstrangeiro": null,
                        "qtdAreaCedida": 0.0,
                        "tipoContrato": "Verbal",
                        "prazoContrato": "Indeterminado",
                        "dataTerminoContrato": null
                    }
                ],
                "localizacoes": [
                    {
                        "codigoIbge": 3204906,
                        "municipio": "SÃO MATEUS",
                        "siglaUf": "ES",
                        "areaMunicipio": 94.9000,
                        "indicadorSede": 1
                    }
                ],
                "situacoesJuridicas": [
                    {
                        "situacaoJuridica": "Área Registrada",
                        "formaObtencao": "Aquisição Do Governo Estadual",
                        "dataSituacaoJuridica": "24/01/2002",
                        "areaSituacaoJuridica": 94.9000,
                        "cartorioImovel": null,
                        "codigoCns": 0,
                        "matricula": 15473,
                        "numeroRegistroTranscricao": "R01",
                        "codigoParcelaSigef": null
                    }
                ],
                "ccir": null,
                "habilitadoEmitirCcir": false,
                "codigoCertificacaoSnci": null,
                "dataUltimaAtualizacaoCadastral": "22/07/2005"
            }
            """;

    private static final String CCIR_7_TITULARES_JSON = """
            {
                "codigoImovel": 9130730239223,
                "nomeImovelRural": "LOTE RURAL N 54 DA QUADRA N 46",
                "localizacaoImovelRural": "ESTRADA DA QUINTA LINHA",
                "situacaoImovel": "Ativo",
                "areaTotal": 30.4200,
                "titulares": [
                    {"cpfCnpj": "40486630110", "nomeTitular": "Guilherme Catelan Negreli Filho", "declarante": 1, "estadoCivilTitular": "Casado", "cpfConjuge": "46519726168", "nomeConjuge": "Edinez Alves dos Santos Negreli"},
                    {"cpfCnpj": "61365696120", "nomeTitular": "Marcelo Negrelli", "declarante": 0, "estadoCivilTitular": "Casado", "cpfConjuge": "84989742168", "nomeConjuge": "Rosa Ferreira Rodrigues"},
                    {"cpfCnpj": "00858563126", "nomeTitular": "Marli Negrelli Lemos", "declarante": 0, "estadoCivilTitular": "Casado", "cpfConjuge": "36661058153", "nomeConjuge": "Jose Aparecido Anselmo Ramos"},
                    {"cpfCnpj": "04820282115", "nomeTitular": "Antonio Negrelli", "declarante": 0, "estadoCivilTitular": "Divorciado", "cpfConjuge": null, "nomeConjuge": null},
                    {"cpfCnpj": "88100782172", "nomeTitular": "Marcilene Negrelli", "declarante": 0, "estadoCivilTitular": "Divorciado", "cpfConjuge": null, "nomeConjuge": null},
                    {"cpfCnpj": "46604766187", "nomeTitular": "Euclides Negrelli", "declarante": 0, "estadoCivilTitular": "Casado", "cpfConjuge": "51944111115", "nomeConjuge": "Adecilva Lima Negrelli"},
                    {"cpfCnpj": "38566796187", "nomeTitular": "Maria Negreli dos Santos", "declarante": 0, "estadoCivilTitular": "Casado", "cpfConjuge": "10728210100", "nomeConjuge": "Agnaldo dos Santos"}
                ],
                "temporarios": [{"cpfCnpj": null, "nomeTemporario": null, "paisOrigem": null, "estadoCivilTemporario": null, "regimeBens": null, "condicaoPessoa": null, "atividadeExploracao": null, "capitalNacional": null, "capitalEstrangeiro": null, "qtdAreaCedida": 0.0, "tipoContrato": "Verbal", "prazoContrato": "Indeterminado", "dataTerminoContrato": null}],
                "localizacoes": [{"codigoIbge": 5003801, "municipio": "F\u00c1TIMA DO SUL", "siglaUf": "MS", "areaMunicipio": 30.4200, "indicadorSede": 1}],
                "situacoesJuridicas": [{"situacaoJuridica": "\u00c1rea Registrada", "formaObtencao": "Adjudica\u00e7\u00e3o", "dataSituacaoJuridica": "05/05/2003", "areaSituacaoJuridica": 30.4200, "cartorioImovel": "Registro De Im\u00f3veis, Titulos E Documentos E Civil Das Pessoas Juridicas", "codigoCns": 62778, "matricula": 8837, "numeroRegistroTranscricao": "R-6", "codigoParcelaSigef": null}],
                "ccir": {
                    "codigoImovelIncra": "9130730239223",
                    "denominacao": "LOTE RURAL N 54 DA QUADRA N 46",
                    "areaTotal": 30.42,
                    "classificacaoFundiaria": "Pequena Propriedade Improdutiva",
                    "dataProcessamentoUltimaDeclaracao": "2021-10-05 16:13:36",
                    "areaCertificada": 0,
                    "indicacoesLocalizacao": "ESTRADA DA QUINTA LINHA",
                    "municipioSede": "F\u00c1TIMA DO SUL",
                    "ufSede": "MS",
                    "areaModuloRural": 16.0105,
                    "numeroModulosRurais": 1.9,
                    "areaModuloFiscal": 30,
                    "numeroModulosFiscais": 1.014,
                    "fracaoMinimaParcelamento": 2,
                    "totalAreaRegistrada": 30.42,
                    "totalAreaPosseJustoTitulo": 0,
                    "totalAreaPosseSimplesOcupacao": 0,
                    "areaMedida": 30.42,
                    "totalPessoasRelacionadasImovel": 7,
                    "titulares": [
                        {"cpfCnpj": "40486630110", "nomeTitular": "Guilherme Catelan Negreli Filho", "declarante": "S", "condicaoTitularidade": "Proprietario Ou Posseiro Comum", "percentualDetencao": 25, "nacionalidade": "BRASILEIRA"},
                        {"cpfCnpj": "61365696120", "nomeTitular": "Marcelo Negrelli", "declarante": "N", "condicaoTitularidade": "Proprietario Ou Posseiro Comum", "percentualDetencao": 6.25, "nacionalidade": "BRASILEIRA"},
                        {"cpfCnpj": "00858563126", "nomeTitular": "Marli Negrelli Lemos", "declarante": "N", "condicaoTitularidade": "Proprietario Ou Posseiro Comum", "percentualDetencao": 6.25, "nacionalidade": "BRASILEIRA"},
                        {"cpfCnpj": "04820282115", "nomeTitular": "Antonio Negrelli", "declarante": "N", "condicaoTitularidade": "Proprietario Ou Posseiro Comum", "percentualDetencao": 25, "nacionalidade": "BRASILEIRA"},
                        {"cpfCnpj": "88100782172", "nomeTitular": "Marcilene Negrelli", "declarante": "N", "condicaoTitularidade": "Proprietario Ou Posseiro Comum", "percentualDetencao": 6.25, "nacionalidade": "BRASILEIRA"},
                        {"cpfCnpj": "46604766187", "nomeTitular": "Euclides Negrelli", "declarante": "N", "condicaoTitularidade": "Proprietario Ou Posseiro Comum", "percentualDetencao": 6.25, "nacionalidade": "BRASILEIRA"},
                        {"cpfCnpj": "38566796187", "nomeTitular": "Maria Negreli dos Santos", "declarante": "N", "condicaoTitularidade": "Proprietario Ou Posseiro Comum", "percentualDetencao": 25, "nacionalidade": "BRASILEIRA"}
                    ],
                    "dadosUltimoCcir": {
                        "dataLancamento": "2025-06-16",
                        "numeroCcir": "73286678255",
                        "dataGeracaoCcir": "2025-09-10 16:42:39",
                        "dataVencimentoCcir": "2025-09-30",
                        "debitosAnteriores": 0,
                        "taxaServicosCadastrais": 11.29,
                        "valorCobrado": 11.29,
                        "multa": 2.26,
                        "juros": 0.23,
                        "valorTotal": 13.78
                    }
                },
                "habilitadoEmitirCcir": false,
                "dataUltimaAtualizacaoCadastral": "05/10/2021"
            }
            """;

    private static final String CCIR_1_TITULAR_JSON = """
            {
                "codigoImovel": 9500926025827,
                "nomeImovelRural": "Almecegas Ii",
                "localizacaoImovelRural": null,
                "situacaoImovel": "Ativo",
                "areaTotal": 15.7100,
                "titulares": [
                    {"cpfCnpj": "72516500700", "nomeTitular": "Phillip John Blackby", "declarante": 1, "estadoCivilTitular": "Casado", "cpfConjuge": null, "nomeConjuge": null}
                ],
                "temporarios": [{"cpfCnpj": null, "nomeTemporario": null, "paisOrigem": null, "estadoCivilTemporario": null, "regimeBens": null, "condicaoPessoa": null, "atividadeExploracao": null, "capitalNacional": null, "capitalEstrangeiro": null, "qtdAreaCedida": 0.0, "tipoContrato": "Verbal", "prazoContrato": "Indeterminado", "dataTerminoContrato": null}],
                "localizacoes": [{"codigoIbge": 2310258, "municipio": "PARAIPABA", "siglaUf": "CE", "areaMunicipio": 15.7100, "indicadorSede": 1}],
                "situacoesJuridicas": [{"situacaoJuridica": "\u00c1rea Posse Simples Ocupa\u00e7\u00e3o", "formaObtencao": "Compra E Venda De Particular", "dataSituacaoJuridica": "17/06/1993", "areaSituacaoJuridica": 15.7100, "cartorioImovel": null, "codigoCns": 0, "matricula": 0, "numeroRegistroTranscricao": null, "codigoParcelaSigef": null}],
                "ccir": {
                    "codigoImovelIncra": "9500926025827",
                    "denominacao": "Almecegas Ii",
                    "areaTotal": 15.71,
                    "classificacaoFundiaria": "Pequena Propriedade",
                    "dataProcessamentoUltimaDeclaracao": "2008-09-18 13:53:32",
                    "areaCertificada": 0,
                    "indicacoesLocalizacao": null,
                    "municipioSede": "PARAIPABA",
                    "ufSede": "CE",
                    "areaModuloRural": 0,
                    "numeroModulosRurais": 0,
                    "areaModuloFiscal": 45,
                    "numeroModulosFiscais": 0.3491,
                    "fracaoMinimaParcelamento": 2,
                    "totalAreaRegistrada": 0,
                    "totalAreaPosseJustoTitulo": 0,
                    "totalAreaPosseSimplesOcupacao": 15.71,
                    "areaMedida": 0,
                    "totalPessoasRelacionadasImovel": 1,
                    "titulares": [
                        {"cpfCnpj": "72516500700", "nomeTitular": "Phillip John Blackby", "declarante": "S", "condicaoTitularidade": "Proprietario Ou Posseiro Individual", "percentualDetencao": 100, "nacionalidade": "BRASILEIRA"}
                    ],
                    "dadosUltimoCcir": {
                        "dataLancamento": "2022-07-18",
                        "numeroCcir": "49494229228",
                        "dataGeracaoCcir": "2022-08-24 11:50:42",
                        "dataVencimentoCcir": "2022-09-30",
                        "debitosAnteriores": 0,
                        "taxaServicosCadastrais": 4.86,
                        "valorCobrado": 4.86,
                        "multa": 0.49,
                        "juros": 0.05,
                        "valorTotal": 5.4
                    }
                },
                "habilitadoEmitirCcir": false,
                "dataUltimaAtualizacaoCadastral": "18/09/2008"
            }
            """;

    private static final String CCIR_DATA_JSON = """
            {
                "codigoImovel": 1460130095040,
                "ccir": {
                    "codigoImovelIncra": "1460130095040",
                    "denominacao": "Sitio Boa Sorte",
                    "areaTotal": 267.7,
                    "classificacaoFundiaria": "Media Propriedade Produtiva",
                    "dataProcessamentoUltimaDeclaracao": "2002-11-08 08:25:57",
                    "areaCertificada": 0,
                    "indicacoesLocalizacao": "A 26 Km Da Sede",
                    "municipioSede": "ALTO SANTO",
                    "ufSede": "CE",
                    "areaModuloRural": 10.7509,
                    "numeroModulosRurais": 23.97,
                    "areaModuloFiscal": 55,
                    "numeroModulosFiscais": 4.86,
                    "fracaoMinimaParcelamento": 4,
                    "totalAreaRegistrada": 267.7,
                    "totalAreaPosseJustoTitulo": 0,
                    "totalAreaPosseSimplesOcupacao": 0,
                    "areaMedida": 0,
                    "totalPessoasRelacionadasImovel": 2,
                    "titulares": [
                        {"cpfCnpj": "02441365304", "nomeTitular": "Antonio Dantas De Almeida", "declarante": "S", "condicaoTitularidade": "Proprietario Ou Posseiro Comum", "percentualDetencao": 50, "nacionalidade": "ESTRANGEIRA"},
                        {"cpfCnpj": "02404842315", "nomeTitular": "Jose Alves De Almeida", "declarante": "N", "condicaoTitularidade": "Proprietario Ou Posseiro Comum", "percentualDetencao": 50, "nacionalidade": "BRASILEIRA"}
                    ],
                    "dadosUltimoCcir": {
                        "dataLancamento": "2024-06-17",
                        "numeroCcir": "67267486244",
                        "dataGeracaoCcir": "2024-10-17 13:18:03",
                        "dataVencimentoCcir": "2024-10-30",
                        "debitosAnteriores": 588.86,
                        "taxaServicosCadastrais": 32.35,
                        "valorCobrado": 621.21,
                        "multa": 6.47,
                        "juros": 0.97,
                        "valorTotal": 628.65
                    }
                }
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
        JSONObject vcMetadata = new JSONObject().put("issuer", "INCRA");
        JSONObject inner = new JSONObject()
                .put("vcMetadata", vcMetadata)
                .put("verifiableCredential", new JSONObject().put("credential", vcJson));
        JSONObject finalOutput = new JSONObject();
        finalOutput.put("verifiableCredential", new org.json.JSONArray().put(inner.toString()));
        return finalOutput.toString();
    }

    @SneakyThrows
    @Before
    public void setUp() {
        realVelocityEngine = new CcirVelocityTemplatingEngineImpl();
        ReflectionTestUtils.setField(realVelocityEngine, "credentialTemplateRepository", credentialTemplateRepository);
        ReflectionTestUtils.setField(realVelocityEngine, "renderingTemplateService", renderingTemplateService);
        ReflectionTestUtils.setField(realVelocityEngine, "defaultExpiryDuration", "P730D");
        ReflectionTestUtils.setField(realVelocityEngine, "idPrefix", "");
        realVelocityEngine.initialize();

        CredentialTemplate ccirTemplate = new CredentialTemplate();
        ccirTemplate.setTemplate(CCIR_TEMPLATE);
        ccirTemplate.setCredentialType("CCIR,VerifiableCredential");
        ccirTemplate.setContext("https://www.w3.org/2018/credentials/v1");
        when(credentialTemplateRepository.findByCredentialTypeAndContext(
                "CCIR,VerifiableCredential", "https://www.w3.org/2018/credentials/v1"))
                .thenReturn(Optional.of(ccirTemplate));

        LinkedHashMap<String, LinkedHashMap<String, Object>> issuerMetadata = new LinkedHashMap<>();
        LinkedHashMap<String, Object> incraMetadata = new LinkedHashMap<>();
        LinkedHashMap<String, Object> credConfigs = new LinkedHashMap<>();
        LinkedHashMap<String, Object> ccirConfig = new LinkedHashMap<>();
        ccirConfig.put("format", "ldp_vc");
        ccirConfig.put("scope", "openid");
        LinkedHashMap<String, Object> credDef = new LinkedHashMap<>();
        credDef.put("type", Arrays.asList("VerifiableCredential", "CCIR"));
        ccirConfig.put("credential_definition", credDef);
        ccirConfig.put("proof_types_supported", Map.of("jwt", Map.of("proof_signing_alg_values_supported", List.of("RS256"))));
        credConfigs.put("CCIRDocument", ccirConfig);
        incraMetadata.put("credential_configurations_supported", credConfigs);
        incraMetadata.put("credential_issuer", ISSUER_URI);
        incraMetadata.put("credential_endpoint", ISSUER_URI + "/v1/certify/issuance/credential");
        issuerMetadata.put("INCRA", incraMetadata);

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

        when(velocityTemplatingEngineFactory.getVelocityInstance("velocityEngineCcir")).thenReturn(realVelocityEngine);
    }

    @SneakyThrows
    @Test
    public void getCredential_CCIRDocument_singleVcWithDeclaranteAndTitulares() {
        when(dataProviderPlugin.fetchData(any())).thenReturn(new JSONObject(CCIR_DATA_JSON));

        ArgumentCaptor<String> unsignedVcCaptor = ArgumentCaptor.forClass(String.class);
        VCResult<JsonLDObject> vcResult = new VCResult<>();
        vcResult.setCredential(new JsonLDObject());
        when(vcSigner.attachSignature(unsignedVcCaptor.capture(), any(Map.class))).thenReturn(vcResult);

        CredentialRequest request = new CredentialRequest();
        request.setFormat("ldp_vc");
        request.setDoctype("CCIRDocument");
        request.setIssuerId("INCRA");
        CredentialDefinition credDef = new CredentialDefinition();
        credDef.setContext(List.of("https://www.w3.org/2018/credentials/v1"));
        credDef.setType(List.of("VerifiableCredential", "CCIR"));
        credDef.setCredentialSubject(new HashMap<>());
        request.setCredential_definition(credDef);
        CredentialProof proof = new CredentialProof();
        proof.setProof_type("jwt");
        proof.setJwt("test-jwt");
        request.setProof(proof);

        CredentialResponse<?> response = issuanceService.getCredential(request);
        assertNotNull(response);

        // Single VC
        verify(vcSigner, times(1)).attachSignature(any(), any());
        String unsignedVC = unsignedVcCaptor.getValue();
        JSONObject vcJson = new JSONObject(unsignedVC);
        JSONObject cs = vcJson.getJSONObject("credentialSubject");

        // Declarante fields from titular with declarante="S"
        assertEquals("Antonio Dantas De Almeida", cs.getString("declarante"));
        assertEquals("02441365304", cs.getString("cpfCnpj"));
        assertEquals("ESTRANGEIRA", cs.getString("nacionalidade"));

        // Titulares as string array
        String titulares = cs.getString("titulares");
        assertTrue(titulares.contains("Antonio Dantas De Almeida"));
        assertTrue(titulares.contains("Jose Alves De Almeida"));
        assertTrue(titulares.contains("02441365304"));
        assertTrue(titulares.contains("02404842315"));

        // Base fields
        assertEquals("1460130095040", cs.getString("codigoImovelIncra"));
        assertEquals("Sitio Boa Sorte", cs.getString("denominacao"));
        assertEquals("ALTO SANTO", cs.getString("municipioSede"));
        assertEquals("67267486244", cs.getString("numeroCcir"));
        assertEquals("628.65", cs.getString("valorTotal"));

        // Build final output in verifiableCredential:[""] format
        String output = buildFinalOutput(vcJson);
        writeJsonOutput("ccir-2titulares-output.json", output);
        System.out.println(output);
    }

    @SneakyThrows
    @Test
    public void getCredential_CCIRDocument_ccirNull_shouldFail() {
        // When ccir is null, CCIRDataProvider throws ResponseStatusException
        // which is wrapped as DataProviderExchangeException by DataProviderPluginImpl
        when(dataProviderPlugin.fetchData(any()))
                .thenThrow(new io.mosip.certify.api.exception.DataProviderExchangeException("CCIR data is null. Property may be cancelled."));

        CredentialRequest request = new CredentialRequest();
        request.setFormat("ldp_vc");
        request.setDoctype("CCIRDocument");
        request.setIssuerId("INCRA");
        CredentialDefinition credDef = new CredentialDefinition();
        credDef.setContext(List.of("https://www.w3.org/2018/credentials/v1"));
        credDef.setType(List.of("VerifiableCredential", "CCIR"));
        credDef.setCredentialSubject(new HashMap<>());
        request.setCredential_definition(credDef);
        CredentialProof proof = new CredentialProof();
        proof.setProof_type("jwt");
        proof.setJwt("test-jwt");
        request.setProof(proof);

        assertThrows(CertifyException.class, () -> issuanceService.getCredential(request));
        verify(vcSigner, never()).attachSignature(any(), any());
    }

    @SneakyThrows
    @Test
    public void velocityEngine_ccirNull_fieldsUnresolved() {
        // Tests directly that the Velocity engine produces unresolved placeholders
        // when ccir is null in the input JSON
        JSONObject inputJson = new JSONObject(CCIR_NULL_JSON);

        Map<String, Object> templateSettings = Map.of(
                "templateName", "CCIR,VerifiableCredential:https://www.w3.org/2018/credentials/v1",
                "issuerURI", ISSUER_URI
        );

        String result = realVelocityEngine.format(inputJson, templateSettings);
        JSONObject vcJson = new JSONObject(result);
        JSONObject cs = vcJson.getJSONObject("credentialSubject");

        System.out.println("====== CCIR NULL - VELOCITY OUTPUT ======");
        System.out.println(cs.toString(2));

        // ccir fields should be unresolved
        assertTrue(cs.getString("codigoImovelIncra").contains("${"));
        assertTrue(cs.getString("numeroCcir").contains("${"));
        assertTrue(cs.getString("denominacao").contains("${"));
        assertTrue(cs.getString("municipioSede").contains("${"));
        assertTrue(cs.getString("valorTotal").contains("${"));

        // root-level titulares array is processed but without ccir-specific fields
        String titulares = cs.getString("titulares");
        assertTrue(titulares.contains("Hernandez Favarato"));
        assertTrue(titulares.contains("09693408772"));
    }

    @SneakyThrows
    @Test
    public void getCredential_CCIRDocument_1titular() {
        when(dataProviderPlugin.fetchData(any())).thenReturn(new JSONObject(CCIR_1_TITULAR_JSON));

        ArgumentCaptor<String> unsignedVcCaptor = ArgumentCaptor.forClass(String.class);
        VCResult<JsonLDObject> vcResult = new VCResult<>();
        vcResult.setCredential(new JsonLDObject());
        when(vcSigner.attachSignature(unsignedVcCaptor.capture(), any(Map.class))).thenReturn(vcResult);

        CredentialRequest request = new CredentialRequest();
        request.setFormat("ldp_vc");
        request.setDoctype("CCIRDocument");
        request.setIssuerId("INCRA");
        CredentialDefinition credDef = new CredentialDefinition();
        credDef.setContext(List.of("https://www.w3.org/2018/credentials/v1"));
        credDef.setType(List.of("VerifiableCredential", "CCIR"));
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

        // Single titular who is also the declarante
        assertEquals("Phillip John Blackby", cs.getString("declarante"));
        assertEquals("72516500700", cs.getString("cpfCnpj"));
        assertEquals("BRASILEIRA", cs.getString("nacionalidade"));

        // Titulares array with single element
        String titulares = cs.getString("titulares");
        assertTrue(titulares.contains("Phillip John Blackby"));
        assertTrue(titulares.contains("72516500700"));

        // Base fields
        assertEquals("9500926025827", cs.getString("codigoImovelIncra"));
        assertEquals("Almecegas Ii", cs.getString("denominacao"));
        assertEquals("Pequena Propriedade", cs.getString("classificacaoFundiaria"));
        assertEquals("PARAIPABA", cs.getString("municipioSede"));
        assertEquals("CE", cs.getString("ufSede"));
        assertEquals("49494229228", cs.getString("numeroCcir"));
        assertEquals("5.4", cs.getString("valorTotal"));
        assertEquals("15.71", cs.getString("totalAreaPosseSimplesOcupacao"));
        assertEquals("0", cs.getString("totalAreaRegistrada"));
        assertEquals("1", cs.getString("totalPessoasRelacionadasImovel"));

        String output = buildFinalOutput(vcJson);
        writeJsonOutput("ccir-1titular-output.json", output);
        System.out.println(output);
    }

    @SneakyThrows
    @Test
    public void getCredential_CCIRDocument_7titulares() {
        when(dataProviderPlugin.fetchData(any())).thenReturn(new JSONObject(CCIR_7_TITULARES_JSON));

        ArgumentCaptor<String> unsignedVcCaptor = ArgumentCaptor.forClass(String.class);
        VCResult<JsonLDObject> vcResult = new VCResult<>();
        vcResult.setCredential(new JsonLDObject());
        when(vcSigner.attachSignature(unsignedVcCaptor.capture(), any(Map.class))).thenReturn(vcResult);

        CredentialRequest request = new CredentialRequest();
        request.setFormat("ldp_vc");
        request.setDoctype("CCIRDocument");
        request.setIssuerId("INCRA");
        CredentialDefinition credDef = new CredentialDefinition();
        credDef.setContext(List.of("https://www.w3.org/2018/credentials/v1"));
        credDef.setType(List.of("VerifiableCredential", "CCIR"));
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

        // Declarante fields from titular with declarante="S" (Guilherme)
        assertEquals("Guilherme Catelan Negreli Filho", cs.getString("declarante"));
        assertEquals("40486630110", cs.getString("cpfCnpj"));
        assertEquals("BRASILEIRA", cs.getString("nacionalidade"));

        // Titulares array should contain all 7
        String titulares = cs.getString("titulares");
        assertTrue(titulares.contains("Guilherme Catelan Negreli Filho"));
        assertTrue(titulares.contains("Marcelo Negrelli"));
        assertTrue(titulares.contains("Marli Negrelli Lemos"));
        assertTrue(titulares.contains("Antonio Negrelli"));
        assertTrue(titulares.contains("Marcilene Negrelli"));
        assertTrue(titulares.contains("Euclides Negrelli"));
        assertTrue(titulares.contains("Maria Negreli dos Santos"));

        // Base fields
        assertEquals("9130730239223", cs.getString("codigoImovelIncra"));
        assertEquals("LOTE RURAL N 54 DA QUADRA N 46", cs.getString("denominacao"));
        assertEquals("MS", cs.getString("ufSede"));
        assertEquals("73286678255", cs.getString("numeroCcir"));
        assertEquals("13.78", cs.getString("valorTotal"));
        assertEquals("7", cs.getString("totalPessoasRelacionadasImovel"));

        // Build final output
        String output = buildFinalOutput(vcJson);
        writeJsonOutput("ccir-7titulares-output.json", output);
        System.out.println(output);
    }
}
