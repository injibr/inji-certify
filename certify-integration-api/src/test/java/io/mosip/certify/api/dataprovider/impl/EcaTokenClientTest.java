package io.mosip.certify.api.dataprovider.impl;

import org.junit.Assume;
import org.junit.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class EcaTokenClientTest {

    @Test
    public void getAccessToken_withRealCredentials_returnsToken() {
        String clientId = System.getenv("ECA_CLIENT_ID");
        String clientSecret = System.getenv("ECA_CLIENT_SECRET");

        Assume.assumeTrue("ECA_CLIENT_ID env var not set", clientId != null && !clientId.isBlank());
        Assume.assumeTrue("ECA_CLIENT_SECRET env var not set", clientSecret != null && !clientSecret.isBlank());

        EcaTokenClient client = new EcaTokenClient(
                WebClient.create(),
                "https://hisrj.dataprev.gov.br/oauth2/token",
                clientId,
                clientSecret
        );

        String token = client.getAccessToken();

        assertNotNull(token);
        assertFalse(token.isBlank());
    }
}
