package com.uniforex.apexdata;

public class ScoringEngine {

    /**
     * Evaluates the fundamental bias for a currency based on Real Yield.
     * Real Yield = Interest Rate - YoY Inflation Rate
     */
    public String evaluateFundamentalBias(double interestRate, double inflationRate) {
        double realYield = interestRate - inflationRate;

        // We use a small threshold (e.g., 0.5%) to prevent flip-flopping on tiny differences
        double threshold = 0.5;

        if (realYield > threshold) {
            return "BULLISH (Positive Real Yield)";
        } else if (realYield < -threshold) {
            return "BEARISH (Negative Real Yield)";
        } else {
            return "NEUTRAL (Rates and Inflation are tightly coupled)";
        }
    }

    /**
     * Returns the exact numeric Real Yield for display purposes.
     */
    public double calculateRealYield(double interestRate, double inflationRate) {
        return interestRate - inflationRate;
    }
}