package pro.akosarev.sandbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@SpringBootApplication
public class SpringSecurityKeycloakSandbox {

    public static void main(String[] args) {
        SpringApplication.run(SpringSecurityKeycloakSandbox.class, args);
    }

    /**
     * Цепочка фильров безопасности
     * */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
//        теперь с oauth2Login
        http.oauth2Login(Customizer.withDefaults());

        return http
                .authorizeHttpRequests(c -> c.requestMatchers("/error").permitAll()
                        .requestMatchers("/manager.html").hasRole("MANAGER")
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        // стандартное поведение -- получение прав из клейма scope
        var jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

        // устанавливаем имя принципала
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = jwtGrantedAuthoritiesConverter.convert(jwt);
//            var roles = (List<String>) jwt.getClaimAsMap("realm_access").get("roles");
            var roles = jwt.getClaimAsStringList("spring_sec_roles");

            return Stream.concat(authorities.stream(),
                            roles.stream()
                                    // откидываем стандартные для киклоак роли
                                    .filter(role -> role.startsWith("ROLE_"))
                                    .map(SimpleGrantedAuthority::new)
//            (SimpleGrantedAuthority a) -> (GrantedAuthority) a
                                    .map(GrantedAuthority.class::cast))
                    .toList();
        });

        return converter;
    }

    /**
     * Настраивает пользовательский {@link OAuth2UserService} для обработки {@link OidcUserRequest} и возврата {@link OidcUser}.
     * {@link OidcUserRequest} описывает запрос к провайдеру (киклоак в нашм случае)
     * {@link OidcUser} представляет пользователя, полученного от провайдера
     * Этот сервис расширяет стандартный {@link OidcUserService}, дополняя полномочия (authorities) пользователя ролями,
     * полученными из клейма "spring_sec_roles", предоставляемого провайдером удостоверений.
     *
     * @return экземпляр {@link OAuth2UserService}, который сочетает стандартную обработку OIDC-пользователя
     *         с добавлением полномочий на основе ролей
     */
    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> oAuth2UserService() {
        var oidcUserService = new OidcUserService();
        return (OidcUserRequest userRequest) -> {
            var oidcUser = oidcUserService.loadUser(userRequest);
            var roles = oidcUser.getClaimAsStringList("spring_sec_roles");
            var authorities = Stream.concat(oidcUser.getAuthorities().stream(),
                            roles.stream()
                                    .filter(role -> role.startsWith("ROLE_"))
                                    .map(SimpleGrantedAuthority::new)
                                    .map(GrantedAuthority.class::cast))
                    .toList();

            return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
        };
    }
}
