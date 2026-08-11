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

    /**
     * Scores the Labor Market based on the Unemployment Rate.
     * Returns: +1 (Bullish), -1 (Bearish), 0 (Neutral)
     */
    public int scoreLaborMarket(double unemploymentRate) {
        if (unemploymentRate < 4.0) {
            return 1;   // Extremely tight labor market, fed can hike rates
        } else if (unemploymentRate > 4.5) {
            return -1;  // Labor market cooling, rate cuts likely
        } else {
            return 0;   // Normal/Neutral bounds
        }
    }

    /**
     * Scores Economic Growth based on Real GDP (Annualized).
     * Returns: +1 (Bullish), -1 (Bearish), 0 (Neutral)
     */
    public int scoreGdp(double gdp) {
        if (gdp > 2.5) {
            return 1;   // Strong growth, supportive of higher rates
        } else if (gdp < 1.0) {
            return -1;  // Weak growth / contraction, dovish for USD
        } else {
            return 0;   // Moderate, stable growth
        }
    }
}