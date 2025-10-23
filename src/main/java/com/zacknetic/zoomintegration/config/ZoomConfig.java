package com.zacknetic.zoomintegration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "zoom")
public class ZoomConfig {
    
    private Api api = new Api();
    private OAuth oauth = new OAuth();
    
    public Api getApi() {
        return api;
    }
    
    public void setApi(Api api) {
        this.api = api;
    }
    
    public OAuth getOauth() {
        return oauth;
    }
    
    public void setOauth(OAuth oauth) {
        this.oauth = oauth;
    }
    
    public static class Api {
        private String baseUrl;
        
        public String getBaseUrl() {
            return baseUrl;
        }
        
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
    
    public static class OAuth {
        private String tokenUrl;
        private String accountId;
        private String clientId;
        private String clientSecret;
        
        public String getTokenUrl() {
            return tokenUrl;
        }
        
        public void setTokenUrl(String tokenUrl) {
            this.tokenUrl = tokenUrl;
        }
        
        public String getAccountId() {
            return accountId;
        }
        
        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }
        
        public String getClientId() {
            return clientId;
        }
        
        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
        
        public String getClientSecret() {
            return clientSecret;
        }
        
        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }
}