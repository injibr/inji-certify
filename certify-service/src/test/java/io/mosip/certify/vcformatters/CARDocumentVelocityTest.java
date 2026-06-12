package io.mosip.certify.vcformatters;

import io.mosip.certify.core.constants.Constants;
import io.mosip.certify.core.constants.VCDM2Constants;
import io.mosip.certify.entity.CredentialConfig;
import io.mosip.certify.repository.CredentialConfigRepository;
import io.mosip.certify.core.spi.RenderingTemplateService;
import io.mosip.certify.utils.CredentialUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static io.mosip.certify.core.constants.Constants.DELIMITER;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * Teste que reproduz o erro:
 * "Invalid JSON data encountered during credential generation. Please check the data provider response and template configurations."
 *
 * Causa raiz: quando o JSON da API SICAR retorna sobreposicoesUnidadeConservacao como array [...],
 * o fluxo jsonObject.toMap() → toJsonMap() converte para JSONArray, e o Velocity substitui
 * "${sobreposicoesUnidadeConservacao}" com o toString() do JSONArray gerando JSON inválido
 * (aspas internas não escapadas dentro de aspas da string).
 *
 * A solução é serializar arrays como strings JSON escapadas no CARDataProvider,
 * seguindo o mesmo padrão do CAFDataProvider para "membros" e "areas".
 */
@RunWith(MockitoJUnitRunner.class)
public class CARDocumentVelocityTest {

    @InjectMocks
    private VelocityTemplatingEngineImpl formatter;
    @Mock
    CredentialConfigRepository credentialConfigRepository;
    @Mock
    RenderingTemplateService renderingTemplateService;

    private static final String CAR_TEMPLATE = """
            {"@context": ["https://www.w3.org/2018/credentials/v1"],"issuer": "${_issuer}","type": ["VerifiableCredential","CARDocument"],"issuanceDate": "${validFrom}","expirationDate": "${validUntil}","credentialSubject": {"id": "${_holderId}","situacaoImovel": "${situacaoImovel}","codigoImovel": "${codigoImovel}","descricaoEtapaCadastro": "${descricaoEtapaCadastro}","areaTotalImovel": "${areaTotalImovel}","quantidadeModulosFiscais": "${quantidadeModulosFiscais}","dataCadastro": "${dataCadastro}","dataUltimaAtualizacaoCadastro": "${dataUltimaAtualizacaoCadastro}","municipio": "${municipio}","unidadeFederativa": "${unidadeFederativa}","coordenadaImovelX": "${coordenadaImovelX}","coordenadaImovelY": "${coordenadaImovelY}","areaRemanescenteVegetacaoNativa": "${areaRemanescenteVegetacaoNativa}","areaConsolidada": "${areaConsolidada}","areaServidaoAdministrativa": "${areaServidaoAdministrativa}","situacaoReservaLegal": "${situacaoReservaLegal}","areaReservaLegalAverbadaDocumental": "${areaReservaLegalAverbadaDocumental}","areaReservaLegalAverbada": "${areaReservaLegalAverbada}","areaReservaLegalAprovadaNaoAverbada": "${areaReservaLegalAprovadaNaoAverbada}","areaReservaLegalProposta": "${areaReservaLegalProposta}","areaReservaLegalDeclaradaProprietarioPossuidor": "${areaReservaLegalDeclaradaProprietarioPossuidor}","areaPreservacaoPermanente": "${areaPreservacaoPermanente}","areaPreservacaoPermanenteAreaRuralConsolida": "${areaPreservacaoPermanenteAreaRuralConsolida}","areaPreservacaoPermanenteAreaRemanescenteVegetacaoNativa": "${areaPreservacaoPermanenteAreaRemanescenteVegetacaoNativa}","areaUsoRestrito": "${areaUsoRestrito}","areaUsoRestritoDeclividade": "${areaUsoRestritoDeclividade}","areaReservaLegalExcedentePassivo": "${areaReservaLegalExcedentePassivo}","areaReservaLegalRecompor": "${areaReservaLegalRecompor}","areaPreservacaoPermanenteRecompor": "${areaPreservacaoPermanenteRecompor}","areaUsoRestritoRecompor": "${areaUsoRestritoRecompor}","sobreposicoesTerraIndigena": "${sobreposicoesTerraIndigena}","sobreposicoesUnidadeConservacao": "${sobreposicoesUnidadeConservacao}","sobreposicoesAreasEmbargadas": "${sobreposicoesAreasEmbargadas}"}}
            """;

    // JSON simulando resposta da API SICAR para Canutama/AM com sobreposicoesUnidadeConservacao como ARRAY
    private static final String MOCK_API_RESPONSE_WITH_ARRAY = """
            {
              "situacaoImovel": "AT",
              "codigoImovel": "AM-1300904-0B225E133A9848CDAC3C9A1A5EA27673",
              "descricaoEtapaCadastro": "Analisado com pendências",
              "areaTotalImovel": 236.9349,
              "quantidadeModulosFiscais": "2.3693",
              "dataCadastro": "30/01/2015",
              "dataUltimaAtualizacaoCadastro": "30/01/2015",
              "codigoMunicipio": 1300904,
              "municipio": "Canutama",
              "unidadeFederativa": "AM",
              "coordenadaImovelX": -64.1544635408198,
              "coordenadaImovelY": -8.92677596916461,
              "areaRemanescenteVegetacaoNativa": 182.176816945402,
              "areaConsolidada": 52.8641163715117,
              "areaServidaoAdministrativa": 0.0,
              "situacaoReservaLegal": "Não analisada",
              "areaReservaLegalAverbadaDocumental": null,
              "areaReservaLegalAverbada": "0.0000",
              "areaReservaLegalAprovadaNaoAverbada": "0.0000",
              "areaReservaLegalProposta": "182.1768",
              "areaReservaLegalDeclaradaProprietarioPossuidor": 0.0,
              "areaPreservacaoPermanente": 11.2864880606595,
              "areaPreservacaoPermanenteAreaRuralConsolidada": "0.0000",
              "areaPreservacaoPermanenteAreaRemanescenteVegetacaoNativa": "9.9070",
              "areaUsoRestrito": 0.0,
              "areaUsoRestritoDeclividade": 0.0,
              "areaReservaLegalExcedentePassivo": "-7.3712",
              "areaReservaLegalRecompor": 0.0,
              "areaPreservacaoPermanenteRecompor": "0.0000",
              "areaUsoRestritoRecompor": 0.0,
              "sobreposicoesTerraIndigena": null,
              "sobreposicoesUnidadeConservacao": [
                {
                  "tema": "Unidade de Conservação",
                  "descricao": "Floresta - FLORESTA ESTADUAL DE RENDIMENTO SUSTENTADO RIO VERMELHO (C)",
                  "processamento": "2025-03-12T21:56:31.994",
                  "areaSobreposicao": 25.3195,
                  "percentualSobreposicao": 10.6863
                }
              ],
              "sobreposicoesAreasEmbargadas": null,
              "poligonoAreaImovel": "MULTIPOLYGON(((-64.16 -8.92,-64.15 -8.93,-64.14 -8.92,-64.16 -8.92)))"
            }
            """;

    private String templateKey;

    @Before
    public void setUp() {
        String type = "CARDocument,VerifiableCredential";
        String context = "https://www.w3.org/2018/credentials/v1";
        String format = "ldp_vc";
        templateKey = type + DELIMITER + context + DELIMITER + format;

        CredentialConfig config = new CredentialConfig();
        config.setVcTemplate(Base64.getEncoder().encodeToString(CAR_TEMPLATE.getBytes()));
        config.setCredentialType(type);
        config.setContext(context);
        config.setCredentialFormat(format);
        config.setDidUrl("did:web:injibr.github.io:inji-did:dev:mgi");
        config.setKeyManagerAppId("CERTIFY_VC_SIGN_ED25519");
        config.setKeyManagerRefId("ED25519_SIGN");
        config.setSignatureAlgo("EdDSA");
        config.setSignatureCryptoSuite("Ed25519Signature2020");

        when(credentialConfigRepository.findByCredentialFormatAndCredentialTypeAndContext(format, type, context))
                .thenReturn(Optional.of(config));

        ReflectionTestUtils.setField(formatter, "defaultExpiryDuration", "P730d");
        ReflectionTestUtils.setField(formatter, "idPrefix", "");

        formatter.initialize();
    }

    /**
     * REPRODUZ O ERRO: quando sobreposicoesUnidadeConservacao é um array,
     * o template Velocity gera JSON inválido → JSONException.
     *
     * Este teste DEVE FALHAR até que o CARDataProvider serialize arrays
     * como strings JSON escapadas (igual ao CAFDataProvider faz com "membros"/"areas").
     */
    @Test
    public void format_withArrayFieldFromApi_shouldThrowJSONException() {
        // Simula o fluxo: DataProvider retorna JSONObject → toMap() → putAll → toJsonMap → Velocity
        JSONObject apiResponse = new JSONObject(MOCK_API_RESPONSE_WITH_ARRAY);

        // Monta templateParams como CertifyIssuanceServiceImpl faz
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put(Constants.TEMPLATE_NAME, templateKey);
        templateParams.put(Constants.DID_URL, "did:web:injibr.github.io:inji-did:dev:mgi");
        templateParams.put(VCDM2Constants.VALID_FROM, "2026-06-11T14:50:28.257Z");
        templateParams.put(VCDM2Constants.VALID_UNTIL, "2028-06-10T14:50:28.257Z");

        // Simula: jsonObject.put("_holderId", holderId) + templateParams.putAll(jsonObject.toMap())
        apiResponse.put("_holderId", "did:jwk:test123");
        templateParams.putAll(apiResponse.toMap());

        // Simula: toJsonMap(templateParams)
        Map<String, Object> updatedTemplateParams = CredentialUtils.toJsonMap(templateParams);

        // O erro acontece aqui: Velocity renderiza array como string sem escape → JSON inválido
        assertThrows(
                "Deveria lançar JSONException porque sobreposicoesUnidadeConservacao é um array " +
                        "e o template espera uma string. O CARDataProvider precisa serializar arrays " +
                        "como strings escapadas (mesmo padrão do CAFDataProvider).",
                JSONException.class,
                () -> formatter.format(updatedTemplateParams)
        );
    }

    /**
     * Demonstra que o fix correto funciona: serializar arrays como strings JSON escapadas
     * ANTES de colocar no templateParams (no DataProvider), igual ao CAFDataProvider.
     */
    @Test
    public void format_withArraySerializedAsEscapedString_shouldProduceValidJson() {
        JSONObject apiResponse = new JSONObject(MOCK_API_RESPONSE_WITH_ARRAY);

        // FIX: serializar campos de sobreposição que são arrays como strings escapadas
        // (mesmo padrão do CAFDataProvider.flattenCaf() para membros/areas)
        for (String arrayKey : new String[]{"sobreposicoesTerraIndigena", "sobreposicoesUnidadeConservacao", "sobreposicoesAreasEmbargadas"}) {
            if (!apiResponse.isNull(arrayKey) && apiResponse.get(arrayKey) instanceof org.json.JSONArray) {
                String serialized = apiResponse.getJSONArray(arrayKey).toString();
                String quoted = JSONObject.quote(serialized);
                // Remove aspas externas adicionadas por quote() pois o template já coloca aspas
                apiResponse.put(arrayKey, quoted.substring(1, quoted.length() - 1));
            }
        }

        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put(Constants.TEMPLATE_NAME, templateKey);
        templateParams.put(Constants.DID_URL, "did:web:injibr.github.io:inji-did:dev:mgi");
        templateParams.put(VCDM2Constants.VALID_FROM, "2026-06-11T14:50:28.257Z");
        templateParams.put(VCDM2Constants.VALID_UNTIL, "2028-06-10T14:50:28.257Z");

        apiResponse.put("_holderId", "did:jwk:test123");
        templateParams.putAll(apiResponse.toMap());

        Map<String, Object> updatedTemplateParams = CredentialUtils.toJsonMap(templateParams);

        // Agora o format() NÃO deve lançar exceção
        String result = formatter.format(updatedTemplateParams);

        assertNotNull(result);
        JSONObject vc = new JSONObject(result);
        JSONObject credentialSubject = vc.getJSONObject("credentialSubject");

        // sobreposicoesUnidadeConservacao deve ser uma string JSON escapada válida
        String sobreposicoes = credentialSubject.getString("sobreposicoesUnidadeConservacao");
        assertNotNull(sobreposicoes);
        // Validar que é um JSON array válido quando desescapado
        org.json.JSONArray parsed = new org.json.JSONArray(sobreposicoes);
        assertEquals(1, parsed.length());
        assertEquals("Unidade de Conservação", parsed.getJSONObject(0).getString("tema"));

        System.out.println("====== VC GERADA COM FIX (sobreposições serializada) ======");
        System.out.println(vc.toString(2));
    }
}
