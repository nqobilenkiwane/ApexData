import { useState, useEffect } from 'react'

function App() {
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetch('http://localhost:8080/api/v1/dashboard/summary')
      .then(response => response.json())
      .then(data => {
        setSummary(data)
        setLoading(false)
      })
      .catch(error => {
        console.error("Error fetching dashboard:", error)
        setLoading(false)
      })
  }, [])

  if (loading) return <div style={styles.loading}>Initializing ApexData Engine...</div>
  if (!summary) return <div style={styles.loading}>Failed to load market data.</div>

  // Helper to color-code scores
  const getScoreColor = (score) => {
    if (score > 0) return '#00ff88'; // Neon Green
    if (score < 0) return '#ff3366'; // Neon Red
    return '#8892b0'; // Muted Gray
  };

  // Helper to format category names
  const formatCategory = (cat) => {
    return cat.replace('_', ' ').replace(/\b\w/g, l => l.toUpperCase());
  }

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
  container: { backgroundColor: '#0a192f', minHeight: '100vh', color: '#ccd6f6', fontFamily: "'Inter', 'Segoe UI', sans-serif", padding: '40px 20px' },
  loading: { backgroundColor: '#0a192f', height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#64ffda', fontSize: '24px', fontFamily: 'monospace' },
  header: { maxWidth: '1200px', margin: '0 auto 40px', textAlign: 'center' },
  title: { color: '#e6f1ff', letterSpacing: '2px', fontSize: '2.5rem', marginBottom: '30px' },
  biasContainer: { display: 'flex', justifyContent: 'center', gap: '20px' },
  scoreBox: { backgroundColor: '#112240', padding: '20px 40px', borderRadius: '8px', display: 'flex', flexDirection: 'column', alignItems: 'center', boxShadow: '0 10px 30px -15px rgba(2,12,27,0.7)' },
  biasBox: { backgroundColor: '#112240', padding: '20px 60px', borderRadius: '8px', display: 'flex', flexDirection: 'column', alignItems: 'center', boxShadow: '0 10px 30px -15px rgba(2,12,27,0.7)' },
  scoreLabel: { fontSize: '0.85rem', color: '#8892b0', letterSpacing: '1px', marginBottom: '10px' },
  scoreValue: { fontSize: '3rem', fontWeight: 'bold' },
  biasValue: { fontSize: '2.5rem', fontWeight: 'bold' },
  grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(450px, 1fr))', gap: '25px', maxWidth: '1400px', margin: '0 auto' },
  card: { backgroundColor: '#112240', borderRadius: '8px', padding: '25px', boxShadow: '0 10px 30px -15px rgba(2,12,27,0.7)' },
  cardHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #233554', paddingBottom: '15px', marginBottom: '15px' },
  cardTitle: { margin: 0, fontSize: '1.2rem', color: '#e6f1ff', letterSpacing: '1px' },
  catScoreBadge: { fontSize: '1.2rem', fontWeight: 'bold', backgroundColor: 'rgba(255,255,255,0.05)', padding: '5px 12px', borderRadius: '4px' },
  metricList: { display: 'flex', flexDirection: 'column', gap: '12px' },
  metricRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#0a192f', padding: '12px 15px', borderRadius: '6px', fontSize: '0.9rem' },
  metricName: { flex: '1', color: '#a8b2d1', fontWeight: '500' },
  metricValues: { flex: '1', display: 'flex', flexDirection: 'column', alignItems: 'flex-end', marginRight: '20px' },
  actual: { color: '#e6f1ff', fontWeight: '600' },
  estimate: { color: '#8892b0', fontSize: '0.8rem', marginTop: '3px' },
  metricScore: { fontWeight: 'bold', fontSize: '1.1rem', minWidth: '35px', textAlign: 'right' }
};

export default App;