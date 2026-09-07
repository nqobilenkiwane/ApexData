import { useState, useEffect } from 'react'
import { BarChart, Bar, ReferenceLine, ReferenceArea, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import LogoDark from './LogoDark'

function App() {
  const [summary, setSummary] = useState(null)
  const [history, setHistory] = useState([])
  const [goldSummary, setGoldSummary] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const baseUrl = 'https://apexdata-production-dc24.up.railway.app';

    Promise.all([
      fetch(`${baseUrl}/api/dashboard/summary`).then(res => {
        if (!res.ok) throw new Error(`Summary API failed with status ${res.status}`);
        return res.json();
      }),
      fetch(`${baseUrl}/api/dashboard/history`).then(res => {
        if (!res.ok) throw new Error(`History API failed with status ${res.status}`);
        return res.json();
      }),
      fetch(`${baseUrl}/api/dashboard/gold`).then(res => {
        // Soft fail for Gold if the engine hasn't completed its first run yet
        if (!res.ok) return null;
        return res.json();
      }).catch(() => null)
    ])
      .then(([summaryData, historyData, goldData]) => {
        setSummary(summaryData);
        setGoldSummary(goldData);

        if (Array.isArray(historyData)) {
          // Filter to only show USD scores in the trend chart for now
          const usdHistory = historyData.filter(item => item.currency === 'USD' || !item.currency);
          const formattedHistory = usdHistory.map(item => ({
            ...item,
            displayDate: new Date(item.timestamp).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
          }));
          setHistory(formattedHistory);
        }
        setLoading(false);
      })
      .catch(error => {
        console.error("Error fetching data:", error);
        setSummary(null);
        setLoading(false);
      });
  }, []);

  if (loading) return <div style={styles.loading}>Initializing ApexData Engine...</div>
  if (!summary) return <div style={styles.loading}>Failed to load market data.</div>

  const getCompositeScoreColor = (score) => {
    if (score >= 4) return '#00ff88';
    if (score <= -4) return '#ff3366';
    return '#888888';
  };

  const getMetricScoreColor = (score) => {
    if (score > 0) return '#00ff88';
    if (score < 0) return '#ff3366';
    return '#888888';
  };

  const getActualValueColor = (scoreDelta) => {
    if (scoreDelta > 0) return '#00ff88';
    if (scoreDelta < 0) return '#ff3366';
    return '#FFFFFF';
  };

  const formatCategory = (cat) => {
    return cat.replace('_', ' ').replace(/\b\w/g, l => l.toUpperCase());
  }

  const CustomTooltip = ({ active, payload, label }) => {
    if (active && payload && payload.length) {
      return (
        <div style={styles.tooltip}>
          <p style={styles.tooltipLabel}>{label}</p>
          <p style={{ color: '#FFFFFF', fontWeight: 'bold' }}>
            Score: <span style={{ color: getCompositeScoreColor(payload[0].value) }}>{payload[0].value}</span>
          </p>
          <p style={{ color: '#888888', fontSize: '0.8rem', marginTop: '4px' }}>
            Bias: {payload[0].payload.biasLabel || payload[0].payload.bias_label || 'Neutral'}
          </p>
        </div>
      );
    }
    return null;
  };

  const CATEGORY_ORDER = [
      'ECONOMIC_GROWTH',
      'JOB_MARKET',
      'INFLATION',
      'INSTITUTIONAL_ACTIVITY',
      'CAPITAL_FLOWS',
      'TECHNICALS'
  ];

  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '30px' }}>
          <LogoDark width={350} height={120} />
        </div>

        {/* MULTI-ASSET DASHBOARD HEADER */}
        <div style={styles.assetContainer}>

          {/* USD MACRO CARD */}
          <div style={styles.assetCard}>
            <div style={styles.assetHeader}>
              <span style={styles.assetTitle}>US DOLLAR (DXY)</span>
              <span style={{...styles.assetScore, color: getCompositeScoreColor(summary.totalScore)}}>
                {summary.totalScore > 0 ? '+' : ''}{summary.totalScore}
              </span>
            </div>
            <div style={{textAlign: 'center', margin: '15px 0'}}>
              <span style={styles.scoreLabel}>MACRO BIAS</span>
              <div style={{...styles.biasValue, color: getCompositeScoreColor(summary.totalScore)}}>
                {summary.overallBias}
              </div>
            </div>
          </div>

          {/* GOLD (XAUUSD) CARD */}
          {goldSummary && (
            <div style={styles.assetCard}>
              <div style={styles.assetHeader}>
                <span style={styles.assetTitle}>GOLD (XAUUSD)</span>
                <span style={{...styles.assetScore, color: getCompositeScoreColor(goldSummary.totalScore)}}>
                  {goldSummary.totalScore > 0 ? '+' : ''}{goldSummary.totalScore}
                </span>
              </div>
              <div style={{textAlign: 'center', margin: '10px 0 20px 0'}}>
                <span style={styles.scoreLabel}>COMPOSITE BIAS</span>
                <div style={{...styles.biasValue, fontSize: '1.8rem', color: getCompositeScoreColor(goldSummary.totalScore)}}>
                  {goldSummary.biasLabel}
                </div>
              </div>

              {/* Gold Sub-Component Breakdown */}
              <div style={styles.breakdownContainer}>
                <div style={styles.breakdownRow}>
                  <span style={styles.breakdownLabel}>USD Macro Inversion</span>
                  <span style={{...styles.breakdownScore, color: getMetricScoreColor(goldSummary.invertedMacroBaseline)}}>
                    {goldSummary.invertedMacroBaseline > 0 ? '+' : ''}{goldSummary.invertedMacroBaseline}
                  </span>
                </div>
                <div style={styles.breakdownRow}>
                  <span style={styles.breakdownLabel}>COT Sentiment</span>
                  <span style={{...styles.breakdownScore, color: getMetricScoreColor(goldSummary.cotScore)}}>
                    {goldSummary.cotScore > 0 ? '+' : ''}{goldSummary.cotScore}
                  </span>
                </div>
                <div style={styles.breakdownRow}>
                  <span style={styles.breakdownLabel}>Technical Momentum</span>
                  <span style={{...styles.breakdownScore, color: getMetricScoreColor(goldSummary.technicalScore)}}>
                    {goldSummary.technicalScore > 0 ? '+' : ''}{goldSummary.technicalScore}
                  </span>
                </div>
              </div>
            </div>
          )}
        </div>
      </header>

      {/* HISTORICAL TREND CHART */}
      {history.length > 0 && (
        <div style={styles.chartSection}>
          <div style={styles.cardHeader}>
            <h2 style={styles.cardTitle}>USD MACRO TREND</h2>
          </div>
          <div style={styles.chartWrapper}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={history} margin={{ top: 10, right: 10, left: -20, bottom: 0 }} barCategoryGap="10%">
                <CartesianGrid strokeDasharray="3 3" stroke="#222222" vertical={false} />
                <XAxis dataKey="displayDate" stroke="#888888" tick={{ fill: '#888888', fontSize: 12 }} tickMargin={10} />
                <YAxis stroke="#888888" tick={{ fill: '#888888', fontSize: 12 }} domain={[-22, 22]} />
                <Tooltip content={<CustomTooltip />} cursor={{fill: '#1a1a1a'}} />
                <ReferenceArea y1={10} y2={22} fill="#00ff88" fillOpacity={0.15} />
                <ReferenceArea y1={4} y2={9.99} fill="#00ff88" fillOpacity={0.05} />
                <ReferenceArea y1={-4} y2={-9.99} fill="#ff3366" fillOpacity={0.05} />
                <ReferenceArea y1={-10} y2={-22} fill="#ff3366" fillOpacity={0.15} />
                <ReferenceLine y={0} stroke="#444444" strokeWidth={2} />
                <Bar dataKey="totalScore" fill="#E2E8F0" radius={[2, 2, 2, 2]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* METRICS GRID */}
      <div style={styles.grid}>
        {CATEGORY_ORDER.map((category) => {
          const catScore = summary.categoryScores[category];
          if (catScore === undefined) return null;

          return (
            <div key={category} style={styles.card}>
              <div style={styles.cardHeader}>
                <h2 style={styles.cardTitle}>{formatCategory(category)}</h2>
                <span style={{...styles.catScoreBadge, color: getMetricScoreColor(catScore)}}>
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
                        <span style={{ ...styles.actual, color: getActualValueColor(metric.scoreDelta) }}>
                          Act: {Number(metric.actualValue).toFixed(2)}
                        </span>
                        {metric.forecastValue !== 0 && (
                          <span style={styles.estimate}>
                            Est: {Number(metric.forecastValue).toFixed(2)}
                          </span>
                        )}
                      </div>
                      <div style={{...styles.metricScore, color: getMetricScoreColor(metric.scoreDelta)}}>
                        {metric.scoreDelta > 0 ? '+' : ''}{metric.scoreDelta}
                      </div>
                    </div>
                  ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  )
}

const styles = {
  container: { backgroundColor: '#000000', minHeight: '100vh', color: '#FFFFFF', fontFamily: "'Inter', 'Segoe UI', sans-serif", padding: '40px 20px' },
  loading: { backgroundColor: '#000000', height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#2563EB', fontSize: '24px', fontFamily: 'monospace' },
  header: { maxWidth: '1400px', margin: '0 auto 40px' },

  // Multi-Asset Header Styles
  assetContainer: { display: 'flex', justifyContent: 'center', gap: '25px', flexWrap: 'wrap' },
  assetCard: { backgroundColor: '#111111', padding: '25px', borderRadius: '8px', border: '1px solid #222222', flex: '1', minWidth: '320px', maxWidth: '450px', display: 'flex', flexDirection: 'column' },
  assetHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #222222', paddingBottom: '15px' },
  assetTitle: { fontSize: '1.2rem', color: '#FFFFFF', letterSpacing: '2px', fontWeight: 'bold' },
  assetScore: { fontSize: '2rem', fontWeight: 'bold' },

  breakdownContainer: { display: 'flex', flexDirection: 'column', gap: '8px', marginTop: 'auto', backgroundColor: '#000000', padding: '15px', borderRadius: '6px', border: '1px solid #1A1A1A' },
  breakdownRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.9rem' },
  breakdownLabel: { color: '#888888' },
  breakdownScore: { fontWeight: 'bold', fontSize: '1rem' },

  scoreLabel: { fontSize: '0.85rem', color: '#888888', letterSpacing: '1px', marginBottom: '5px', display: 'block' },
  biasValue: { fontSize: '2.5rem', fontWeight: 'bold', textTransform: 'uppercase' },

  chartSection: { maxWidth: '1400px', margin: '0 auto 40px', backgroundColor: '#111111', borderRadius: '8px', padding: '25px', border: '1px solid #222222' },
  chartWrapper: { height: '350px', width: '100%', marginTop: '20px' },

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