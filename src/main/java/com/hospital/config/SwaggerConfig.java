package com.hospital.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI hospitalOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hospital Information System API")
                        .description("REST API for Hospital Information System (HIS) pet project")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("HIS Development Team")
                                .email("his@hospital.com")));
    }
}
