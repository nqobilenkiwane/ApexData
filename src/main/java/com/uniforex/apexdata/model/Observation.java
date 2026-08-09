package com.uniforex.apexdata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Observation(String date, String value) {

    // Safely parses the numerical rate value
    public double getRateAsDouble() {
        if (value == null || value.equals(".")) {
            return 0.0;
        }
        return Double.parseDouble(value);
    }
}