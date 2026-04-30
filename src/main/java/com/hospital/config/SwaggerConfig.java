package com.hospital.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация Swagger / OpenAPI 3 документации.
 *
 * Swagger UI доступен по адресу: http://localhost:8080/swagger-ui.html
 * OpenAPI JSON-спецификация: http://localhost:8080/v3/api-docs
 *
 * Эти URL открыты без авторизации (настроено в SecurityConfig через permitAll()).
 *
 * Что настраивается здесь:
 *   1. Метаданные API (заголовок, описание, версия, контакт).
 *   2. Схема аутентификации Bearer JWT — это добавляет кнопку "Authorize" в Swagger UI.
 *      После нажатия можно вставить токен (без "Bearer ") и все запросы из UI
 *      автоматически добавят заголовок Authorization: Bearer <token>.
 */
@Configuration
public class SwaggerConfig {

    /**
     * Создаёт объект OpenAPI — корневой элемент спецификации OpenAPI 3.0.
     *
     * addSecurityItem + addSecuritySchemes — настраивает глобальную Bearer-аутентификацию.
     *   SecurityRequirement.addList("Bearer Authentication") — говорит Swagger:
     *   "по умолчанию все эндпоинты требуют эту схему аутентификации".
     *   SecurityScheme.type(HTTP).scheme("bearer").bearerFormat("JWT") —
     *   описывает схему: HTTP Bearer токен в формате JWT.
     *   Это чисто документация — Swagger UI использует это для UI-формы "Authorize".
     *   Реальная проверка токена происходит в JwtAuthenticationFilter.
     */
    @Bean
    public OpenAPI hospitalOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hospital Information System API")
                        .description("REST API for Hospital Information System (HIS) pet project")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("HIS Development Team")
                                .email("his@hospital.com")))
                // Привязываем схему безопасности ко всем эндпоинтам глобально.
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .name("Bearer Authentication")
                                .type(SecurityScheme.Type.HTTP) // схема через HTTP-заголовок
                                .scheme("bearer")               // Authorization: Bearer ...
                                .bearerFormat("JWT")));          // формат — JWT (информационно)
    }
}
