import { useState, useEffect } from 'react'
import { BarChart, Bar, ReferenceLine, ReferenceArea, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import LogoDark from './LogoDark'

function App() {
  const [summary, setSummary] = useState(null)
  const [history, setHistory] = useState([])
  const [goldSummary, setGoldSummary] = useState(null)
  const [loading, setLoading] = useState(true)

  // New state to manage the dropdown selection
  const [activeAsset, setActiveAsset] = useState('DXY')

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
        if (!res.ok) return null;
        return res.json();
      }).catch(() => null)
    ])
      .then(([summaryData, historyData, goldData]) => {
        setSummary(summaryData);
        setGoldSummary(goldData);

        if (Array.isArray(historyData)) {
          const formattedHistory = historyData.map(item => ({
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

  // Dynamic values for the Active Summary Card based on dropdown selection
  const isGold = activeAsset === 'XAUUSD';
  const activeSummaryData = isGold ? goldSummary : summary;
  const activeScore = activeSummaryData?.totalScore ?? 0;
  const activeBias = activeSummaryData?.biasLabel || activeSummaryData?.overallBias || 'NEUTRAL';

  // Construct the 3 pillars based on the selected asset
  let activePillars = [];
  if (isGold && goldSummary) {
    activePillars = [
      { name: 'USD Macro Inversion', score: goldSummary.invertedMacroBaseline },
      { name: 'COT Sentiment', score: goldSummary.cotScore },
      { name: 'Technical Momentum', score: goldSummary.technicalScore }
    ];
  } else if (!isGold && summary) {
    activePillars = [
      { name: 'Economic Health', score: (summary.categoryScores.ECONOMIC_GROWTH || 0) + (summary.categoryScores.JOB_MARKET || 0) + (summary.categoryScores.INFLATION || 0) },
      { name: 'Positioning & Flows', score: (summary.categoryScores.CAPITAL_FLOWS || 0) + (summary.categoryScores.INSTITUTIONAL_ACTIVITY || 0) },
      { name: 'Technical Momentum', score: (summary.categoryScores.TECHNICALS || 0) }
    ];
  }

  const activeHistory = history.filter(item =>
    isGold ? item.currency === 'XAU' : (item.currency === 'USD' || !item.currency)
  );

  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '30px' }}>
          <LogoDark width={350} height={120} />
        </div>

        {/* ACTIVE SUMMARY CARD WITH DROPDOWN */}
        <div style={styles.summaryCard}>
          <div style={styles.summaryHeader}>
            <select
              value={activeAsset}
              onChange={(e) => setActiveAsset(e.target.value)}
              style={styles.dropdown}
            >
              <option value="DXY" style={{ backgroundColor: '#111111' }}>US DOLLAR (DXY)</option>
              {goldSummary && <option value="XAUUSD" style={{ backgroundColor: '#111111' }}>GOLD (XAUUSD)</option>}
            </select>

            <span style={{ fontSize: '2.5rem', fontWeight: '900', color: getCompositeScoreColor(activeScore) }}>
              {activeScore > 0 ? `+${activeScore}` : activeScore}
            </span>
          </div>

          <div style={styles.summaryBiasContainer}>
            <span style={styles.summaryBiasLabel}>COMPOSITE BIAS</span>
            <div style={{ ...styles.summaryBiasValue, color: getCompositeScoreColor(activeScore) }}>
              {activeBias}
            </div>
          </div>

          <div style={styles.pillarBox}>
            {activePillars.map((pillar) => (
              <div key={pillar.name} style={styles.pillarRow}>
                <span style={styles.pillarName}>{pillar.name}</span>
                <span style={{ ...styles.pillarScore, color: getMetricScoreColor(pillar.score) }}>
                  {pillar.score > 0 ? `+${pillar.score}` : pillar.score}
                </span>
              </div>
            ))}
          </div>
        </div>
      </header>

      {/* HISTORICAL TREND CHART */}
      {activeHistory.length > 0 && (
        <div style={styles.chartSection}>
          <div style={styles.cardHeader}>
            <h2 style={styles.cardTitle}>{isGold ? 'GOLD MACRO TREND' : 'USD MACRO TREND'}</h2>
          </div>
          <div style={styles.chartWrapper}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={activeHistory} margin={{ top: 10, right: 10, left: -20, bottom: 0 }} barCategoryGap="10%">
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

      {/* METRICS GRID - Hidden for Gold until specific Gold metrics are added */}
      {!isGold && (
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
      )}
    </div>
  )
}

const styles = {
  container: { backgroundColor: '#000000', minHeight: '100vh', color: '#FFFFFF', fontFamily: "'Inter', 'Segoe UI', sans-serif", padding: '40px 20px' },
  loading: { backgroundColor: '#000000', height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#2563EB', fontSize: '24px', fontFamily: 'monospace' },
  header: { maxWidth: '1400px', margin: '0 auto 40px' },

  // New Summary Card Styles
  summaryCard: { backgroundColor: '#232323', borderRadius: '8px', padding: '25px', boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.5)', color: '#FFFFFF', maxWidth: '450px', width: '100%', margin: '0 auto' },
  summaryHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #3a3a3a', paddingBottom: '15px' },
  dropdown: { backgroundColor: 'transparent', color: '#FFFFFF', fontWeight: '900', fontSize: '1.4rem', border: 'none', outline: 'none', cursor: 'pointer', appearance: 'none', paddingRight: '30px', backgroundImage: 'url("data:image/svg+xml;charset=US-ASCII,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22292.4%22%20height%3D%22292.4%22%3E%3Cpath%20fill%3D%22%23FFFFFF%22%20d%3D%22M287%2069.4a17.6%2017.6%200%200%200-13-5.4H18.4c-5%200-9.3%201.8-12.9%205.4A17.6%2017.6%200%200%200%200%2082.2c0%205%201.8%209.3%205.4%2012.9l128%20127.9c3.6%203.6%207.8%205.4%2012.8%205.4s9.2-1.8%2012.8-5.4L287%2095c3.5-3.5%205.4-7.8%205.4-12.8%200-5-1.9-9.2-5.5-12.8z%22%2F%3E%3C%2Fsvg%3E")', backgroundRepeat: 'no-repeat', backgroundPosition: 'right center', backgroundSize: '0.8rem' },
  summaryBiasContainer: { display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '30px 0' },
  summaryBiasLabel: { fontSize: '0.85rem', fontWeight: '600', letterSpacing: '1px', color: '#D4D4D8', marginBottom: '8px' },
  summaryBiasValue: { fontSize: '3rem', fontWeight: '900', letterSpacing: '-0.025em', textTransform: 'uppercase' },
  pillarBox: { backgroundColor: '#18181B', borderRadius: '8px', padding: '20px', display: 'flex', flexDirection: 'column', gap: '16px', boxShadow: 'inset 0 2px 4px 0 rgba(0, 0, 0, 0.2)' },
  pillarRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
  pillarName: { fontSize: '1rem', fontWeight: '500', color: '#E4E4E7' },
  pillarScore: { fontSize: '1.125rem', fontWeight: '700' },

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