package com.wiremock.example.springbootwiremock.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UniversityDTO {
    private List<String> domains;
    private List<String> web_pages;
    private String name;
    private String country;
    private String alpha_two_code;
}
