// PatternAlarm Dashboard - Frontend Logic
let currentTestId = null;
let pollInterval = null;

// AC1.3: Show crisis warning
document.getElementById('loadLevel').addEventListener('change', function() {
    const warning = document.getElementById('crisisWarning');
    if (this.value === 'crisis') {
        document.getElementById('crisisCost').textContent = '$3.80';
        warning.style.display = 'block';
    } else {
        warning.style.display = 'none';
    }
});

// AC1.2: Execute test
document.getElementById('testForm').addEventListener('submit', async function(e) {
    e.preventDefault();

    const domain = document.querySelector('input[name="domain"]:checked');
    if (!domain) {
        alert('Please select a domain');
        return;
    }

    const loadLevel = document.getElementById('loadLevel').value;
    const executeBtn = document.getElementById('executeBtn');

    // Disable button during execution
    executeBtn.disabled = true;
    executeBtn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Starting...';

    try {
        const response = await fetch('/api/test/execute', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: `domain=${domain.value}&loadLevel=${loadLevel}`
        });

        const result = await response.json();

        if (result.success) {
            currentTestId = result.testId;
            document.getElementById('progressCard').style.display = 'block';
            startPolling();
        } else {
            alert('Failed to start test');
            executeBtn.disabled = false;
            executeBtn.innerHTML = '<i class="bi bi-play-fill"></i> Run Test';
        }
    } catch (error) {
        console.error('Error:', error);
        alert('Error starting test');
        executeBtn.disabled = false;
        executeBtn.innerHTML = '<i class="bi bi-play-fill"></i> Run Test';
    }
});

// AC1.4: Poll for progress every 3 seconds
function startPolling() {
    pollInterval = setInterval(updateProgress, 3000);
    updateProgress(); // Initial update
}

async function updateProgress() {
    if (!currentTestId) return;

    try {
        const response = await fetch(`/api/test/${currentTestId}/progress`);
        const progress = await response.json();

        if (progress.error) {
            stopPolling();
            return;
        }

        // Update metrics
        document.getElementById('eventsProcessed').textContent =
            progress.eventsProcessed.toLocaleString();
        document.getElementById('fraudDetected').textContent =
            progress.fraudDetected.toLocaleString();
        document.getElementById('throughput').textContent =
            Math.round(progress.currentThroughput);

        // Update progress bar
        const totalEvents = getTotalEvents();
        const percentage = Math.min(100, (progress.eventsProcessed / totalEvents) * 100);
        const progressBar = document.getElementById('progressBar');
        progressBar.style.width = percentage + '%';
        progressBar.textContent = Math.round(percentage) + '%';

        // Check if completed
        if (progress.status === 'completed') {
            document.getElementById('testStatus').innerHTML =
                '<span class="badge bg-success">Completed</span>';
            stopPolling();

            // Reload page after 2 seconds to show in history
            setTimeout(() => location.reload(), 2000);
        }

    } catch (error) {
        console.error('Error fetching progress:', error);
    }
}

function stopPolling() {
    if (pollInterval) {
        clearInterval(pollInterval);
        pollInterval = null;
    }
    document.getElementById('executeBtn').disabled = false;
    document.getElementById('executeBtn').innerHTML = '<i class="bi bi-play-fill"></i> Run Test';
}

function getTotalEvents() {
    const loadLevel = document.getElementById('loadLevel').value;
    return loadLevel === 'crisis' ? 100000 :
           loadLevel === 'peak' ? 50000 : 10000;
}