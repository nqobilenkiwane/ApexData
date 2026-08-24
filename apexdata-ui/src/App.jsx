import { useState, useEffect } from 'react'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'

function App() {
  const [summary, setSummary] = useState(null)
  const [history, setHistory] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // Fetch both endpoints at the same time
    Promise.all([
      fetch('http://localhost:8080/api/v1/dashboard/summary').then(res => res.json()),
      fetch('http://localhost:8080/api/v1/dashboard/history').then(res => res.json())
    ])
      .then(([summaryData, historyData]) => {
        setSummary(summaryData)

        // Format the timestamp for the chart before setting it to state
        const formattedHistory = historyData.map(item => ({
          ...item,
          displayTime: new Date(item.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        }))
        setHistory(formattedHistory)

        setLoading(false)
      })
      .catch(error => {
        console.error("Error fetching data:", error)
        setLoading(false)
      })
  }, [])

  if (loading) return <div style={styles.loading}>Initializing ApexData Engine...</div>
  if (!summary) return <div style={styles.loading}>Failed to load market data.</div>

  const getScoreColor = (score) => {
    if (score > 0) return '#00ff88';
    if (score < 0) return '#ff3366';
    return '#888888';
  };

  const formatCategory = (cat) => {
    return cat.replace('_', ' ').replace(/\b\w/g, l => l.toUpperCase());
  }

  // Custom tooltip for the Recharts component
  const CustomTooltip = ({ active, payload, label }) => {
    if (active && payload && payload.length) {
      return (
        <div style={styles.tooltip}>
          <p style={styles.tooltipLabel}>{label}</p>
          <p style={{ color: '#FFFFFF', fontWeight: 'bold' }}>
            Score: <span style={{ color: getScoreColor(payload[0].value) }}>{payload[0].value}</span>
          </p>
          <p style={{ color: '#888888', fontSize: '0.8rem', marginTop: '4px' }}>
            Bias: {payload[0].payload.biasLabel}
          </p>
        </div>
      );
    }
    return null;
  };

  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <h1 style={styles.title}>APEX DATA DASHBOARD</h1>
        <div style={styles.biasContainer}>
          <div style={styles.scoreBox}>
            <span style={styles.scoreLabel}>USD COMPOSITE SCORE</span>
            <span style={{...styles.scoreValue, color: getScoreColor(summary.totalScore)}}>
              {summary.totalScore > 0 ? '+' : ''}{summary.totalScore}
            </span>
          </div>
          <div style={styles.biasBox}>
            <span style={styles.scoreLabel}>MARKET BIAS</span>
            <span style={{...styles.biasValue, color: getScoreColor(summary.totalScore)}}>
              {summary.overallBias}
            </span>
          </div>
        </div>
      </header>

      {/* 3. HISTORICAL TREND CHART */}
      {history.length > 0 && (
        <div style={styles.chartSection}>
          <div style={styles.cardHeader}>
            <h2 style={styles.cardTitle}>USD SCORE TREND</h2>
          </div>
          <div style={styles.chartWrapper}>
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={history} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#222222" vertical={false} />
                <XAxis dataKey="displayTime" stroke="#888888" tick={{ fill: '#888888', fontSize: 12 }} tickMargin={10} />
                <YAxis stroke="#888888" tick={{ fill: '#888888', fontSize: 12 }} />
                <Tooltip content={<CustomTooltip />} />
                <Line
                  type="monotone"
                  dataKey="totalScore"
                  stroke="#2563EB"
                  strokeWidth={3}
                  dot={{ r: 4, fill: '#2563EB', strokeWidth: 0 }}
                  activeDot={{ r: 6, fill: '#FFFFFF', stroke: '#2563EB', strokeWidth: 2 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* 4. METRICS GRID */}
      <div style={styles.grid}>
        {Object.entries(summary.categoryScores).map(([category, catScore]) => (
          <div key={category} style={styles.card}>
            <div style={styles.cardHeader}>
              <h2 style={styles.cardTitle}>{formatCategory(category)}</h2>
              <span style={{...styles.catScoreBadge, color: getScoreColor(catScore)}}>
                {catScore > 0 ? '+' : ''}{catScore}
              </span>
            </div>

            <div style={styles.metricList}>
              {summary.metrics
                .filter(m => m.category === category)
                .map(metric => (
                  <div key={metric.name} style={styles.metricRow}>
                    <div style={styles.metricName}>{metric.name}</div>
                    <div style={styles.metricValues}>
                      <span style={styles.actual}>
                        Act: {Number(metric.actualValue).toFixed(2)}
                      </span>
                      {metric.forecastValue !== 0 && (
                        <span style={styles.estimate}>
                          Est: {Number(metric.forecastValue).toFixed(2)}
                        </span>
                      )}
                    </div>
                    <div style={{...styles.metricScore, color: getScoreColor(metric.scoreDelta)}}>
                      {metric.scoreDelta > 0 ? '+' : ''}{metric.scoreDelta}
                    </div>
                  </div>
                ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

const styles = {
  container: { backgroundColor: '#000000', minHeight: '100vh', color: '#FFFFFF', fontFamily: "'Inter', 'Segoe UI', sans-serif", padding: '40px 20px' },
  loading: { backgroundColor: '#000000', height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#2563EB', fontSize: '24px', fontFamily: 'monospace' },
  header: { maxWidth: '1200px', margin: '0 auto 40px', textAlign: 'center' },
  title: { color: '#FFFFFF', letterSpacing: '2px', fontSize: '2.5rem', marginBottom: '30px' },
  biasContainer: { display: 'flex', justifyContent: 'center', gap: '20px' },

  scoreBox: { backgroundColor: '#111111', padding: '20px 40px', borderRadius: '8px', display: 'flex', flexDirection: 'column', alignItems: 'center', border: '1px solid #222222' },
  biasBox: { backgroundColor: '#111111', padding: '20px 60px', borderRadius: '8px', display: 'flex', flexDirection: 'column', alignItems: 'center', border: '1px solid #222222' },
  scoreLabel: { fontSize: '0.85rem', color: '#888888', letterSpacing: '1px', marginBottom: '10px' },
  scoreValue: { fontSize: '3rem', fontWeight: 'bold' },
  biasValue: { fontSize: '2.5rem', fontWeight: 'bold' },

  chartSection: { maxWidth: '1400px', margin: '0 auto 40px', backgroundColor: '#111111', borderRadius: '8px', padding: '25px', border: '1px solid #222222' },
  chartWrapper: { height: '300px', width: '100%', marginTop: '20px' },

  tooltip: { backgroundColor: '#000000', padding: '15px', border: '1px solid #222222', borderRadius: '6px', boxShadow: '0 4px 12px rgba(0,0,0,0.5)' },
  tooltipLabel: { margin: '0 0 8px 0', color: '#888888', fontSize: '0.9rem', borderBottom: '1px solid #222222', paddingBottom: '4px' },

  grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(450px, 1fr))', gap: '25px', maxWidth: '1400px', margin: '0 auto' },
  card: { backgroundColor: '#111111', borderRadius: '8px', padding: '25px', border: '1px solid #222222' },

  cardHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '2px solid #2563EB', paddingBottom: '15px', marginBottom: '15px' },
  cardTitle: { margin: 0, fontSize: '1.2rem', color: '#FFFFFF', letterSpacing: '1px' },
  catScoreBadge: { fontSize: '1.2rem', fontWeight: 'bold', backgroundColor: 'rgba(255,255,255,0.05)', padding: '5px 12px', borderRadius: '4px' },

  metricList: { display: 'flex', flexDirection: 'column', gap: '12px' },
  metricRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#000000', padding: '12px 15px', borderRadius: '6px', fontSize: '0.9rem', border: '1px solid #1A1A1A' },
  metricName: { flex: '1', color: '#CCCCCC', fontWeight: '500' },
  metricValues: { flex: '1', display: 'flex', flexDirection: 'column', alignItems: 'flex-end', marginRight: '20px' },
  actual: { color: '#FFFFFF', fontWeight: '600' },
  estimate: { color: '#888888', fontSize: '0.8rem', marginTop: '3px' },
  metricScore: { fontWeight: 'bold', fontSize: '1.1rem', minWidth: '35px', textAlign: 'right' }
};

export default App;