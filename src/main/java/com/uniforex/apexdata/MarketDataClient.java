package com.uniforex.apexdata;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class MarketDataClient {

    private final HttpClient httpClient;

    public MarketDataClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String fetchRawJson(String endpointURL) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointURL))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API request failed with status: " + response.statusCode());
        }

        return response.body();
    }
}