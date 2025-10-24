package com.zacknetic.zoomintegration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "zoom")
public class ZoomConfig {
    
    private Api api = new Api();
    private OAuth oauth = new OAuth();
    
    @Data
    public static class Api {
        private String baseUrl;
    }
    
    @Data
    public static class OAuth {
        private String tokenUrl;
        private String accountId;
        private String clientId;
        private String clientSecret;
    }
}