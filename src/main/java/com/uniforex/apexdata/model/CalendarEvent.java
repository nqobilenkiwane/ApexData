package com.uniforex.apexdata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CalendarEvent(
        String event,
        String date,
        String country,
        String currency,
        Double actual,    // This forces Jackson to convert the JSON value to a number
        Double estimate,  // This forces Jackson to convert the JSON value to a number
        Double previous,  // This forces Jackson to convert the JSON value to a number
        String impact
) {}