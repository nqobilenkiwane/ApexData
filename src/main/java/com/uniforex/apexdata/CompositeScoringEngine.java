package com.uniforex.apexdata;

import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CompositeScoringEngine {

    /**
     * Iterates through raw metrics, applies the existing mathematical logic,
     * and returns a new list of fully scored metrics.
     */
    public List<MarketMetric> applyScores(List<MarketMetric> rawMetrics) {
        // 1. Extract values needed for composite scores (like Real Yield)
        double interestRate = rawMetrics.stream()
                .filter(m -> m.name().equals("Interest Rate"))
                .mapToDouble(MarketMetric::actualValue).findFirst().orElse(0.0);
        double inflation = rawMetrics.stream()
                .filter(m -> m.name().equals("YoY Inflation"))
                .mapToDouble(MarketMetric::actualValue).findFirst().orElse(0.0);

        int realYieldScore = scoreMacroFundamentals(interestRate, inflation);

        // 2. Loop through and score each metric
        List<MarketMetric> scoredMetrics = new ArrayList<>();

        for (MarketMetric m : rawMetrics) {
            int score = 0;

            if (m.name().equals("Interest Rate")) {
                score = realYieldScore; // Attach the combined Real Yield score here
            } else if (m.name().equals("YoY Inflation")) {
                score = 0; // Set to 0 to avoid double-counting the Real Yield score
            } else if (m.forecastValue() != 0.0) {
                // NEW: If an estimate exists, score the Surprise Factor!
                score = scoreSurprise(m);
            } else {
                // FALLBACK: If no estimate exists, use the absolute value logic
                switch (m.name()) {
                    case "Unemployment Rate":
                        score = scoreLaborMarket(m.actualValue());
                        break;
                    case "NFP (Jobs)":
                        score = scoreNfp(m.actualValue());
                        break;
                    case "Real GDP":
                        score = scoreGdp(m.actualValue());
                        break;
                    case "Retail Sales (MoM)":
                        score = scoreRetailSales(m.actualValue());
                        break;
                    default:
                        score = 0;
                }
            }
            // Create a fresh, immutable record with the calculated score
            scoredMetrics.add(new MarketMetric(m.name(), m.actualValue(), m.forecastValue(), score, m.category()));
        }
        return scoredMetrics;
    }

    /**
     * Calculates the Surprise Factor (Actual - Forecast) and assigns a directional score.
     */
    public int scoreSurprise(MarketMetric metric) {
        double surprise = metric.actualValue() - metric.forecastValue();
        double epsilon = 0.0001; // Avoid floating point rounding issues

        if (Math.abs(surprise) < epsilon) {
            return 0; // Neutral (Met expectations perfectly)
        }

        // Inverse indicators: Higher than forecast is BAD for the economy/currency
        boolean isInverse = metric.name().equalsIgnoreCase("Unemployment Rate")
                || metric.name().contains("Jobless Claims");

        if (isInverse) {
            return surprise > 0 ? -1 : 1;
        } else {
            return surprise > 0 ? 1 : -1;
        }
    }

    /**
     * Groups the metrics by Category and sums their scores dynamically.
     */
    public Map<MetricCategory, Integer> calculateCategoryScores(List<MarketMetric> scoredMetrics) {
        return scoredMetrics.stream()
                .collect(Collectors.groupingBy(
                        MarketMetric::category,
                        Collectors.summingInt(MarketMetric::scoreDelta)
                ));
    }

    /**
     * Calculates the absolute total score across all data points.
     */
    public int calculateTotalScore(List<MarketMetric> scoredMetrics) {
        return scoredMetrics.stream().mapToInt(MarketMetric::scoreDelta).sum();
    }

//    public int scoreSurprise(MarketMetric metric) {
//        double surprise = metric.actualValue() - metric.forecastValue();
//        double epsilon = 0.0001; // Avoid floating point rounding issues
//
//        if (Math.abs(surprise) < epsilon) {
//            return 0; // Neutral (Met expectations)
//        }
//
//        // Inverse indicators: Higher than forecast is BAD for currency/growth
//        boolean isInverse = metric.name().equalsIgnoreCase("Unemployment Rate")
//                || metric.name().contains("Jobless Claims");
//
//        if (isInverse) {
//            return surprise > 0 ? -1 : 1;
//        } else {
//            return surprise > 0 ? 1 : -1;
//        }
//    }

    // ========================================================================
    // EXISTING SCORING LOGIC (Untouched)
    // ========================================================================

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

    public int scoreInstitutionalPositioning(double netPositions) {
        if (netPositions > 0) {
            return 1;
        } else if (netPositions < 0) {
            return -1;
        } else {
            return 0;
        }
    }

    public String getOverallBiasLabel(int totalScore) {
        if (totalScore >= 2) return "STRONGLY BULLISH";
        if (totalScore == 1) return "BULLISH";
        if (totalScore == 0) return "NEUTRAL";
        if (totalScore == -1) return "BEARISH";
        if (totalScore <= -2) return "STRONGLY BEARISH";
        return "UNKNOWN";
    }

    public int scoreLaborMarket(double unemploymentRate) {
        if (unemploymentRate < 4.0) {
            return 1;
        } else if (unemploymentRate > 4.5) {
            return -1;
        } else {
            return 0;
        }
    }

    public int scoreNfp(double nfpChange) {
        if (nfpChange > 150.0) {
            return 1;
        } else if (nfpChange < 100.0) {
            return -1;
        } else {
            return 0;
        }
    }

    public int scoreGdp(double gdp) {
        if (gdp > 2.5) {
            return 1;
        } else if (gdp < 1.0) {
            return -1;
        } else {
            return 0;
        }
    }

    public int scoreRetailSales(double momRetailSales) {
        if (momRetailSales > 0.1) {
            return 1;
        } else if (momRetailSales < -0.1) {
            return -1;
        } else {
            return 0;
        }
    }

    public int scoreTechnicals(double currentPrice, double sma200, double rsi14) {
        boolean isUptrend = currentPrice > sma200;
        boolean isBullishMomentum = rsi14 > 50.0;

        if (isUptrend && isBullishMomentum) {
            return 1;
        } else if (!isUptrend && !isBullishMomentum) {
            return -1;
        } else {
            return 0;
        }
    }
}