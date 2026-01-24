package pro.akosarev.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.util.stream.Stream;

@SpringBootApplication
public class SpringSecurityKeycloakSandbox {

    private final Logger logger = LoggerFactory.getLogger(SpringSecurityKeycloakSandbox.class);

    public static void main(String[] args) {
        SpringApplication.run(SpringSecurityKeycloakSandbox.class, args);
    }

    /**
     * Цепочка фильров безопасности
     *
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OAuth2UserService<OidcUserRequest, OidcUser> oAuth2UserService
    ) throws Exception {
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        // теперь с oauth2Login
        http.oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oAuth2UserService)));

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
// значения roles: "SCOPE_profile", "SCOPE_email"
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
     * Настраивает пользовательский {@link OAuth2UserService} для обработки {@link OidcUserRequest}
     * и возврата {@link OidcUser}.
     *
     * <p>Поток выполнения:</p>
     * <ol>
     *   <li>Пользователь в браузере запрашивает HTML.</li>
     *   <li>Приложение выполняет редирект:
     *       http://localhost:8080/realms/eselpo/protocol/openid-connect/auth?response_type=code&client_id=springsecurity&scope=openid&redirect_uri=http://localhost:8081/login/oauth2/code/keycloak
     *   </li>
     *   <li>Следом выполняется запрос к провайдеру:
     *       GET /login/oauth2/code/keycloak?iss=http%3A%2F%2Flocalhost%3A8080%2Frealms%2Feselpo
     *   </li>
     *   <li>HTTP GET http://localhost:8080/realms/eselpo/protocol/openid-connect/certs</li>
     *   <li>После этого выполняется лямбда.</li>
     *   <li>После выполнения {@code oidcUserService.loadUser(userRequest)} выведется лог.
     *   <p>
     *   {@code "classpath:логи после получения userinfo в лямбде.txt"}
     *   <p>
     *   </li>
     *   <li>При повторном вызове HTML не попадаем ни сюда, ни в {@code JwtGrantedAuthoritiesConverter},
     *       потому что создается JSESSIONID.</li>
     *   <li>Даже если делаем в Keycloak sign out, то не попадаем сюда, потому что авторизация происходит JSESSIONID.</li>
     * </ol>
     *
     * <p>{@link OidcUserRequest} описывает запрос к провайдеру (Keycloak в нашем случае),
     * {@link OidcUser} представляет пользователя, полученного от провайдера.</p>
     *
     * <p>Этот сервис расширяет стандартный {@link OidcUserService}, дополняя полномочия
     * (authorities) пользователя ролями, полученными из клейма {@code spring_sec_roles},
     * предоставляемого провайдером удостоверений.</p>
     *
     * @return экземпляр {@link OAuth2UserService}, который сочетает стандартную обработку OIDC-пользователя
     *         с добавлением полномочий на основе ролей
     */
    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> oAuth2UserService() {
        logger.info("Creating OAuth2UserService..."); // выполняется при инициализации контекста приложения
        var oidcUserService = new OidcUserService();
        logger.info("OAuth2UserService created.");
        return (OidcUserRequest userRequest) -> {
            logger.info("Loading user from OIDC provider...");
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
