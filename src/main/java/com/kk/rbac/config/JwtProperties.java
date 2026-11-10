package com.kk.rbac.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NotBlank(message = "JWT签名密钥不能为空")
    private String secretBase64;

    @NotNull(message = "JWT有效期不能为空")
    private Duration ttl;
    @AssertTrue(message = "JWT有效期必须大于0")
    public boolean isTtlPositive() {
        return ttl == null || (!ttl.isZero() && !ttl.isNegative());
    }
}