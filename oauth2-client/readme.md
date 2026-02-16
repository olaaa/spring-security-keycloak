В браузере откройте: http://localhost:8082/client/call-manager.

URL `http://localhost:8082/login/oauth2/code/keycloak` является стандартным для Spring Security при использовании модуля
OAuth2 Client.

#### 1. Как формируется этот URL?

Принцип формирования основан на шаблоне по умолчанию, который зашит в Spring Security:
`{baseUrl}/login/oauth2/code/{registrationId}`

Разберем составляющие в вашем конкретном случае:

* **`{baseUrl}`**: В вашем случае это `http://localhost:8082`. Это адрес вашего приложения.
* **`/login/oauth2/code/`**: Это фиксированная часть пути (base path), которую Spring Security использует по умолчанию
  для обработки callback-запросов (Authorization Response) в потоке Authorization Code Flow.
* **`{registrationId}`**: Это уникальный идентификатор регистрации клиента. В вашем файле
  `oauth2-client/src/main/resources/application.yml` он задан как `keycloak`:
  ```yaml
  spring:
    security:
      oauth2:
        client:
          registration:
            keycloak: # <-- Это и есть registrationId
              ...
              redirect-uri: http://localhost:8082/login/oauth2/code/keycloak
  ```

#### 2. Какие классы обрабатывают этот URL?

В Spring Security за обработку этого URL отвечает целая цепочка фильтров и провайдеров, но основными являются:

1. **`OAuth2LoginAuthenticationFilter`**:
    * **Роль**: Это главный фильтр, который "слушает" запросы, приходящие по адресу `/login/oauth2/code/*`.
    * **Действие**: Когда Keycloak перенаправляет пользователя обратно с временным кодом (`code`), этот фильтр
      перехватывает запрос. Он извлекает `code` и `state` из параметров запроса и создает объект аутентификации для
      дальнейшей проверки.

2. **`OAuth2LoginAuthenticationProvider`**:
    * **Роль**: Обрабатывает запрос на аутентификацию, созданный фильтром.
    * **Действие**: Именно этот класс инициирует второй шаг Authorization Code Flow — обмен временного кода (`code`) на
      токен доступа (`Access Token`). Он использует `OAuth2AuthorizationCodeAuthenticationProvider` для выполнения
      самого сетевого запроса к Keycloak.

3. **`DefaultOAuth2AuthorizationRequestResolver`**:
    * **Роль**: Хотя он не принимает входящий запрос на этот URL, он участвует в формировании данных для него на
      начальном этапе (когда вы только переходите на `/login`).

4. **`OAuth2AuthorizedClientRepository` / `OAuth2AuthorizedClientService`**:
    * **Роль**: После успешного обмена кода на токен эти компоненты сохраняют полученный токен, чтобы вы могли
      использовать его в контроллере (например, через аннотацию `@RegisteredOAuth2AuthorizedClient`).

#### Почему это важно?

Если вы измените `redirect-uri` в настройках на что-то другое, не соответствующее шаблону `/login/oauth2/code/*`, то вам
придется вручную настраивать `OAuth2LoginAuthenticationFilter`, чтобы он знал, какой URL теперь нужно перехватывать.
Использование стандартного пути избавляет от лишней конфигурации.

# Поток выполнения:

При старте приложения выполняется:
HTTP GET http://localhost:8080/realms/eselpo/.well-known/openid-configuration       
Accept=[application/json, application/*+json]

1. Пользователь в браузере запрашивает HTML.
2. Приложение выполняет редирект:
3. Redirecting to http://localhost:8082/oauth2/authorization/keycloak
4. Потом на киклоак ЧЕРЕЗ БРАУЗЕР 302 код
   GET http://localhost:8080/realms/eselpo/protocol/openid-connect/auth
   ?response_type=code
   &client_id=springsecurity
   &scope=openid
   &state=DLxrzqMcCqlckR97Iq1P2woYeQKOQIikp1ypQizMJ6s%3D
   &redirect_uri=http://localhost:8082/login/oauth2/code/keycloak
   &nonce=gB6-0sCoOziKHJailv3DxiEQ86z1sR91e-znk9K7ZW0
5. Открывается форма аутентификации keycloak

6. Следом провайдер дергает нас ЧЕРЕЗ БРАУЗЕР 302 код:  
   GET /login/oauth2/code/keycloak
   ?state=DLxrzqMcCqlckR97Iq1P2woYeQKOQIikp1ypQizMJ6s%3D
   &session_state=SOcbGTZOX293XJWbmBKpPnt4
   &iss=http%3A%2F%2Flocalhost%3A8080%2Frealms%2Feselpo
   &code=97499581-93c4-9c15-0050-7354493966fa.SOcbGTZOX293XJWbmBKpPnt4.b4188d58-8e42-479f-a70d-4b8351f4020f
7. Меняем код на токен доступа:
   -> OUT POST http://localhost:8080/realms/eselpo/protocol/openid-connect/token
   headers=[Accept:"application/json;charset=UTF-8", Content-Type:"application/x-www-form-urlencoded;charset=UTF-8", Authorization:"<redacted>", Content-Length:"211"]
   body:
   grant_type=authorization_code
   code=c1be0c78-6314-6a62-cbe3-9199b4ffaeb0.2Hp4FPw0vdp7g089ZIWEZh9X.b4188d58-8e42-479f-a70d-4b8351f4020f
   redirect_uri=http://localhost:8082/login/oauth2/code/keycloak
8. В ответ приходит jwt токен с access_token и refresh_token, "token_type":"Bearer", "id_token", "not-before-policy":1769644632,"session_state":"2Hp4FPw0vdp7g089ZIWEZh9X","scope":"openid profile email"
9. Проверка:  HTTP GET http://localhost:8080/realms/eselpo/protocol/openid-connect/certs
10. HTTP GET http://localhost:8080/realms/eselpo/protocol/openid-connect/userinfo
11. Changed session id from 1171681A4881245F89E3D8659A79B6FE
12. Authentication: OAuth2AuthenticationToken
    ``` 
    ├─ principal
    │  ├─ name: j.daniels
    │  ├─ authorities (principal):
    │  │  ├─ OIDC_USER
    │  │  ├─ SCOPE_email
    │  │  ├─ SCOPE_openid
    │  │  └─ SCOPE_profile
    │  └─ attributes (OIDC ID Token claims):
    │     ├─ iss: http://localhost:8080/realms/eselpo
    │     ├─ sub: 2c662c42-02f4-426e-9fd0-8d559357fdca
    │     ├─ aud: [springsecurity]
    │     ├─ azp: springsecurity
    │     ├─ typ: ID
    │     ├─ sid: 2Hp4FPw0vdp7g089ZIWEZh9X
    │     ├─ nonce: vzzyDZM11Gk5zaf3y_Ei4qaPlEufqPRryU0SddB9dsk
    │     ├─ at_hash: i_QSrDqQ0MwFOhRgbYG3Og
    │     ├─ acr: 1
    │     ├─ auth_time: 2026-01-29T00:04:40Z
    │     ├─ iat: 2026-01-29T00:04:40Z
    │     ├─ exp: 2026-01-29T00:09:40Z
    │     ├─ jti: 6b23cbf9-c014-1042-2fd6-6824b6516a61
    │     ├─ preferred_username: j.daniels
    │     ├─ given_name: Jack
    │     ├─ family_name: Daniels
    │     ├─ name: Jack Daniels
    │     ├─ email: j.daniels@example.com
    │     ├─ email_verified: true
    │     └─ spring_sec_roles:
    │        ├─ offline_access
    │        ├─ ROLE_MANAGER
    │        ├─ uma_authorization
    │        └─ default-roles-eselpo
    ├─ credentials: [PROTECTED]
    ├─ authenticated: true
    ├─ details: WebAuthenticationDetails
    │  ├─ remoteIpAddress: 0:0:0:0:0:0:0:1
    │  └─ sessionId: 1171681A4881245F89E3D8659A79B6FE
    └─ authorities (token):
    ├─ OIDC_USER
    ├─ SCOPE_email
    ├─ SCOPE_openid
    └─ SCOPE_profile  
    ```
13. Redirecting to http://localhost:8082/client/call-manager?continue
14. RestClient -> GET http://localhost:8081/manager.html
    headers=[Authorization:"<redacted>", Content-Length:"0"]
15. В ответ приходит содержимое manager.html Content-Type:"text/html"