package pro.akosarev.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
public class RestClientLoggingConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        ClientHttpRequestFactory base = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofSeconds(5))
                        .withReadTimeout(Duration.ofSeconds(30))
        );

        ClientHttpRequestFactory buffering = new BufferingClientHttpRequestFactory(base);

        return RestClient.builder()
                .requestFactory(buffering)
                .requestInterceptor(new LoggingInterceptor());
    }

    static final class LoggingInterceptor implements ClientHttpRequestInterceptor {
        private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {

            HttpHeaders headers = new HttpHeaders();
            headers.putAll(request.getHeaders());
            if (headers.containsKey(HttpHeaders.AUTHORIZATION)) {
                headers.set(HttpHeaders.AUTHORIZATION, "<redacted>");
            }

            log.info("RestClient -> {} {}\nheaders={}\nbody={}",
                    request.getMethod(), request.getURI(), headers, toString(body));

            ClientHttpResponse response = execution.execute(request, body);

            byte[] respBody = response.getBody().readAllBytes();
            log.info("RestClient <- {} {}\nheaders={}\nbody={}",
                    response.getStatusCode().value(), request.getURI(), response.getHeaders(), toString(respBody));

            return new ClientHttpResponseWrapper(response, respBody);
        }

        private String toString(byte[] bytes) {
            if (bytes == null || bytes.length == 0) return "";
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    static final class ClientHttpResponseWrapper implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] body;

        ClientHttpResponseWrapper(ClientHttpResponse delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override public org.springframework.http.HttpStatusCode getStatusCode() throws IOException { return delegate.getStatusCode(); }
        @Override public String getStatusText() throws IOException { return delegate.getStatusText(); }
        @Override public void close() { delegate.close(); }
        @Override public InputStream getBody() { return new java.io.ByteArrayInputStream(body); }
        @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
    }
}