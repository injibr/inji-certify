package io.mosip.certify.vcformatters;

import io.mosip.certify.core.constants.Constants;
import io.mosip.certify.core.constants.VCDM2Constants;
import io.mosip.certify.entity.CredentialConfig;
import io.mosip.certify.repository.CredentialConfigRepository;
import io.mosip.certify.core.spi.RenderingTemplateService;
import io.mosip.certify.utils.CredentialUtils;
import org.json.JSONArray;
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
 * Teste que reproduz o erro do CARReceipt quando "proprietarios" vem como array da API.
 * Mesmo padrão do CARDocumentVelocityTest para sobreposições.
 */
@RunWith(MockitoJUnitRunner.class)
public class CARReceiptVelocityTest {

    @InjectMocks
    private VelocityTemplatingEngineImpl formatter;
    @Mock
    CredentialConfigRepository credentialConfigRepository;
    @Mock
    RenderingTemplateService renderingTemplateService;

    private static final String CAR_RECEIPT_TEMPLATE = "eyJAY29udGV4dCI6IFsiaHR0cHM6Ly93d3cudzMub3JnL25zL2NyZWRlbnRpYWxzL3YyIl0sICJpc3N1ZXIiOiAiJHtfaXNzdWVyfSIsICJ0eXBlIjogWyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsICJDQVJSZWNlaXB0Il0sICJ2YWxpZEZyb20iOiAiJHt2YWxpZEZyb219IiwgInZhbGlkVW50aWwiOiAiJHt2YWxpZFVudGlsfSIsICJjcmVkZW50aWFsU3ViamVjdCI6IHsiaWRlbnRpZmljYWRvckltb3ZlbCI6ICIke2lkZW50aWZpY2Fkb3JJbW92ZWx9IiwgImNvZGlnb0ltb3ZlbCI6ICIke2NvZGlnb0ltb3ZlbH0iLCAic2l0dWFjYW9JbW92ZWwiOiAiJHtzaXR1YWNhb0ltb3ZlbH0iLCAidGlwb0ltb3ZlbCI6ICIke3RpcG9JbW92ZWx9IiwgImRhdGFDYWRhc3RybyI6ICIke2RhdGFDYWRhc3Ryb30iLCAibm9tZUltb3ZlbCI6ICIke25vbWVJbW92ZWx9IiwgImNvZGlnb011bmljaXBpbyI6ICIke2NvZGlnb011bmljaXBpb30iLCAibXVuaWNpcGlvIjogIiR7bXVuaWNpcGlvfSIsICJ1bmlkYWRlRmVkZXJhdGl2YSI6ICIke3VuaWRhZGVGZWRlcmF0aXZhfSIsICJjb29yZGVuYWRhSW1vdmVsWCI6ICIke2Nvb3JkZW5hZGFJbW92ZWxYfSIsICJjb29yZGVuYWRhSW1vdmVsWSI6ICIke2Nvb3JkZW5hZGFJbW92ZWxZfSIsICJhcmVhVG90YWxJbW92ZWwiOiAiJHthcmVhVG90YWxJbW92ZWx9IiwgIm1vZHVsb0Zpc2NhbCI6ICIke21vZHVsb0Zpc2NhbH0iLCAicHJvdG9jb2xvIjogIiR7cHJvdG9jb2xvfSIsICJpbmZvcm1hY29lc0FkaWNpb25haXMiOiAiJHtpbmZvcm1hY29lc0FkaWNpb25haXN9IiwgImdlb0ltb3ZlbCI6ICIke2dlb0ltb3ZlbH0iLCAicHJvcHJpZXRhcmlvcyI6ICIke3Byb3ByaWV0YXJpb3N9IiwgImFyZWFTZXJ2aWRhb0FkbWluaXN0cmF0aXZhIjogIiR7YXJlYVNlcnZpZGFvQWRtaW5pc3RyYXRpdmF9IiwgImFyZWFMaXF1aWRhSW1vdmVsIjogIiR7YXJlYUxpcXVpZGFJbW92ZWx9IiwgImFyZWFQcmVzZXJ2YWNhb1Blcm1hbmVudGUiOiAiJHthcmVhUHJlc2VydmFjYW9QZXJtYW5lbnRlfSIsICJhcmVhVXNvUmVzdHJpdG8iOiAiJHthcmVhVXNvUmVzdHJpdG99IiwgImFyZWFDb25zb2xpZGFkYSI6ICIke2FyZWFDb25zb2xpZGFkYX0iLCAiYXJlYVJlbWFuZXNjZW50ZVZlZ2V0YWNhb05hdGl2YSI6ICIke2FyZWFSZW1hbmVzY2VudGVWZWdldGFjYW9OYXRpdmF9IiwgImFyZWFSZXNlcnZhTGVnYWwiOiAiJHthcmVhUmVzZXJ2YUxlZ2FsfSIsICJtYXRyaWN1bGEiOiAiJHttYXRyaWN1bGF9IiwgImRhdGFNYXRyaWN1bGEiOiAiJHtkYXRhTWF0cmljdWxhfSIsICJsaXZyb01hdHJpY3VsYSI6ICIke2xpdnJvTWF0cmljdWxhfSIsICJmb2xoYU1hdHJpY3VsYSI6ICIke2ZvbGhhTWF0cmljdWxhfSIsICJtdW5pY2lwaW9DYXJ0b3JpbyI6ICIke211bmljaXBpb0NhcnRvcmlvfSIsICJ1ZkNhcnRvcmlvIjogIiR7dWZDYXJ0b3Jpb30ifX0=";

    private static final String MOCK_API_RESPONSE = """
            {
              "identificadorImovel": 11948211,
              "codigoImovel": "MA-2112233-044B8D8BC23345BE88179D0385A8BB1A",
              "situacaoImovel": "AT",
              "tipoImovel": "IRU",
              "dataCadastro": "17/08/2023",
              "nomeImovel": "CHÁCARA BOA ESPERANÇA",
              "codigoMunicipio": 2112233,
              "municipio": "Trizidela do Vale",
              "unidadeFederativa": "MA",
              "coordenadaImovelX": -44.6060289201918,
              "coordenadaImovelY": -4.48399759305822,
              "areaTotalImovel": "1.0045",
              "moduloFiscal": "0.0167",
              "protocolo": "MA-2112233-51BFEFBDAE1A1525CC1D100BA5DD929F",
              "informacoesAdicionais": "Foi detectada uma diferença entre a área do imóvel rural declarada conforme documentação comprobatória de propriedade/posse/concessão [1.0000 hectares] e a área do imóvel rural identificada em representação gráfica [1.0045 hectares].",
              "geoImovel": "MULTIPOLYGON(((-44.6052642447223 -4.48351501166667,-44.6051451588889 -4.48406289888889,-44.6070051211111 -4.48440398,-44.6070456588889 -4.48411312833333,-44.607050335 -4.48409434722222,-44.6052642447223 -4.48351501166667)))",
              "proprietarios": [
                {
                  "tipoPessoa": "PF",
                  "cpfCnpj": "60780859308",
                  "nome": "BEATRIZ COELHO DAS NEVES",
                  "nomeFantasia": null
                },
                {
                  "tipoPessoa": "PF",
                  "cpfCnpj": "04014095380",
                  "nome": "JURANDY DA SILVA LOPES JUNIOR",
                  "nomeFantasia": null
                }
              ],
              "areaServidaoAdministrativa": 0.0,
              "areaLiquidaImovel": "1.0045",
              "areaPreservacaoPermanente": 0.0,
              "areaUsoRestrito": 0.0,
              "areaConsolidada": 1.00453400647938,
              "areaRemanescenteVegetacaoNativa": 0.0,
              "areaReservaLegal": "0.0000",
              "matricula": null,
              "dataMatricula": null,
              "livroMatricula": null,
              "folhaMatricula": null,
              "municipioCartorio": null,
              "ufCartorio": null
            }
            """;

    private String templateKey;

    @Before
    public void setUp() {
        String type = "CARReceipt,VerifiableCredential";
        String context = "https://www.w3.org/ns/credentials/v2";
        String format = "ldp_vc";
        templateKey = type + DELIMITER + context + DELIMITER + format;

        CredentialConfig config = new CredentialConfig();
        config.setVcTemplate(CAR_RECEIPT_TEMPLATE);
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
     * REPRODUZ O ERRO: quando "proprietarios" vem como array da API,
     * o template Velocity gera JSON inválido → JSONException.
     */
    @Test
    public void format_withProprietariosArray_shouldThrowJSONException() {
        JSONObject apiResponse = new JSONObject(MOCK_API_RESPONSE);

        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put(Constants.TEMPLATE_NAME, templateKey);
        templateParams.put(Constants.DID_URL, "did:web:injibr.github.io:inji-did:dev:mgi");
        templateParams.put(VCDM2Constants.VALID_FROM, "2026-06-12T20:20:29.401Z");
        templateParams.put(VCDM2Constants.VALID_UNTIL, "2028-06-11T20:20:29.401Z");

        templateParams.putAll(apiResponse.toMap());

        Map<String, Object> updatedTemplateParams = CredentialUtils.toJsonMap(templateParams);

        assertThrows(
                "Deveria lançar JSONException porque proprietarios é um array " +
                        "e o template espera uma string.",
                JSONException.class,
                () -> formatter.format(updatedTemplateParams)
        );
    }

    /**
     * Valida que o fix funciona: serializar "proprietarios" como string JSON escapada
     * (mesmo padrão do CAFDataProvider para "membros"/"areas") gera VP válida.
     */
    @Test
    public void format_withProprietariosSerializedAsEscapedString_shouldProduceValidVP() {
        JSONObject apiResponse = new JSONObject(MOCK_API_RESPONSE);

        // FIX: serializar proprietarios como string JSON escapada
        for (String arrayKey : new String[]{"proprietarios"}) {
            if (!apiResponse.isNull(arrayKey) && apiResponse.get(arrayKey) instanceof JSONArray) {
                String serialized = apiResponse.getJSONArray(arrayKey).toString();
                String quoted = JSONObject.quote(serialized);
                apiResponse.put(arrayKey, quoted.substring(1, quoted.length() - 1));
            }
        }

        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put(Constants.TEMPLATE_NAME, templateKey);
        templateParams.put(Constants.DID_URL, "did:web:injibr.github.io:inji-did:dev:mgi");
        templateParams.put(VCDM2Constants.VALID_FROM, "2026-06-12T20:20:29.401Z");
        templateParams.put(VCDM2Constants.VALID_UNTIL, "2028-06-11T20:20:29.401Z");

        templateParams.putAll(apiResponse.toMap());

        Map<String, Object> updatedTemplateParams = CredentialUtils.toJsonMap(templateParams);

        String result = formatter.format(updatedTemplateParams);

        assertNotNull(result);
        JSONObject vc = new JSONObject(result);
        JSONObject credentialSubject = vc.getJSONObject("credentialSubject");

        // Valida campos simples
        assertEquals("MA-2112233-044B8D8BC23345BE88179D0385A8BB1A", credentialSubject.getString("codigoImovel"));
        assertEquals("AT", credentialSubject.getString("situacaoImovel"));
        assertEquals("IRU", credentialSubject.getString("tipoImovel"));
        assertEquals("Trizidela do Vale", credentialSubject.getString("municipio"));
        assertEquals("MA", credentialSubject.getString("unidadeFederativa"));

        // Valida proprietarios como string JSON escapada parseável
        String proprietariosStr = credentialSubject.getString("proprietarios");
        assertNotNull(proprietariosStr);
        JSONArray proprietarios = new JSONArray(proprietariosStr);
        assertEquals(2, proprietarios.length());
        assertEquals("BEATRIZ COELHO DAS NEVES", proprietarios.getJSONObject(0).getString("nome"));
        assertEquals("JURANDY DA SILVA LOPES JUNIOR", proprietarios.getJSONObject(1).getString("nome"));
        assertEquals("PF", proprietarios.getJSONObject(0).getString("tipoPessoa"));

        System.out.println("====== VC CARReceipt GERADA COM FIX (proprietarios serializado) ======");
        System.out.println(vc.toString(2));
    }
}
