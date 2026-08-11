package com.uniforex.apexdata;

public class CompositeScoringEngine {

    /**
     * Scores the Macro Fundamentals based on Real Yield.
     * Returns: +1 (Bullish), -1 (Bearish), 0 (Neutral)
     */
    public int scoreMacroFundamentals(double interestRate, double inflationRate) {
        double realYield = interestRate - inflationRate;
        double threshold = 0.5;

        if (realYield > threshold) {
            return 1;
        } else if (realYield < -threshold) {
            return -1;
        } else {
            return 0;
        }
    }

    /**
     * Scores the Institutional Order Flow based on Net Hedge Fund Positions.
     * Returns: +1 (Bullish), -1 (Bearish), 0 (Neutral)
     */
    public int scoreInstitutionalPositioning(double netPositions) {
        if (netPositions > 0) {
            return 1;
        } else if (netPositions < 0) {
            return -1;
        } else {
            return 0;
        }
    }

    /**
     * Translates the final integer tally into a clear, readable market bias.
     */
    public String getOverallBiasLabel(int totalScore) {
        if (totalScore >= 2) return "STRONGLY BULLISH";
        if (totalScore == 1) return "BULLISH";
        if (totalScore == 0) return "NEUTRAL";
        if (totalScore == -1) return "BEARISH";
        if (totalScore <= -2) return "STRONGLY BEARISH";
        return "UNKNOWN";
    }
}