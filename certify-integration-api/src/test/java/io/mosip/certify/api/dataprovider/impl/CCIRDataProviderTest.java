package io.mosip.certify.api.dataprovider.impl;

import io.netty.handler.logging.LogLevel;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

import static org.junit.Assert.*;

public class CCIRDataProviderTest {

    private WebClient buildLoggingWebClient() {
        HttpClient httpClient = HttpClient.create()
                .wiretap("reactor.netty.http.client.HttpClient", LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(ExchangeFilterFunction.ofRequestProcessor(request -> {
                    System.out.println("\n===== REQUEST =====");
                    System.out.println(request.method() + " " + request.url());
                    request.headers().forEach((name, values) ->
                            values.forEach(value -> System.out.println(name + ": " + value)));
                    return Mono.just(request);
                }))
                .filter(ExchangeFilterFunction.ofResponseProcessor(response -> {
                    System.out.println("\n===== RESPONSE =====");
                    System.out.println("Status: " + response.statusCode());
                    response.headers().asHttpHeaders().forEach((name, values) ->
                            values.forEach(value -> System.out.println(name + ": " + value)));
                    return Mono.just(response);
                }))
                .build();
    }

    /**
     * Teste de integração do fluxo completo do CCIRDataProvider.getData():
     *   1. CCIRTokenClient.getAccessToken()
     *   2. SncrCpfCnpjClient.getRegistrationNumber(cpf, token, xcpf)
     *   3. WebClient GET ccir.document.api.url/{registrationNumber}
     *   4. Parse do JSON e retorno do primeiro elemento de "result"
     *
     * Variáveis de ambiente necessárias:
     *   CCIR_TOKEN_URL                - URL do endpoint OAuth2 token
     *   CCIR_CLIENT_ID                - Client ID
     *   CCIR_CLIENT_SECRET            - Client Secret
     *   CCIR_REGISTRATION_NUMBER_URL  - URL da API SNCR com placeholder %s
     *   CCIR_DOCUMENT_API_URL         - URL da API de documento CCIR com placeholder %s
     *   CCIR_TEST_CPF                 - CPF para teste
     *   CCIR_XCPF_USER               - Valor do header x-cpf-usuario
     */
    @Test
    public void getData_withRealApi_returnsJsonObject() throws Exception {
        String tokenUrl = System.getenv("CCIR_TOKEN_URL");
        String clientId = System.getenv("CCIR_CLIENT_ID");
        String clientSecret = System.getenv("CCIR_CLIENT_SECRET");
        String registrationUrl = System.getenv("CCIR_REGISTRATION_NUMBER_URL");
        String documentApiUrl = System.getenv("CCIR_DOCUMENT_API_URL");
        String cpf = System.getenv("CCIR_TEST_CPF");
        String xcpf = System.getenv("CCIR_XCPF_USER");

        Assume.assumeTrue("CCIR_TOKEN_URL not set", tokenUrl != null && !tokenUrl.isBlank());
        Assume.assumeTrue("CCIR_CLIENT_ID not set", clientId != null && !clientId.isBlank());
        Assume.assumeTrue("CCIR_CLIENT_SECRET not set", clientSecret != null && !clientSecret.isBlank());
        Assume.assumeTrue("CCIR_REGISTRATION_NUMBER_URL not set", registrationUrl != null && !registrationUrl.isBlank());
        Assume.assumeTrue("CCIR_DOCUMENT_API_URL not set", documentApiUrl != null && !documentApiUrl.isBlank());
        Assume.assumeTrue("CCIR_TEST_CPF not set", cpf != null && !cpf.isBlank());
        Assume.assumeTrue("CCIR_XCPF_USER not set", xcpf != null && !xcpf.isBlank());

        WebClient loggingWebClient = buildLoggingWebClient();

        // Monta as dependências do CCIRDataProvider
        CCIRTokenClient tokenClient = new CCIRTokenClient(
                loggingWebClient, tokenUrl, clientId, clientSecret, false);
        SncrCpfCnpjClient sncrClient = new SncrCpfCnpjClient(loggingWebClient, registrationUrl);
        CCIRDataProvider dataProvider = new CCIRDataProvider(
                sncrClient, loggingWebClient, documentApiUrl, xcpf, tokenClient);

        // Executa o fluxo completo
        System.out.println("====== CCIRDataProvider.getData() ======");
        System.out.println("CPF: " + cpf);

        JSONObject result = dataProvider.getData(cpf);

        System.out.println("\n====== RESULTADO FINAL ======");
        System.out.println(result.toString(2));

        assertNotNull("Result should not be null", result);
        assertTrue("Result should have codigoImovel", result.has("codigoImovel"));
        System.out.println("codigoImovel: " + result.get("codigoImovel"));
    }
}
