package pro.akosarev.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class OAuth2HttpLoggingConfig {

    @Bean
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>
    authorizationCodeTokenResponseClient() {

        var client = new DefaultAuthorizationCodeTokenResponseClient();
        client.setRestOperations(oauth2LoggingRestTemplate());
        return client;
    }

    @Bean
    OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest>
    clientCredentialsTokenResponseClient() {

        var client = new DefaultClientCredentialsTokenResponseClient();
        client.setRestOperations(oauth2LoggingRestTemplate());
        return client;
    }

    private RestTemplate oauth2LoggingRestTemplate() {
        // Важно: для OAuth2 token endpoint нужны правильные converters и error handler
        List<HttpMessageConverter<?>> converters = List.of(
                new FormHttpMessageConverter(),
                new OAuth2AccessTokenResponseHttpMessageConverter()
        );

        RestTemplate restTemplate = new RestTemplate(converters);
        restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());

        // Buffering нужен, чтобы можно было прочитать body ответа в логах, не “съев” поток.
        ClientHttpRequestFactory requestFactory =
                new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory());
        restTemplate.setRequestFactory(requestFactory);

        var interceptors = new ArrayList<>(restTemplate.getInterceptors());
        interceptors.add(new OutboundHttpLoggingInterceptor());
        restTemplate.setInterceptors(interceptors);

        return restTemplate;
    }

    static class OutboundHttpLoggingInterceptor implements ClientHttpRequestInterceptor {
        private static final Logger log = LoggerFactory.getLogger(OutboundHttpLoggingInterceptor.class);

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {

            HttpHeaders headers = new HttpHeaders();
            headers.putAll(request.getHeaders());

            // ВАЖНО: не логируем bearer и прочие секреты
            if (headers.containsKey(HttpHeaders.AUTHORIZATION)) {
                headers.set(HttpHeaders.AUTHORIZATION, "<redacted>");
            }

            log.info("-> OUT {} {}\nheaders={}\nbody={}",
                    request.getMethod(),
                    request.getURI(),
                    headers,
                    bodyToString(body));

            ClientHttpResponse response = execution.execute(request, body);

            HttpHeaders respHeaders = new HttpHeaders();
            respHeaders.putAll(response.getHeaders());
//            if (respHeaders.containsKey(HttpHeaders.SET_COOKIE)) {
//                respHeaders.set(HttpHeaders.SET_COOKIE, "<redacted>");
//            }

            byte[] respBody = response.getBody().readAllBytes();
            log.info("<- IN  {} {}\nheaders={}\nbody={}",
                    response.getStatusCode().value(),
                    request.getURI(),
                    respHeaders,
                    bodyToString(respBody));

            // Вернём response с восстановленным body
            return new ClientHttpResponseWrapper(response, respBody);
        }

        private String bodyToString(byte[] body) {
            if (body == null || body.length == 0) return "";
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    static class ClientHttpResponseWrapper implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] body;

        ClientHttpResponseWrapper(ClientHttpResponse delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override
        public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public InputStream getBody() {
            return new java.io.ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }
}