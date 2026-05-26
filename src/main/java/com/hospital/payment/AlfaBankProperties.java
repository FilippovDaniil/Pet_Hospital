package com.hospital.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "alfabank")
@Data
public class AlfaBankProperties {
    private String gatewayUrl;
    private String userName;
    private String password;
    private String returnUrl;
    private String failUrl;
}
