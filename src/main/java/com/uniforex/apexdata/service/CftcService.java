package com.uniforex.apexdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniforex.apexdata.MarketDataClient;
import com.uniforex.apexdata.model.CotObservation;

public class CftcService {

    private static final String CFTC_ENDPOINT = "https://publicreporting.cftc.gov/resource/gpe5-46if.json" +
            "?cftc_contract_market_code=098662" +
            "&$order=report_date_as_yyyy_mm_dd%20DESC" +
            "&$limit=1";

    private final MarketDataClient client;
    private final ObjectMapper mapper;

    public CftcService(MarketDataClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public CotObservation fetchLatestUsdCot() throws Exception {
        String cftcJson = client.fetchRawJson(CFTC_ENDPOINT);
        CotObservation[] cotData = mapper.readValue(cftcJson, CotObservation[].class);

        if (cotData.length == 0) {
            throw new RuntimeException("No COT data found for contract code 098662.");
        }
        return cotData[0];
    }
}