package com.uniforex.apexdata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CotObservation(
        String contract_market_name,
        String report_date_as_yyyy_mm_dd,

        // Removed the "_all" suffix to match the new CFTC JSON keys
        String lev_money_positions_long,
        String lev_money_positions_short
) {

    // Helper method to safely parse long (buy) positions
    public double getLongPositions() {
        if (lev_money_positions_long == null || lev_money_positions_long.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(lev_money_positions_long);
    }

    // Helper method to safely parse short (sell) positions
    public double getShortPositions() {
        if (lev_money_positions_short == null || lev_money_positions_short.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(lev_money_positions_short);
    }

    // Calculate the Net Position (Longs minus Shorts)
    public double getNetPosition() {
        return getLongPositions() - getShortPositions();
    }
}