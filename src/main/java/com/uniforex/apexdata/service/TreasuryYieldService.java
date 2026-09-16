package com.uniforex.apexdata.service;

import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

@Service
public class TreasuryYieldService {

    private final HttpClient httpClient;

    public TreasuryYieldService() {
        this.httpClient = HttpClient.newHttpClient();
    }

    public List<MarketMetric> fetchLatestYields() throws Exception {
        // Dynamically insert the current year so the URL rolls over automatically
        int currentYear = Year.now().getValue();
        String url = "https://home.treasury.gov/resource-center/data-chart-center/interest-rates/pages/xml?data=daily_treasury_yield_curve&field_tdr_date_value=" + currentYear;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Parse the raw XML string
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(response.body().getBytes()));

        // Extract the 2-Year and 10-Year nodes
        NodeList twoYearNodes = doc.getElementsByTagName("d:BC_2YEAR");
        NodeList tenYearNodes = doc.getElementsByTagName("d:BC_10YEAR");

        if (twoYearNodes.getLength() == 0 || tenYearNodes.getLength() == 0) {
            throw new RuntimeException("Could not locate yield data in Treasury XML feed.");
        }

        // The feed appends new days to the bottom, so the last element is the most recent market close
        int lastIndex = twoYearNodes.getLength() - 1;
        double twoYearYield = Double.parseDouble(twoYearNodes.item(lastIndex).getTextContent());
        double tenYearYield = Double.parseDouble(tenYearNodes.item(lastIndex).getTextContent());

        // Calculate the yield curve spread
        double yieldSpread = tenYearYield - twoYearYield;

        List<MarketMetric> metrics = new ArrayList<>();
        metrics.add(new MarketMetric("2Y Treasury Yield", twoYearYield, 0.0, 0, MetricCategory.CAPITAL_FLOWS));
        metrics.add(new MarketMetric("10Y Treasury Yield", tenYearYield, 0.0, 0, MetricCategory.CAPITAL_FLOWS));
        metrics.add(new MarketMetric("10s2s Yield Curve Spread", yieldSpread, 0.0, 0, MetricCategory.CAPITAL_FLOWS));

        return metrics;
    }
}