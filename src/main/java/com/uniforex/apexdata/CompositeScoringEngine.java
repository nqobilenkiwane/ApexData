package com.uniforex.apexdata;

import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CompositeScoringEngine {

    /**
     * Iterates through raw metrics, applies mathematical scoring logic,
     * and returns an immutable list of scored metrics.
     */
    public List<MarketMetric> applyScores(List<MarketMetric> rawMetrics) {
        List<MarketMetric> scoredMetrics = new ArrayList<>();

        for (MarketMetric m : rawMetrics) {
            int score = 0;

            if (m.forecastValue() != 0.0) {
                // PRIORITY: Score surprise factor if an estimate/forecast exists
                score = scoreSurprise(m);
            } else {
                // FALLBACK: Absolute threshold scoring for metrics without estimates
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
                    case "YoY Inflation":
                        score = m.actualValue() > 2.0 ? 1 : -1;
                        break;
                    case "10Y Real Yield":
                        score = m.actualValue() > 0 ? 1 : -1;
                        break;
                    case "2s10s Yield Curve":
                        score = scoreYieldCurve(m.actualValue());
                        break;
                    case "COT Net Positioning":
                        score = m.actualValue() > 0 ? 1 : (m.actualValue() < 0 ? -1 : 0);
                        break;
                    case "COT Long Percentage":
                        double pct = m.actualValue();
                        if (pct >= 80) score = -1;       // Bearish (Overcrowded Longs)
                        else if (pct <= 20) score = 1;   // Bullish (Short Squeeze Risk)
                        else if (pct >= 55) score = 1;   // Bullish (Healthy Trend)
                        else if (pct <= 45) score = -1;  // Bearish (Healthy Short)
                        else score = 0;                  // Neutral
                        break;
                    default:
                        score = 0;
                }
            }
            scoredMetrics.add(new MarketMetric(m.name(), m.actualValue(), m.forecastValue(), score, m.category()));
        }
        return scoredMetrics;
    }

    /**
     * Calculates the Surprise Factor (Actual - Forecast) and assigns a directional score.
     */
    public int scoreSurprise(MarketMetric metric) {
        double surprise = metric.actualValue() - metric.forecastValue();
        double epsilon = 0.0001;

        if (Math.abs(surprise) < epsilon) {
            return 0; // Neutral (Met expectations)
        }

        // Inverse indicators: Higher than forecast is bearish for currency/growth
        boolean isInverse = metric.name().equalsIgnoreCase("Unemployment Rate")
                || metric.name().contains("Jobless Claims");

        if (isInverse) {
            return surprise > 0 ? -1 : 1;
        } else {
            return surprise > 0 ? 1 : -1;
        }
    }

    /**
     * Groups metrics by Category and calculates category-level sub-scores.
     */
    public Map<MetricCategory, Integer> calculateCategoryScores(List<MarketMetric> scoredMetrics) {
        return scoredMetrics.stream()
                .collect(Collectors.groupingBy(
                        MarketMetric::category,
                        Collectors.summingInt(MarketMetric::scoreDelta)
                ));
    }

    /**
     * Calculates the overall total score across all metrics in the list.
     */
    public int calculateTotalScore(List<MarketMetric> scoredMetrics) {
        return scoredMetrics.stream().mapToInt(MarketMetric::scoreDelta).sum();
    }

    // ========================================================================
    // DECOUPLED MACRO & CROSS-ASSET LOGIC
    // ========================================================================

    /**
     * Isolates macroeconomic fundamentals (Growth, Jobs, Inflation, Yields)
     * by excluding asset-specific Technical and COT categories.
     */
    public int calculateMacroSubtotal(List<MarketMetric> scoredMetrics) {
        return scoredMetrics.stream()
                .filter(m -> isMacroCategory(m.category()))
                .mapToInt(MarketMetric::scoreDelta)
                .sum();
    }

    /**
     * Filters out asset-specific technical and sentiment categories.
     */
    public boolean isMacroCategory(MetricCategory category) {
        if (category == null) return false;
        String name = category.name().toUpperCase();
        return !name.contains("TECHNICAL") && !name.contains("COT") && !name.contains("SENTIMENT");
    }

    /**
     * Calculates a dedicated XAUUSD composite score using the inverted USD
     * macroeconomic baseline combined with Gold-specific COT and technical overlays.
     *
     * Formula: (USD_Macro_Subtotal * -1) + Gold_COT_Score + Gold_Technical_Score
     */
    public int calculateGoldCompositeScore(int usdMacroSubtotal, int goldCotScore, int goldTechScore) {
        int invertedMacro = usdMacroSubtotal * -1;
        return invertedMacro + goldCotScore + goldTechScore;
    }

    /**
     * Evaluates Gold CFTC Commitment of Traders (Non-Commercial Speculator positioning).
     */
    public int scoreGoldCot(long nonCommercialLongs, long nonCommercialShorts, long previousNetPosition) {
        long currentNet = nonCommercialLongs - nonCommercialShorts;

        // Hedge funds aggressively expanding net longs
        if (currentNet > previousNetPosition && currentNet > 0) {
            return 1;
        }
        // Hedge funds aggressively expanding net shorts
        else if (currentNet < previousNetPosition && currentNet < 0) {
            return -1;
        }
        return 0;
    }

    /**
     * Evaluates standalone technical momentum for an asset.
     */
    public int scoreTechnicals(double currentPrice, double sma200, double rsi14) {
        boolean isUptrend = currentPrice > sma200;
        boolean isBullishMomentum = rsi14 > 50.0 && rsi14 < 70.0;
        boolean isBearishMomentum = rsi14 < 50.0 && rsi14 > 30.0;

        if (isUptrend && isBullishMomentum) {
            return 1;
        } else if (!isUptrend && isBearishMomentum) {
            return -1;
        }
        return 0;
    }

    // ========================================================================
    // BIAS LABELS & ABSOLUTE THRESHOLDS
    // ========================================================================

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
    }

    public int scoreLaborMarket(double unemploymentRate) {
        if (unemploymentRate < 4.0) {
            return 1;
        } else if (unemploymentRate > 4.5) {
            return -1;
        }
        return 0;
    }

    public int scoreNfp(double nfpChange) {
        if (nfpChange > 150.0) {
            return 1;
        } else if (nfpChange < 100.0) {
            return -1;
        }
        return 0;
    }

    public int scoreGdp(double gdp) {
        if (gdp > 2.5) {
            return 1;
        } else if (gdp < 1.0) {
            return -1;
        }
        return 0;
    }

    public int scoreRetailSales(double momRetailSales) {
        if (momRetailSales > 0.1) {
            return 1;
        } else if (momRetailSales < -0.1) {
            return -1;
        }
        return 0;
    }

    public int scoreAbsoluteYield(double currentYield, double baselineThreshold) {
        if (currentYield >= baselineThreshold) {
            return 1;
        } else if (currentYield <= baselineThreshold - 0.75) {
            return -1;
        }
        return 0;
    }

    public int scoreYieldCurve(double yieldCurve) {
        if (yieldCurve <= -0.10) {
            return 1;  // Inverted curve (Recession risk / USD safe-haven demand)
        } else if (yieldCurve >= 0.20) {
            return -1; // Normal curve steepening
        }
        return 0;
    }
}