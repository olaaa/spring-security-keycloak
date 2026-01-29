package pro.akosarev.sandbox;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@RestController
public class ClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Bean
    public SecurityFilterChain clientSecurityFilterChain(HttpSecurity http) throws Exception {
        http.oauth2Login(oauth2 -> {});
        return http
                .authorizeHttpRequests(c -> c.anyRequest().authenticated())
                .build();
    }

    @GetMapping("/client/call-manager")
    public String callManager(@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient client) {
        String token = client.getAccessToken().getTokenValue();
        
        RestClient restClient = restClientBuilder
                .baseUrl("http://localhost:8081")
                .defaultHeader("Authorization", "Bearer " + token)
                .build();

        return restClient.get()
                .uri("/manager.html")
                .retrieve()
                .body(String.class);
    }
}
