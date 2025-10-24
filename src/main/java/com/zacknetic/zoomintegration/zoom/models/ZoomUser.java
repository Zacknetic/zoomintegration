package com.zacknetic.zoomintegration.zoom.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Zoom user information model
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZoomUser {
    
    private String id;
    
    @JsonProperty("first_name")
    private String firstName;
    
    @JsonProperty("last_name")
    private String lastName;
    
    private String email;
    
    private String type;
    
    private String status;
    
    @JsonProperty("dept")
    private String department;
    
    @JsonProperty("created_at")
    private String createdAt;
}