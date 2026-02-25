package io.mosip.certify.api.config;
 
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
 
import javax.net.ssl.SSLException;
import java.util.Arrays;
 
@Slf4j
@Configuration
public class WebClientConfig {
 
    @Bean
    public WebClient webClient() throws SSLException {
        log.info("Configuring WebClient with custom cipher suites");
 
        SslContext sslContext = SslContextBuilder
                .forClient()
                .protocols("TLSv1.2", "TLSv1.3")
                .ciphers(Arrays.asList(
                        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                        "TLS_RSA_WITH_AES_256_GCM_SHA384",
                        "TLS_RSA_WITH_AES_128_GCM_SHA256"
                ))
                .build();
 
        HttpClient httpClient = HttpClient.create()
                .secure(sslSpec -> sslSpec.sslContext(sslContext));
 
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
 
 
