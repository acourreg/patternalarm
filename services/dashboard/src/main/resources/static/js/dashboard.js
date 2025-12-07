// PatternAlarm Dashboard - Auto-refresh every 5s

let chart = null;

// Init
document.addEventListener('DOMContentLoaded', () => {
    initChart();
    refresh();
    setInterval(refresh, 5000);
});

// Refresh alerts + chart
async function refresh() {
    try {
        const [alerts, velocity] = await Promise.all([
            fetch('/api/alerts?limit=10').then(r => r.json()),
            fetch('/api/analytics/velocity').then(r => r.json())
        ]);
        updateFeed(alerts);
        updateChart(velocity);
        document.getElementById('updateTime').textContent = new Date().toLocaleTimeString();
    } catch (e) {
        console.error('Refresh failed:', e);
    }
}

// Fraud feed table
function updateFeed(alerts) {
    const tbody = document.getElementById('feedBody');
    if (!alerts.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No alerts</td></tr>';
        return;
    }
    tbody.innerHTML = alerts.map(a => `
        <tr>
            <td><span class="badge bg-primary">${a.domain}</span></td>
            <td><code>${a.actor_id}</code></td>
            <td>${a.alert_type}</td>
            <td>$${a.total_amount.toFixed(0)}</td>
            <td><span class="badge bg-${severityColor(a.severity)}">${a.severity}</span></td>
            <td>${a.fraud_score}</td>
        </tr>
    `).join('');
    document.getElementById('alertCount').textContent = alerts.length;
}

function severityColor(s) {
    const key = s?.toLowerCase() || '';
    return { critical: 'danger', high: 'warning', medium: 'info', low: 'success' }[key] || 'secondary';
}

// Chart update
function updateChart(data) {
    if (!data.data_points?.length) return;
    const pts = data.data_points.slice(-15);
    chart.data.labels = pts.map(p => `${Math.floor(p.x / 60)}m`);
    chart.data.datasets[0].data = pts.map(p => p.y1_velocity);
    chart.update('none');
}

// Chart init - single axis
function initChart() {
    chart = new Chart(document.getElementById('chart'), {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'Alerts/min',
                data: [],
                borderColor: '#dc3545',
                backgroundColor: 'rgba(220, 53, 69, 0.1)',
                fill: true,
                tension: 0.3
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: { beginAtZero: true }
            }
        }
    });
}

// Test execution
document.getElementById('testForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const domain = document.querySelector('input[name="domain"]:checked')?.value;
    if (!domain) return alert('Select a domain');

    const btn = document.getElementById('runBtn');
    btn.disabled = true;
    btn.textContent = 'Running...';

    const res = await fetch('/api/test/execute', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `domain=${domain}&loadLevel=${document.getElementById('loadLevel').value}`
    }).then(r => r.json());

    btn.textContent = res.success ? '✓ Started' : '✗ Failed';
    setTimeout(() => { btn.disabled = false; btn.textContent = 'Run Test'; }, 3000);
});