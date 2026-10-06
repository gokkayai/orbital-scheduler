const fallbackResults = [
  {scenario:'Low Workload + Healthy Batteries',strategy:'Least Loaded',seed:42,nodes:10,jobs:40,completed_percent:100,deadline_misses:0,deadline_miss_percent:0,average_wait_minutes:0,energy_kwh:.485667},
  {scenario:'Low Workload + Healthy Batteries',strategy:'Energy Aware',seed:42,nodes:10,jobs:40,completed_percent:100,deadline_misses:0,deadline_miss_percent:0,average_wait_minutes:0,energy_kwh:.485667},
  {scenario:'High Workload',strategy:'Least Loaded',seed:42,nodes:10,jobs:240,completed_percent:100,deadline_misses:0,deadline_miss_percent:0,average_wait_minutes:26.325,energy_kwh:1.736},
  {scenario:'High Workload',strategy:'Energy Aware',seed:42,nodes:10,jobs:240,completed_percent:100,deadline_misses:0,deadline_miss_percent:0,average_wait_minutes:24.379,energy_kwh:1.736},
  {scenario:'Low Batteries + Long Eclipse',strategy:'Least Loaded',seed:42,nodes:10,jobs:120,completed_percent:100,deadline_misses:0,deadline_miss_percent:0,average_wait_minutes:.467,energy_kwh:.955333},
  {scenario:'Low Batteries + Long Eclipse',strategy:'Energy Aware',seed:42,nodes:10,jobs:120,completed_percent:100,deadline_misses:0,deadline_miss_percent:0,average_wait_minutes:.225,energy_kwh:.955333},
  {scenario:'Large Transfers + Slower Links',strategy:'Least Loaded',seed:42,nodes:10,jobs:120,completed_percent:74.167,deadline_misses:31,deadline_miss_percent:25.833,average_wait_minutes:30.217,energy_kwh:.955583},
  {scenario:'Large Transfers + Slower Links',strategy:'Energy Aware',seed:42,nodes:10,jobs:120,completed_percent:81.667,deadline_misses:22,deadline_miss_percent:18.333,average_wait_minutes:25.875,energy_kwh:.894833}
];

const scenarioMeta = {
  'Low Workload + Healthy Batteries': {short:'Low workload', detail:'Healthy power · good links', battery:'80–100 Wh', bandwidth:'100 Mbps', input:'0.1 GB', eclipse:'30 min'},
  'High Workload': {short:'High workload', detail:'Dense arrivals · good links', battery:'80–100 Wh', bandwidth:'100 Mbps', input:'0.1 GB', eclipse:'30 min'},
  'Low Batteries + Long Eclipse': {short:'Power constrained', detail:'Low charge · longer eclipse', battery:'25–45 Wh', bandwidth:'100 Mbps', input:'0.1 GB', eclipse:'35 min'},
  'Large Transfers + Slower Links': {short:'Transfer constrained', detail:'Large data · mixed links', battery:'80–100 Wh', bandwidth:'10 / 50 / 100 Mbps', input:'5 GB', eclipse:'30 min'}
};

const state = {results:fallbackResults, scenario:fallbackResults[0].scenario, strategy:'Least Loaded'};
const $ = id => document.getElementById(id);
const numberFields = new Set(['seed','nodes','jobs','completed_percent','deadline_misses','deadline_miss_percent','average_wait_minutes','energy_kwh']);

function parseCsv(text) {
  const lines = text.trim().split(/\r?\n/);
  const headers = lines.shift().split(',');
  return lines.map(line => {
    const values = line.split(',');
    return Object.fromEntries(headers.map((header, index) => [header,
      numberFields.has(header) ? Number(values[index]) : values[index]
    ]));
  }).filter(row => row.scenario && Number.isFinite(row.completed_percent));
}

async function loadLatestResults() {
  try {
    const response = await fetch('../results.csv', {cache:'no-store'});
    if (!response.ok) return;
    const parsed = parseCsv(await response.text());
    if (parsed.length === 8) state.results = parsed;
  } catch (_) {
    // Opening the HTML directly or deploying only ui/ uses the embedded seed-42 results.
  }
}

function scenarioResults() {
  return state.results.filter(result => result.scenario === state.scenario);
}

function resultFor(strategy) {
  return scenarioResults().find(result => result.strategy === strategy);
}

function renderTabs() {
  const scenarios = [...new Set(state.results.map(result => result.scenario))];
  $('scenarioTabs').replaceChildren(...scenarios.map((scenario, index) => {
    const meta = scenarioMeta[scenario];
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'scenario-tab' + (scenario === state.scenario ? ' active' : '');
    button.setAttribute('aria-pressed', scenario === state.scenario);
    button.innerHTML = `${String(index + 1).padStart(2, '0')} · ${meta.short}<span>${meta.detail}</span>`;
    button.addEventListener('click', () => { state.scenario = scenario; render(); });
    return button;
  }));
}

function policyCard(result) {
  const article = document.createElement('article');
  article.className = 'policy-result' + (result.strategy === 'Energy Aware' ? ' aware' : '');
  article.innerHTML = `
    <div class="policy-name">${result.strategy}</div>
    <div class="completion-row"><span class="completion-value">${result.completed_percent.toFixed(1)}%</span><span class="completion-label">completed</span></div>
    <div class="metric-grid">
      <div class="metric"><strong>${result.deadline_misses}</strong><span>missed</span></div>
      <div class="metric"><strong>${result.average_wait_minutes.toFixed(1)} min</strong><span>average wait</span></div>
      <div class="metric"><strong>${result.energy_kwh.toFixed(3)}</strong><span>kWh used</span></div>
    </div>`;
  return article;
}

function renderComparison() {
  const [baseline, aware] = scenarioResults();
  $('scenarioTitle').textContent = state.scenario;
  $('scenarioBadge').textContent = `${baseline.jobs} jobs`;
  $('comparison').replaceChildren(policyCard(baseline), policyCard(aware));

  const completionGain = aware.completed_percent - baseline.completed_percent;
  const waitGain = baseline.average_wait_minutes - aware.average_wait_minutes;
  const energyGain = baseline.energy_kwh === 0 ? 0 : 100 * (baseline.energy_kwh - aware.energy_kwh) / baseline.energy_kwh;
  const verdict = $('verdict');
  if (completionGain > .05) {
    const waitText = waitGain >= 0
      ? `${waitGain.toFixed(1)} minutes less waiting`
      : `${Math.abs(waitGain).toFixed(1)} minutes more waiting`;
    const energyText = Math.abs(energyGain) < .05
      ? 'the same energy use'
      : energyGain > 0
        ? `${energyGain.toFixed(1)}% less energy`
        : `${Math.abs(energyGain).toFixed(1)}% more energy`;
    const hasTradeoff = waitGain < 0 || energyGain < -.05;
    verdict.className = 'verdict';
    verdict.innerHTML = `<strong>${hasTradeoff ? 'Higher completion, with trade-offs.' : 'Energy aware helps.'}</strong><span>+${completionGain.toFixed(1)} completion points, ${baseline.deadline_misses - aware.deadline_misses} fewer misses, ${waitText}, and ${energyText}.</span>`;
  } else if (waitGain > .05) {
    verdict.className = 'verdict';
    verdict.innerHTML = `<strong>Small scheduling gain.</strong><span>Completion is unchanged, while average waiting falls by ${waitGain.toFixed(1)} minutes. Total energy is effectively the same.</span>`;
  } else {
    verdict.className = 'verdict neutral';
    verdict.innerHTML = '<strong>No meaningful difference.</strong><span>With spare capacity, healthy batteries, and uniform links, the simpler scheduler is enough.</span>';
  }
}

function renderFleet() {
  const result = resultFor(state.strategy);
  const meta = scenarioMeta[state.scenario];
  $('fleetTitle').textContent = `${state.strategy} placement`;
  $('nodeCount').textContent = result.nodes;
  $('seedValue').textContent = result.seed;
  const layer = $('nodeLayer');
  layer.replaceChildren();
  const shownNodes = Math.min(result.nodes, 24);
  const selectedIndex = state.strategy === 'Energy Aware' ? Math.min(7, shownNodes - 1) : 0;
  for (let index = 0; index < shownNodes; index++) {
    const outer = index % 2 === 1;
    const angle = (index / shownNodes) * Math.PI * 2 + (outer ? .4 : 0);
    const rx = outer ? 41 : 35;
    const ry = outer ? 28 : 18;
    const sunlit = (index + Object.keys(scenarioMeta).indexOf(state.scenario)) % 3 !== 0;
    const batterySpan = meta.battery.split('–').map(value => Number.parseInt(value));
    const battery = Math.round(batterySpan[0] + (batterySpan[1] - batterySpan[0]) * ((index * 7 % 10) / 10));
    const link = state.scenario.includes('Transfers') ? [10, 50, 100][index % 3] : 100;
    const node = document.createElement('button');
    node.type = 'button';
    node.className = `node${sunlit ? ' sun' : ''}${index === selectedIndex ? ' selected' : ''}`;
    node.style.left = `${50 + Math.cos(angle) * rx}%`;
    node.style.top = `${50 + Math.sin(angle) * ry}%`;
    node.setAttribute('aria-label', `Node ${index + 1}: ${battery} watt-hours, ${link} megabits per second, ${sunlit ? 'sunlight' : 'eclipse'}`);
    const tip = document.createElement('span');
    tip.className = 'node-tooltip';
    tip.style.left = node.style.left;
    tip.style.top = `calc(${node.style.top} + 12px)`;
    tip.textContent = `N${String(index + 1).padStart(2,'0')} · ${battery} Wh · ${link} Mbps`;
    layer.append(node, tip);
  }
  $('fleetNote').textContent = `${result.nodes > shownNodes ? `Showing ${shownNodes} of ${result.nodes} · ` : ''}${meta.battery} · ${meta.bandwidth}`;
}

function renderWorkload() {
  const result = resultFor(state.strategy);
  const meta = scenarioMeta[state.scenario];
  $('workloadTitle').textContent = `${result.jobs} jobs over 180 minutes`;
  $('jobStream').replaceChildren(...Array.from({length:Math.min(result.jobs, 36)}, (_, index) => {
    const bar = document.createElement('span');
    bar.className = 'job-bar' + (index % 4 === 0 ? ' heavy' : '');
    bar.style.height = `${22 + ((index * 37 + result.jobs) % 70)}%`;
    bar.title = `Representative job ${index + 1}`;
    return bar;
  }));
  $('workloadMeta').innerHTML = `<span>1–2 GPU units</span><span>5–20 min compute</span><span>${meta.input} input</span><span>${meta.bandwidth}</span><span>${meta.eclipse} eclipse</span>`;
}

function renderMethod() {
  const aware = state.strategy === 'Energy Aware';
  $('methodTitle').textContent = state.strategy;
  $('methodCopy').textContent = aware
    ? 'Balance data-transfer delay against the risk of using a low battery, with a smaller penalty in sunlight.'
    : 'Choose the eligible node with the most available GPU capacity.';
  $('formula').textContent = aware
    ? 'transfer min + 30 × (1 − battery) × sunlight factor'
    : 'max(available GPUs)';
  $('methodNote').textContent = aware
    ? 'Sunlight factor: 0.5 in sunlight, 1.0 in eclipse. Ties favor free GPUs.'
    : 'Eligibility still enforces battery reserve. Ties go to the lowest node ID.';
}

function updateDownload() {
  const headers = ['scenario','strategy','seed','nodes','jobs','completed_percent','deadline_misses','deadline_miss_percent','average_wait_minutes','energy_kwh'];
  const csv = [headers.join(','), ...state.results.map(result => headers.map(header => result[header]).join(','))].join('\n');
  $('downloadResults').href = URL.createObjectURL(new Blob([csv + '\n'], {type:'text/csv'}));
}

function renderTable() {
  const body = $('resultsBody');
  body.replaceChildren(...state.results.map(result => {
    const row = document.createElement('tr');
    const aware = result.strategy === 'Energy Aware';
    row.innerHTML = `<td>${scenarioMeta[result.scenario].short}</td>
      <td><span class="strategy-cell${aware ? ' aware' : ''}"><i></i>${result.strategy}</span></td>
      <td${result.completed_percent > 80 ? ' class="good"' : ''}>${result.completed_percent.toFixed(1)}%</td>
      <td>${result.deadline_misses}</td>
      <td>${result.average_wait_minutes.toFixed(1)} min</td>
      <td>${result.energy_kwh.toFixed(3)} kWh</td>`;
    return row;
  }));
}

function render() {
  renderTabs();
  renderComparison();
  renderFleet();
  renderWorkload();
  renderMethod();
  renderTable();
  updateDownload();
}

document.querySelectorAll('.strategy-button').forEach(button => {
  button.addEventListener('click', () => {
    state.strategy = button.dataset.strategy;
    document.querySelectorAll('.strategy-button').forEach(item => {
      const active = item === button;
      item.classList.toggle('active', active);
      item.setAttribute('aria-pressed', active);
    });
    renderFleet();
    renderWorkload();
    renderMethod();
  });
});

loadLatestResults().finally(() => {
  state.scenario = state.results[0].scenario;
  render();
});
