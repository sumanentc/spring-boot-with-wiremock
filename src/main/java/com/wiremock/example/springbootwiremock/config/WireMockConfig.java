package com.wiremock.example.springbootwiremock.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "wiremock-config")
@Data
@Profile("integration")
public class WireMockConfig {
    private List<WireMockProxy> proxies;
}
