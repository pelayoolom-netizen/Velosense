// VeloSense Web & Live Simulation Controller
let isRunning = true;
let speed = 28.4;
let seconds = 2535;
let distance = 18.6;

const speedEl = document.getElementById('speedText');
const timeEl = document.getElementById('timeText');
const distEl = document.getElementById('distText');
const powerEl = document.getElementById('powerText');
const cadEl = document.getElementById('cadText');
const toggleBtn = document.getElementById('toggleRideBtn');
const coachAlert = document.getElementById('coachAlert');

// Real-time metric updater loop
setInterval(() => {
  if (!isRunning) return;
  seconds++;
  const hrs = String(Math.floor(seconds / 3600)).padStart(2, '0');
  const mins = String(Math.floor((seconds % 3600) / 60)).padStart(2, '0');
  const secs = String(seconds % 60).padStart(2, '0');
  if (timeEl) timeEl.innerText = `${hrs}:${mins}:${secs}`;

  // Realistic cycling speed fluctuation
  speed += (Math.random() - 0.49) * 0.8;
  if (speed < 18) speed = 18.5;
  if (speed > 42) speed = 41.5;
  if (speedEl) speedEl.innerText = speed.toFixed(1);

  distance += (speed / 3600);
  if (distEl) distEl.innerHTML = `${distance.toFixed(1)} <span style="font-size: 0.75rem; color: #94A3B8;">km</span>`;

  // Cadence & estimated power
  const cad = Math.round(80 + (speed - 25) * 1.5 + (Math.random() * 4 - 2));
  if (cadEl) cadEl.innerHTML = `${cad} <span style="font-size: 0.75rem; color: #94A3B8;">rpm</span>`;

  const pwr = Math.round(180 + Math.pow(speed / 10, 2.5) * 8 + (Math.random() * 10 - 5));
  if (powerEl) powerEl.innerHTML = `${pwr} <span style="font-size: 0.75rem; color: #94A3B8;">W</span>`;
}, 1000);

function toggleRide() {
  isRunning = !isRunning;
  if (!toggleBtn) return;
  if (isRunning) {
    toggleBtn.innerText = "Pausar Ruta";
    toggleBtn.style.background = "var(--accent-cyan)";
  } else {
    toggleBtn.innerText = "Reanudar Ruta";
    toggleBtn.style.background = "var(--accent-green)";
  }
}

function triggerCoach() {
  if (!coachAlert) return;
  coachAlert.style.display = coachAlert.style.display === 'none' ? 'block' : 'none';
}

// Global expose for button onclick attributes
window.toggleRide = toggleRide;
window.triggerCoach = triggerCoach;
