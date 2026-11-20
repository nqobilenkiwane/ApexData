import { useState, useEffect } from 'react'
import { BarChart, Bar, ReferenceLine, ReferenceArea, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import LogoDark from './LogoDark'

function App() {
  const [summary, setSummary] = useState(null)
  const [history, setHistory] = useState([])
  const [shifts, setShifts] = useState([])
  const [goldSummary, setGoldSummary] = useState(null)
  const [silverSummary, setSilverSummary] = useState(null)
  const [nasdaqSummary, setNasdaqSummary] = useState(null)
  const [dowSummary, setDowSummary] = useState(null)
  const [sp500Summary, setSp500Summary] = useState(null)
  const [russell2000Summary, setRussell2000Summary] = useState(null)
  const [loading, setLoading] = useState(true)

  // Navigation & layout state
  const [activePage, setActivePage] = useState('dashboard')
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [activeAsset, setActiveAsset] = useState('DXY')
  const [hoveredRow, setHoveredRow] = useState(null)

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
      fetch(`${baseUrl}/api/dashboard/shifts`).then(res => {
        if (!res.ok) return [];
        return res.json();
      }).catch(() => []),
      fetch(`${baseUrl}/api/dashboard/gold`).then(res => res.ok ? res.json() : null).catch(() => null),
      fetch(`${baseUrl}/api/dashboard/silver`).then(res => res.ok ? res.json() : null).catch(() => null),
      fetch(`${baseUrl}/api/dashboard/nasdaq`).then(res => res.ok ? res.json() : null).catch(() => null),
      fetch(`${baseUrl}/api/dashboard/dow`).then(res => res.ok ? res.json() : null).catch(() => null),
      fetch(`${baseUrl}/api/dashboard/sp500`).then(res => res.ok ? res.json() : null).catch(() => null),
      fetch(`${baseUrl}/api/dashboard/russell2000`).then(res => res.ok ? res.json() : null).catch(() => null)
    ])
      .then(([summaryData, historyData, shiftsData, goldData, silverData, nasdaqData, dowData, sp500Data, russellData]) => {
        setSummary(summaryData);
        setShifts(Array.isArray(shiftsData) ? shiftsData : []);
        setGoldSummary(goldData);
        setSilverSummary(silverData);
        setNasdaqSummary(nasdaqData);
        setDowSummary(dowData);
        setSp500Summary(sp500Data);
        setRussell2000Summary(russellData);

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

  const getScoreBg = (score) => {
    if (score > 0) return 'rgba(0, 255, 136, 0.08)';
    if (score < 0) return 'rgba(255, 51, 102, 0.08)';
    return 'rgba(255, 255, 255, 0.03)';
  };

  const getActualValueColor = (scoreDelta) => {
    if (scoreDelta > 0) return '#00ff88';
    if (scoreDelta < 0) return '#ff3366';
    return '#FFFFFF';
  };

  const formatCategory = (cat) => {
    return cat ? cat.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase()) : '';
  };

  const formatSigned = (val) => {
    const num = val ?? 0;
    return num > 0 ? `+${num}` : `${num}`;
  };

  // Formats "2026-11-18T19:30:06" -> "18 Nov"
  const formatShortDate = (dateString) => {
    if (!dateString) return null;
    const date = new Date(dateString);
    if (isNaN(date.getTime())) return null;
    return date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
  };

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

  // Build & sort Asset Rankings (Most Bullish -> Most Bearish)
  const rankedAssets = [
    { id: 'DXY', symbol: 'DXY', name: 'US Dollar', data: summary },
    { id: 'XAUUSD', symbol: 'XAUUSD', name: 'Gold', data: goldSummary },
    { id: 'XAGUSD', symbol: 'XAGUSD', name: 'Silver', data: silverSummary },
    { id: 'NAS100', symbol: 'NAS100', name: 'Nasdaq 100', data: nasdaqSummary },
    { id: 'US30', symbol: 'US30', name: 'Dow Jones', data: dowSummary },
    { id: 'SPX', symbol: 'SPX', name: 'S&P 500', data: sp500Summary },
    { id: 'RUT', symbol: 'RUT', name: 'Russell 2000', data: russell2000Summary }
  ]
    .filter(asset => asset.data !== null)
    .map(asset => ({
      ...asset,
      score: asset.data.totalScore ?? 0,
      bias: asset.data.biasLabel || asset.data.overallBias || 'NEUTRAL',
      cats: asset.data.categoryScores || {}
    }))
    .sort((a, b) => b.score - a.score);

  let activeSummaryData = summary;
  if (activeAsset === 'XAUUSD') activeSummaryData = goldSummary;
  if (activeAsset === 'XAGUSD') activeSummaryData = silverSummary;
  if (activeAsset === 'NAS100') activeSummaryData = nasdaqSummary;
  if (activeAsset === 'US30') activeSummaryData = dowSummary;
  if (activeAsset === 'SPX') activeSummaryData = sp500Summary;
  if (activeAsset === 'RUT') activeSummaryData = russell2000Summary;

  const activeScore = activeSummaryData?.totalScore ?? 0;
  const activeBias = activeSummaryData?.biasLabel || activeSummaryData?.overallBias || 'NEUTRAL';

  let activePillars = [];
  if (activeAsset === 'XAUUSD' && goldSummary) {
    activePillars = [
      { name: 'USD Macro Inversion', score: goldSummary.invertedMacroBaseline ?? goldSummary.macroHealth ?? 0 },
      { name: 'Positioning & Flows', score: goldSummary.cotScore ?? goldSummary.positioningAndFlows ?? 0 },
      { name: 'Technical Momentum', score: goldSummary.technicalScore ?? goldSummary.technicalMomentum ?? 0 }
    ];
  } else if (activeAsset === 'XAGUSD' && silverSummary) {
    activePillars = [
      { name: 'USD Macro Inversion', score: silverSummary.invertedMacroBaseline ?? silverSummary.macroHealth ?? 0 },
      { name: 'Positioning & Flows', score: silverSummary.cotScore ?? silverSummary.positioningAndFlows ?? 0 },
      { name: 'Technical Momentum', score: silverSummary.technicalScore ?? silverSummary.technicalMomentum ?? 0 }
    ];
  } else if (activeAsset === 'NAS100' && nasdaqSummary) {
    activePillars = [
      { name: 'Economic Health', score: nasdaqSummary.invertedMacroBaseline ?? nasdaqSummary.macroHealth ?? 0 },
      { name: 'Positioning & Flows', score: nasdaqSummary.cotScore ?? nasdaqSummary.positioningAndFlows ?? 0 },
      { name: 'Technical Momentum', score: nasdaqSummary.technicalScore ?? nasdaqSummary.technicalMomentum ?? 0 }
    ];
  } else if (activeAsset === 'US30' && dowSummary) {
    activePillars = [
      { name: 'Economic Health', score: dowSummary.invertedMacroBaseline ?? dowSummary.macroHealth ?? 0 },
      { name: 'Positioning & Flows', score: dowSummary.cotScore ?? dowSummary.positioningAndFlows ?? 0 },
      { name: 'Technical Momentum', score: dowSummary.technicalScore ?? dowSummary.technicalMomentum ?? 0 }
    ];
  } else if (activeAsset === 'SPX' && sp500Summary) {
    activePillars = [
      { name: 'Economic Health', score: sp500Summary.invertedMacroBaseline ?? sp500Summary.macroHealth ?? 0 },
      { name: 'Positioning & Flows', score: sp500Summary.cotScore ?? sp500Summary.positioningAndFlows ?? 0 },
      { name: 'Technical Momentum', score: sp500Summary.technicalScore ?? sp500Summary.technicalMomentum ?? 0 }
    ];
  } else if (activeAsset === 'RUT' && russell2000Summary) {
    activePillars = [
      { name: 'Economic Health', score: russell2000Summary.invertedMacroBaseline ?? russell2000Summary.macroHealth ?? 0 },
      { name: 'Positioning & Flows', score: russell2000Summary.cotScore ?? russell2000Summary.positioningAndFlows ?? 0 },
      { name: 'Technical Momentum', score: russell2000Summary.technicalScore ?? russell2000Summary.technicalMomentum ?? 0 }
    ];
  } else if (summary) {
    activePillars = [
      { name: 'Economic Health', score: (summary.categoryScores?.ECONOMIC_GROWTH || 0) + (summary.categoryScores?.JOB_MARKET || 0) + (summary.categoryScores?.INFLATION || 0) },
      { name: 'Positioning & Flows', score: (summary.categoryScores?.CAPITAL_FLOWS || 0) + (summary.categoryScores?.INSTITUTIONAL_ACTIVITY || 0) },
      { name: 'Technical Momentum', score: (summary.categoryScores?.TECHNICALS || 0) }
    ];
  }

  const sixtyDaysAgo = new Date();
  sixtyDaysAgo.setDate(sixtyDaysAgo.getDate() - 60);

  const rawFilteredHistory = history.filter(item => {
    const itemDate = new Date(item.timestamp);
    if (itemDate < sixtyDaysAgo) return false;
    if (activeAsset === 'DXY') return item.currency === 'USD' || !item.currency;
    return item.currency === activeAsset;
  });

  const dailyMap = new Map();
  rawFilteredHistory.forEach(item => {
    dailyMap.set(item.displayDate, item);
  });
  const activeHistory = Array.from(dailyMap.values());

  const chartTitle = activeAsset === 'XAUUSD' ? 'GOLD MACRO TREND' :
                     activeAsset === 'XAGUSD' ? 'SILVER MACRO TREND' :
                     activeAsset === 'NAS100' ? 'NASDAQ 100 MACRO TREND' :
                     activeAsset === 'US30' ? 'US30 MACRO TREND' :
                     activeAsset === 'SPX' ? 'S&P 500 MACRO TREND' :
                     activeAsset === 'RUT' ? 'RUSSELL 2000 MACRO TREND' :
                     'USD MACRO TREND';

  return (
    <div style={styles.appShell}>
      {/* COLLAPSIBLE LEFT SIDEBAR */}
      <aside style={{ ...styles.sidebar, width: sidebarCollapsed ? '72px' : '240px' }}>
        <div style={styles.sidebarBrand}>
          {!sidebarCollapsed ? (
            <span style={styles.brandText}>APEX<span style={{ color: '#2563EB' }}>DATA</span></span>
          ) : (
            <span style={{ ...styles.brandText, color: '#2563EB' }}>AD</span>
          )}
        </div>

        <nav style={styles.navList}>
          <button
            onClick={() => setActivePage('dashboard')}
            style={{
              ...styles.navItem,
              ...(activePage === 'dashboard' ? styles.navItemActive : {}),
              justifyContent: sidebarCollapsed ? 'center' : 'flex-start'
            }}
            title="Macro Dashboard"
          >
            <span style={styles.navIcon}>📊</span>
            {!sidebarCollapsed && <span>Macro Dashboard</span>}
          </button>

          <button
            onClick={() => setActivePage('rankings')}
            style={{
              ...styles.navItem,
              ...(activePage === 'rankings' ? styles.navItemActive : {}),
              justifyContent: sidebarCollapsed ? 'center' : 'flex-start'
            }}
            title="Asset Rankings"
          >
            <span style={styles.navIcon}>🏆</span>
            {!sidebarCollapsed && <span>Asset Rankings</span>}
          </button>

          <button
            onClick={() => setActivePage('shifts')}
            style={{
              ...styles.navItem,
              ...(activePage === 'shifts' ? styles.navItemActive : {}),
              justifyContent: sidebarCollapsed ? 'center' : 'flex-start'
            }}
            title="Recent Shifts"
          >
            <span style={styles.navIcon}>⚡</span>
            {!sidebarCollapsed && <span>Recent Shifts</span>}
          </button>
        </nav>

        <button
          onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
          style={styles.collapseBtn}
          title={sidebarCollapsed ? 'Expand Sidebar' : 'Collapse Sidebar'}
        >
          {sidebarCollapsed ? '›' : '‹ Collapse'}
        </button>
      </aside>

      {/* MAIN CONTENT AREA */}
      <main style={styles.mainContent}>
        {activePage === 'dashboard' ? (
          <>
            <header style={styles.header}>
              <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '30px' }}>
                <LogoDark width={350} height={120} />
              </div>

              <div style={styles.summaryCard}>
                <div style={styles.summaryHeader}>
                  <select
                    value={activeAsset}
                    onChange={(e) => setActiveAsset(e.target.value)}
                    style={styles.dropdown}
                  >
                    <option value="DXY" style={{ backgroundColor: '#111111' }}>US DOLLAR (DXY)</option>
                    {goldSummary && <option value="XAUUSD" style={{ backgroundColor: '#111111' }}>GOLD (XAUUSD)</option>}
                    {silverSummary && <option value="XAGUSD" style={{ backgroundColor: '#111111' }}>SILVER (XAGUSD)</option>}
                    {nasdaqSummary && <option value="NAS100" style={{ backgroundColor: '#111111' }}>NASDAQ 100</option>}
                    {dowSummary && <option value="US30" style={{ backgroundColor: '#111111' }}>US30 (DOW)</option>}
                    {sp500Summary && <option value="SPX" style={{ backgroundColor: '#111111' }}>S&P 500 (SPX)</option>}
                    {russell2000Summary && <option value="RUT" style={{ backgroundColor: '#111111' }}>RUSSELL 2000 (RUT)</option>}
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

            {activeHistory.length > 0 && (
              <div style={styles.chartSection}>
                <div style={styles.cardHeader}>
                  <h2 style={styles.cardTitle}>{chartTitle}</h2>
                </div>
                <div style={styles.chartWrapper}>
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={activeHistory} margin={{ top: 10, right: 10, left: -20, bottom: 0 }} barCategoryGap="10%">
                      <CartesianGrid strokeDasharray="3 3" stroke="#222222" vertical={false} />
                      <XAxis dataKey="displayDate" stroke="#888888" tick={{ fill: '#888888', fontSize: 12 }} tickMargin={10} minTickGap={40} />
                      <YAxis stroke="#888888" tick={{ fill: '#888888', fontSize: 12 }} domain={[-22, 22]} />
                      <Tooltip content={<CustomTooltip />} cursor={{ fill: '#1a1a1a' }} />
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

            <div style={styles.grid}>
              {CATEGORY_ORDER.map((category) => {
                const catScore = activeSummaryData?.categoryScores?.[category];
                if (catScore === undefined) return null;

                return (
                  <div key={category} style={styles.card}>
                    <div style={styles.cardHeader}>
                      <h2 style={styles.cardTitle}>{formatCategory(category)}</h2>
                      <span style={{ ...styles.catScoreBadge, color: getMetricScoreColor(catScore) }}>
                        {catScore > 0 ? '+' : ''}{catScore}
                      </span>
                    </div>
                    <div style={styles.metricList}>
                      {activeSummaryData?.metrics
                        ?.filter(m => m.category === category)
                        .map(metric => {
                          const updatedDate = formatShortDate(metric.lastUpdated || metric.last_updated);
                          return (
                            <div key={metric.name} style={styles.metricRow}>
                              <div style={styles.metricNameBlock}>
                                <div style={styles.metricName}>{metric.name}</div>
                                {updatedDate && (
                                  <div style={styles.metricUpdated}>Updated {updatedDate}</div>
                                )}
                              </div>
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
                              <div style={{ ...styles.metricScore, color: getMetricScoreColor(metric.scoreDelta) }}>
                                {metric.scoreDelta > 0 ? '+' : ''}{metric.scoreDelta}
                              </div>
                            </div>
                          );
                        })}
                    </div>
                  </div>
                );
              })}
            </div>
          </>
        ) : activePage === 'rankings' ? (
          /* ASSET RANKINGS PAGE */
          <div style={styles.shiftsContainer}>
            <div style={styles.cardHeader}>
              <h2 style={styles.cardTitle}>ASSET RANKINGS</h2>
              <span style={{ color: '#888888', fontSize: '0.85rem' }}>Click any asset to inspect full breakdown</span>
            </div>

            <div style={styles.tableWrapper}>
              <table style={styles.table}>
                <thead>
                  <tr style={styles.tableHeaderRow}>
                    <th style={styles.th}>Rank</th>
                    <th style={styles.th}>Asset</th>
                    <th style={styles.th}>Bias</th>
                    <th style={styles.thCenter}>Growth</th>
                    <th style={styles.thCenter}>Jobs</th>
                    <th style={styles.thCenter}>Inflation</th>
                    <th style={styles.thCenter}>COT</th>
                    <th style={styles.thCenter}>Yields</th>
                    <th style={styles.thCenter}>Techs</th>
                    <th style={styles.thRight}>Composite</th>
                  </tr>
                </thead>
                <tbody>
                  {rankedAssets.map((asset, index) => {
                    const isHovered = hoveredRow === asset.id;
                    return (
                      <tr
                        key={asset.id}
                        onClick={() => {
                          setActiveAsset(asset.id);
                          setActivePage('dashboard');
                        }}
                        onMouseEnter={() => setHoveredRow(asset.id)}
                        onMouseLeave={() => setHoveredRow(null)}
                        style={{
                          ...styles.tableRow,
                          cursor: 'pointer',
                          backgroundColor: isHovered ? '#18181D' : 'transparent',
                          transition: 'background-color 0.15s ease'
                        }}
                      >
                        <td style={styles.tdMuted}>#{index + 1}</td>
                        <td style={styles.tdBold}>
                          <div style={{ display: 'flex', flexDirection: 'column' }}>
                            <span>{asset.symbol}</span>
                            <span style={{ color: '#71717A', fontSize: '0.78rem', fontWeight: '400' }}>{asset.name}</span>
                          </div>
                        </td>
                        <td style={styles.td}>
                          <span style={{
                            ...styles.biasPill,
                            color: getCompositeScoreColor(asset.score),
                            borderColor: getCompositeScoreColor(asset.score)
                          }}>
                            {asset.bias}
                          </span>
                        </td>
                        {CATEGORY_ORDER.map(cat => {
                          const val = asset.cats[cat] ?? 0;
                          return (
                            <td key={cat} style={styles.tdCenter}>
                              <span style={{
                                ...styles.catPill,
                                color: getMetricScoreColor(val),
                                backgroundColor: getScoreBg(val)
                              }}>
                                {formatSigned(val)}
                              </span>
                            </td>
                          );
                        })}
                        <td style={{
                          ...styles.tdRightBold,
                          fontSize: '1.15rem',
                          color: getCompositeScoreColor(asset.score)
                        }}>
                          {formatSigned(asset.score)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        ) : (
          /* RECENT SHIFTS PAGE */
          <div style={styles.shiftsContainer}>
            <div style={styles.cardHeader}>
              <h2 style={styles.cardTitle}>RECENT MACRO SHIFTS</h2>
              <span style={{ color: '#888888', fontSize: '0.85rem' }}>Latest Economic Data Releases</span>
            </div>

            {shifts.length === 0 ? (
              <div style={styles.emptyShifts}>
                No recent shifts logged yet. Once the daily Apify sync detects a new economic print, the before/after breakdown will appear here.
              </div>
            ) : (
              <div style={styles.tableWrapper}>
                <table style={styles.table}>
                  <thead>
                    <tr style={styles.tableHeaderRow}>
                      <th style={styles.th}>Date</th>
                      <th style={styles.th}>Metric</th>
                      <th style={styles.th}>Category</th>
                      <th style={styles.thRight}>Previous</th>
                      <th style={styles.thRight}>New Actual</th>
                      <th style={styles.thRight}>Estimate</th>
                    </tr>
                  </thead>
                  <tbody>
                    {shifts.map((shift, idx) => (
                      <tr key={shift.id || idx} style={styles.tableRow}>
                        <td style={styles.tdMuted}>{formatShortDate(shift.changedAt || shift.changed_at) || '-'}</td>
                        <td style={styles.tdBold}>{shift.metricName || shift.metric_name}</td>
                        <td style={styles.tdMuted}>{formatCategory(shift.category)}</td>
                        <td style={styles.tdRightMuted}>{Number(shift.oldActual ?? shift.old_actual).toFixed(2)}</td>
                        <td style={styles.tdRightBold}>{Number(shift.newActual ?? shift.new_actual).toFixed(2)}</td>
                        <td style={styles.tdRightMuted}>{Number(shift.estimateValue ?? shift.estimate_value).toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  )
}

const styles = {
  appShell: { display: 'flex', minHeight: '100vh', backgroundColor: '#000000', color: '#FFFFFF', fontFamily: "'Inter', 'Segoe UI', sans-serif" },
  sidebar: { backgroundColor: '#0B0B0D', borderRight: '1px solid #1E1E24', display: 'flex', flexDirection: 'column', padding: '24px 12px', transition: 'width 0.2s ease', flexShrink: 0, position: 'sticky', top: 0, height: '100vh', boxSizing: 'border-box' },
  sidebarBrand: { display: 'flex', alignItems: 'center', justifyContent: 'center', paddingBottom: '24px', marginBottom: '16px', borderBottom: '1px solid #1E1E24' },
  brandText: { fontSize: '1.25rem', fontWeight: '900', letterSpacing: '2px', color: '#FFFFFF' },
  navList: { display: 'flex', flexDirection: 'column', gap: '8px', flex: 1 },
  navItem: { display: 'flex', alignItems: 'center', gap: '12px', backgroundColor: 'transparent', color: '#A1A1AA', border: 'none', borderRadius: '6px', padding: '12px', fontSize: '0.95rem', fontWeight: '500', cursor: 'pointer', transition: 'all 0.15s ease', textAlign: 'left' },
  navItemActive: { backgroundColor: 'rgba(37, 99, 235, 0.15)', color: '#FFFFFF', borderLeft: '3px solid #2563EB' },
  navIcon: { fontSize: '1.1rem' },
  collapseBtn: { backgroundColor: '#141418', color: '#888888', border: '1px solid #222222', borderRadius: '6px', padding: '10px', cursor: 'pointer', fontSize: '0.85rem', fontWeight: '600' },
  mainContent: { flex: 1, padding: '40px 24px', overflowX: 'hidden' },
  loading: { backgroundColor: '#000000', height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#2563EB', fontSize: '24px', fontFamily: 'monospace' },
  header: { maxWidth: '1400px', margin: '0 auto 40px' },
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
  grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: '25px', maxWidth: '1400px', margin: '0 auto' },
  card: { backgroundColor: '#111111', borderRadius: '8px', padding: '25px', border: '1px solid #222222' },
  cardHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '2px solid #2563EB', paddingBottom: '15px', marginBottom: '15px' },
  cardTitle: { margin: 0, fontSize: '1.2rem', color: '#FFFFFF', letterSpacing: '1px' },
  catScoreBadge: { fontSize: '1.2rem', fontWeight: 'bold', backgroundColor: 'rgba(255,255,255,0.05)', padding: '5px 12px', borderRadius: '4px' },
  metricList: { display: 'flex', flexDirection: 'column', gap: '12px' },
  metricRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#000000', padding: '12px 15px', borderRadius: '6px', fontSize: '0.9rem', border: '1px solid #1A1A1A' },
  metricNameBlock: { flex: '1', display: 'flex', flexDirection: 'column', gap: '3px' },
  metricName: { color: '#CCCCCC', fontWeight: '500' },
  metricUpdated: { color: '#666666', fontSize: '0.72rem' },
  metricValues: { flex: '1', display: 'flex', flexDirection: 'column', alignItems: 'flex-end', marginRight: '20px' },
  actual: { color: '#FFFFFF', fontWeight: '600' },
  estimate: { color: '#888888', fontSize: '0.8rem', marginTop: '3px' },
  metricScore: { fontWeight: 'bold', fontSize: '1.1rem', minWidth: '35px', textAlign: 'right' },
  shiftsContainer: { maxWidth: '1200px', margin: '0 auto', backgroundColor: '#111111', borderRadius: '8px', padding: '25px', border: '1px solid #222222' },
  emptyShifts: { color: '#888888', textAlign: 'center', padding: '60px 20px', fontSize: '0.95rem' },
  tableWrapper: { overflowX: 'auto' },
  table: { width: '100%', borderCollapse: 'collapse', fontSize: '0.92rem' },
  tableHeaderRow: { borderBottom: '1px solid #222222', color: '#888888', textTransform: 'uppercase', fontSize: '0.78rem', letterSpacing: '0.8px' },
  th: { textAlign: 'left', padding: '12px 14px' },
  thCenter: { textAlign: 'center', padding: '12px 14px' },
  thRight: { textAlign: 'right', padding: '12px 14px' },
  tableRow: { borderBottom: '1px solid #1A1A1A' },
  td: { padding: '14px' },
  tdBold: { padding: '14px', color: '#FFFFFF', fontWeight: '600' },
  tdMuted: { padding: '14px', color: '#888888' },
  tdCenter: { padding: '14px', textAlign: 'center' },
  tdRightBold: { padding: '14px', textAlign: 'right', color: '#00ff88', fontWeight: '600' },
  tdRightMuted: { padding: '14px', textAlign: 'right', color: '#888888' },
  biasPill: { display: 'inline-block', padding: '4px 10px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: '700', letterSpacing: '0.5px', border: '1px solid', textTransform: 'uppercase' },
  catPill: { display: 'inline-block', minWidth: '34px', padding: '4px 8px', borderRadius: '4px', fontWeight: '700', fontSize: '0.88rem' }
};

export default App;