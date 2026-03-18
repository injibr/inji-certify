package io.mosip.certify.api.dataprovider.impl;

import io.netty.handler.logging.LogLevel;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

import static org.junit.Assert.*;

public class SncrCpfCnpjClientTest {

    private WebClient buildLoggingWebClient() {
        HttpClient httpClient = HttpClient.create()
                .wiretap("reactor.netty.http.client.HttpClient", LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(ExchangeFilterFunction.ofRequestProcessor(request -> {
                    System.out.println("===== REQUEST =====");
                    System.out.println(request.method() + " " + request.url());
                    request.headers().forEach((name, values) ->
                            values.forEach(value -> System.out.println(name + ": " + value)));
                    return Mono.just(request);
                }))
                .filter(ExchangeFilterFunction.ofResponseProcessor(response -> {
                    System.out.println("===== RESPONSE =====");
                    System.out.println("Status: " + response.statusCode());
                    response.headers().asHttpHeaders().forEach((name, values) ->
                            values.forEach(value -> System.out.println(name + ": " + value)));
                    return Mono.just(response);
                }))
                .build();
    }

    @Test
    public void getRegistrationNumber_withRealApi_returnsCodigoImovel() {
        String tokenUrl = System.getenv("CCIR_TOKEN_URL");
        String clientId = System.getenv("CCIR_CLIENT_ID");
        String clientSecret = System.getenv("CCIR_CLIENT_SECRET");
        String registrationUrl = System.getenv("CCIR_REGISTRATION_NUMBER_URL");
        String cpf = System.getenv("CCIR_TEST_CPF");
        String xcpf = System.getenv("CCIR_XCPF_USER");

        Assume.assumeTrue("CCIR_TOKEN_URL env var not set", tokenUrl != null && !tokenUrl.isBlank());
        Assume.assumeTrue("CCIR_CLIENT_ID env var not set", clientId != null && !clientId.isBlank());
        Assume.assumeTrue("CCIR_CLIENT_SECRET env var not set", clientSecret != null && !clientSecret.isBlank());
        Assume.assumeTrue("CCIR_REGISTRATION_NUMBER_URL env var not set", registrationUrl != null && !registrationUrl.isBlank());
        Assume.assumeTrue("CCIR_TEST_CPF env var not set", cpf != null && !cpf.isBlank());
        Assume.assumeTrue("CCIR_XCPF_USER env var not set", xcpf != null && !xcpf.isBlank());

        WebClient loggingWebClient = buildLoggingWebClient();

        // Step 1: Obter token OAuth2
        System.out.println("\n====== STEP 1: OBTENDO TOKEN ======");
        CCIRTokenClient tokenClient = new CCIRTokenClient(
                loggingWebClient, tokenUrl, clientId, clientSecret, false);
        String accessToken = tokenClient.getAccessToken();
        assertNotNull("Access token should not be null", accessToken);
        assertFalse("Access token should not be blank", accessToken.isBlank());
        System.out.println("Access Token: " + accessToken.substring(0, Math.min(50, accessToken.length())) + "...");

        // Step 2: Buscar código do imóvel pelo CPF
        System.out.println("\n====== STEP 2: BUSCANDO REGISTRATION NUMBER ======");
        SncrCpfCnpjClient sncrClient = new SncrCpfCnpjClient(loggingWebClient, registrationUrl);
        String registrationNumber = sncrClient.getRegistrationNumber(cpf, accessToken, xcpf);

        System.out.println("\n====== RESULTADO ======");
        System.out.println("CPF: " + cpf);
        System.out.println("Registration Number (codigoImovel): " + registrationNumber);

        assertNotNull("Registration number should not be null", registrationNumber);
        assertFalse("Registration number should not be blank", registrationNumber.isBlank());
    }
}
