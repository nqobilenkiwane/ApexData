package com.uniforex.apexdata.service;

import com.uniforex.apexdata.model.MarketMetric;
import com.uniforex.apexdata.model.MetricCategory;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class DatabaseManager {

    private final String url;
    private final String user;
    private final String password;

    public DatabaseManager(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS calendar_events (
                metric_name VARCHAR(100) PRIMARY KEY,
                actual_value DOUBLE PRECISION,
                estimate_value DOUBLE PRECISION,
                category VARCHAR(50),
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """;

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("Database connection established and schema verified.");
        } catch (SQLException e) {
            System.err.println("Database Initialization Failed: " + e.getMessage());
        }
    }

    // Upsert (Insert or Update) a metric into the database
    public void saveMetric(MarketMetric metric) {
        String upsertSQL = """
            INSERT INTO calendar_events (metric_name, actual_value, estimate_value, category)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (metric_name) 
            DO UPDATE SET 
                actual_value = EXCLUDED.actual_value,
                estimate_value = EXCLUDED.estimate_value,
                last_updated = CURRENT_TIMESTAMP;
        """;

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement pstmt = conn.prepareStatement(upsertSQL)) {

            pstmt.setString(1, metric.name());
            pstmt.setDouble(2, metric.actualValue());
            pstmt.setDouble(3, metric.forecastValue());
            pstmt.setString(4, metric.category().name());
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Failed to save metric " + metric.name() + ": " + e.getMessage());
        }
    }

    // Load all historical metrics from the database to fill in the gaps
    public Map<String, MarketMetric> loadHistoricalMetrics() {
        Map<String, MarketMetric> historicalData = new HashMap<>();
        String selectSQL = "SELECT metric_name, actual_value, estimate_value, category FROM calendar_events";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {

            while (rs.next()) {
                String name = rs.getString("metric_name");
                double actual = rs.getDouble("actual_value");
                double estimate = rs.getDouble("estimate_value");
                MetricCategory category = MetricCategory.valueOf(rs.getString("category"));

                historicalData.put(name, new MarketMetric(name, actual, estimate, 0, category));
            }
        } catch (SQLException e) {
            System.err.println("Failed to load historical metrics: " + e.getMessage());
        }
        return historicalData;
    }
}