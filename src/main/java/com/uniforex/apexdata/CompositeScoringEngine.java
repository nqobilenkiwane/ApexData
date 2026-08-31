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
        List<MarketMetric> scoredMetrics = new ArrayList<>();

        for (MarketMetric m : rawMetrics) {
            int score = 0;

            if (m.forecastValue() != 0.0) {
                // PRIORITY: If an estimate (or Moving Average) exists, score the Surprise Factor
                score = scoreSurprise(m);
            } else {
                // FALLBACK: Absolute scoring logic for metrics without estimates
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
//                    case "10Y Real Yield":
//                        // Positive real yield attracts capital (+1). Negative repels it (-1).
//                        score = m.actualValue() > 0 ? 1 : -1;
//                        break;
//                    case "2s10s Yield Curve":
//                        // Normal curve (+1). Inverted curve means recession risk (-1).
//                        score = m.actualValue() > 0 ? 1 : -1;
//                        break;
                    case "YoY Inflation":
                        // If no estimate is available, high inflation is broadly Hawkish/Bullish
                        score = m.actualValue() > 2.0 ? 1 : -1;
                        break;
//                    case "COT Net Positioning":
//                        score = m.actualValue() > 0 ? 1 : (m.actualValue() < 0 ? -1 : 0);
//                        break;
//                    case "COT Long Percentage":
//                        double pct = m.actualValue();
//                        if (pct >= 80) score = -1;       // Bearish (Overcrowded Longs)
//                        else if (pct <= 20) score = 1;   // Bullish (Short Squeeze)
//                        else if (pct >= 55) score = 1;   // Bullish (Healthy Trend)
//                        else if (pct <= 45) score = -1;  // Bearish (Healthy Short)
//                        else score = 0;                  // Neutral
//                        break;
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

//    public int scoreMacroFundamentals(double interestRate, double inflationRate) {
//        double realYield = interestRate - inflationRate;
//        double threshold = 0.5;
//
//        if (realYield > threshold) {
//            return 1;
//        } else if (realYield < -threshold) {
//            return -1;
//        } else {
//            return 0;
//        }
//    }

//    public int scoreInstitutionalPositioning(double netPositions) {
//        if (netPositions > 0) {
//            return 1;
//        } else if (netPositions < 0) {
//            return -1;
//        } else {
//            return 0;
//        }
//    }

    public String getOverallBiasLabel(int totalScore) {
        if (totalScore >= 10) {
            return "STRONGLY BULLISH";
        } else if (totalScore >= 4) {
            return "BULLISH";
        } else if (totalScore >= -3) {
            return "NEUTRAL";
        } else if (totalScore >= -9) {
            return "BEARISH";
        } else {
            return "STRONGLY BEARISH";
        }
//        if (totalScore > 9) return "STRONGLY BULLISH";
//        if (totalScore >= 4) return "BULLISH";
//        if (totalScore == 0) return "NEUTRAL";
//        if (totalScore <= -4) return "BEARISH";
//        if (totalScore < -9) return "STRONGLY BEARISH";
//        return "UNKNOWN";
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

    // Evaluates absolute yield levels. High yields attract foreign capital (+1).
    public int scoreAbsoluteYield(double currentYield, double baselineThreshold) {
        if (currentYield >= baselineThreshold) {
            return 1; // Hawkish / Attractive to foreign capital
        } else if (currentYield <= baselineThreshold - 0.75) {
            return -1; // Dovish / Capital flights to higher-yielding currencies
        }
        return 0; // Neutral zone
    }

    // Evaluates the 2s10s spread. An inverted curve (< 0) signals recession fears, triggering USD safe-haven buying.
    public int scoreYieldCurve(double yieldCurve) {
        if (yieldCurve <= -0.10) {
            return 1; // Deeply inverted (Panic buying USD)
        } else if (yieldCurve >= 0.20) {
            return -1; // Normal steepening (Risk-on environment, capital leaves USD)
        }
        return 0; // Flat/Neutral
    }
}