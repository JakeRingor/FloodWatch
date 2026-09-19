const { createClient } = supabase;

const SUPABASE_URL = 'https://jhjkfkgixkqbofehwwtn.supabase.co';
const SUPABASE_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Impoamtma2dpeGtxYm9mZWh3d3RuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzUzMjI2MTQsImV4cCI6MjA5MDg5ODYxNH0._rQSdzdaMt97KqP_QLdu2E9LmRS3MqdeQz_EUVpVISY';

const sb = createClient(SUPABASE_URL, SUPABASE_KEY);
const ADMIN_EMAIL = 'floodwatchstaana@gmail.com';

let currentTab = 'PENDING';
let selectedSeverity = 'ADVISORY';
let allReports = { PENDING: [], VERIFIED: [], DISMISSED: [], INVALID_IMAGE: [], INVALID_INFORMATION: [] };
let reportsRealtimeChannel = null;
let alertsRealtimeChannel = null;
let realtimeRefreshTimer = null;

// ── VERIFY MODAL ──────────────────────────────────────────────
let pendingVerifyId = null;

function openVerifyModal(id) {
  pendingVerifyId = id;
  document.getElementById('verifyModal').classList.add('open');
}

function closeVerifyModal() {
  pendingVerifyId = null;
  document.getElementById('verifyModal').classList.remove('open');
}

async function confirmVerify(category) {
  if (!pendingVerifyId) return;
  const id = pendingVerifyId;
  closeVerifyModal();

  const { error } = await sb.from('flood_reports')
    .update({ status: category })
    .eq('id', id);

  if (error) { showToast('Error: ' + error.message, 'error'); return; }

  const labels = {
    VERIFIED: '✅ Report verified!',
    DISMISSED: '❌ Report dismissed',
    INVALID_IMAGE: '🖼️ Marked as Invalid Image',
    INVALID_INFORMATION: '📋 Marked as Invalid Information'
  };
  showToast(labels[category] || 'Updated', 'success');
  loadAllReports();
}

// ── AUTH ──────────────────────────────────────────────────────
async function adminLogin() {
  const email = document.getElementById('adminEmail').value.trim();
  const pass  = document.getElementById('adminPassword').value;
  document.getElementById('loginError').textContent = '';

  if (!email || !pass) {
    document.getElementById('loginError').textContent = 'Please fill in all fields.';
    return;
  }

  const btn = document.querySelector('.btn-primary');
  btn.textContent = 'Signing in…';
  btn.disabled = true;

  const { data, error } = await sb.auth.signInWithPassword({ email, password: pass });

  if (error) {
    document.getElementById('loginError').textContent = error.message;
    btn.textContent = 'Sign In →';
    btn.disabled = false;
  } else if (data.user.email?.toLowerCase() !== ADMIN_EMAIL.toLowerCase()) {
    await sb.auth.signOut();
    document.getElementById('loginError').textContent = 'This account is not authorized for the admin panel.';
    btn.textContent = 'Sign In →';
    btn.disabled = false;
  } else {
    document.getElementById('adminEmailDisplay').textContent = data.user.email;
    showDashboard();
  }
}

async function logout() {
  if (reportsRealtimeChannel) await sb.removeChannel(reportsRealtimeChannel);
  if (alertsRealtimeChannel) await sb.removeChannel(alertsRealtimeChannel);
  await sb.auth.signOut();
  location.reload();
}

function showDashboard() {
  document.getElementById('loginScreen').style.display = 'none';
  document.getElementById('dashboard').style.display = 'block';
  loadAllReports();
  loadActiveAlerts();
  setupRealtimeRefresh();
}

function setupRealtimeRefresh() {
  if (!reportsRealtimeChannel) {
    reportsRealtimeChannel = sb.channel('admin-report-refresh')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'flood_reports' }, () => {
        clearTimeout(realtimeRefreshTimer);
        realtimeRefreshTimer = setTimeout(loadAllReports, 250);
      })
      .subscribe();
  }
  if (!alertsRealtimeChannel) {
    alertsRealtimeChannel = sb.channel('admin-alert-refresh')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'flood_alerts' }, loadActiveAlerts)
      .subscribe();
  }
}

// ── REPORTS ───────────────────────────────────────────────────
async function loadAllReports() {
  const { data, error } = await sb
    .from('flood_reports')
    .select('*')
    .order('created_at', { ascending: false });

  if (error) { showToast('Error loading reports', 'error'); return; }

  allReports.PENDING             = data.filter(r => normalizeStatus(r.status) === 'PENDING');
  allReports.VERIFIED            = data.filter(r => normalizeStatus(r.status) === 'VERIFIED');
  allReports.DISMISSED           = data.filter(r => normalizeStatus(r.status) === 'DISMISSED');
  allReports.INVALID_IMAGE       = data.filter(r => normalizeStatus(r.status) === 'INVALID_IMAGE');
  allReports.INVALID_INFORMATION = data.filter(r => normalizeStatus(r.status) === 'INVALID_INFORMATION');

  const historyCount = allReports.DISMISSED.length + allReports.INVALID_IMAGE.length + allReports.INVALID_INFORMATION.length;

  document.getElementById('statPending').textContent      = allReports.PENDING.length;
  document.getElementById('statVerified').textContent     = allReports.VERIFIED.length;
  document.getElementById('statHistory').textContent      = historyCount;
  document.getElementById('tabCountPending').textContent  = allReports.PENDING.length;
  document.getElementById('tabCountVerified').textContent = allReports.VERIFIED.length;
  document.getElementById('tabCountHistory').textContent  = historyCount;

  renderReports(currentTab);
}

function renderReports(tab) {
  const list = document.getElementById('reportsList');

  let reports;
  if (tab === 'HISTORY') {
    reports = [
      ...(allReports.DISMISSED || []),
      ...(allReports.INVALID_IMAGE || []),
      ...(allReports.INVALID_INFORMATION || [])
    ]
      .sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
  } else {
    reports = allReports[tab] || [];
  }

  if (!reports.length) {
    const labels = { PENDING: 'pending', VERIFIED: 'verified', HISTORY: 'history' };
    list.innerHTML = `<div class="empty-state"><div class="icon">📭</div><p>No ${labels[tab] || tab.toLowerCase()} reports</p></div>`;
    return;
  }

  const statusBadgeClass = s => {
    if (s === 'VERIFIED')            return 'badge-verified';
    if (s === 'DISMISSED')           return 'badge-dismissed';
    if (s === 'INVALID_IMAGE')       return 'badge-invalid';
    if (s === 'INVALID_INFORMATION') return 'badge-invalid';
    return 'badge-pending';
  };

  const statusLabel = s => {
    if (s === 'DISMISSED')           return '❌ Dismissed';
    if (s === 'INVALID_IMAGE')       return '🖼️ Invalid Image';
    if (s === 'INVALID_INFORMATION') return '📋 Invalid Info';
    return s;
  };

  list.innerHTML = reports.map(r => {
    const safeId = encodeURIComponent(String(r.id || ''));
    const safeImageUrl = sanitizeImageUrl(r.image_url);
    const encodedImageUrl = encodeURIComponent(safeImageUrl);
    const severity = Math.min(Math.max(Number(r.severity) || 0, 0), 4);
    return `
  <div class="report-card">
    <div class="report-card-header">
      <div class="report-meta">
        <span class="badge ${statusBadgeClass(normalizeStatus(r.status))}">${escapeHtml(statusLabel(normalizeStatus(r.status)))}</span>
        ${severity ? `<span class="severity-dot sev-${severity}"></span>` : ''}
      </div>
      <span class="report-date">${formatDate(r.created_at)}</span>
    </div>
    <div class="report-content">
      ${safeImageUrl ? `
        <img class="report-thumb"
          src="${escapeAttribute(safeImageUrl)}"
          alt="Report photo"
          data-lightbox-url="${escapeAttribute(encodedImageUrl)}"
          onclick="openLightbox(decodeURIComponent(this.dataset.lightboxUrl))"
          onerror="this.style.display='none'"/>
      ` : ''}
      <div class="report-info">
        <div class="report-flood-level">💧 ${escapeHtml(r.flood_level || 'Unknown Level')}</div>
        <div class="report-address">📍 ${escapeHtml(r.address || 'No address provided')}</div>
        ${r.description ? `<div class="report-description">${escapeHtml(r.description)}</div>` : ''}
      </div>
    </div>
    ${tab === 'PENDING' ? `
      <div class="report-actions">
        <button class="btn-verify" onclick="openVerifyModal(decodeURIComponent('${safeId}'))">✅ Verify / Categorize</button>
      </div>` : ''}
    ${tab === 'VERIFIED' ? `
      <div class="report-actions">
        <button class="btn-verify" onclick="updateReport(decodeURIComponent('${safeId}'), 'PENDING')">↩️ Unverify</button>
      </div>` : ''}
    ${tab === 'HISTORY' ? `
      <div class="report-actions">
        <button class="btn-verify" onclick="updateReport(decodeURIComponent('${safeId}'), 'PENDING')">↩️ Restore</button>
        <button class="btn-dismiss" onclick="deleteReport(decodeURIComponent('${safeId}'))">🗑️ Delete</button>
      </div>` : ''}
  </div>`;
  }).join('');
}

async function updateReport(id, status) {
  const { error } = await sb.from('flood_reports').update({ status }).eq('id', id);
  if (error) { showToast('Error: ' + error.message, 'error'); return; }
  showToast('Updated!', 'success');
  loadAllReports();
}


function switchTab(status, el) {
  currentTab = status;
  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
  el.classList.add('active');
  renderReports(status);
}

async function deleteReport(id) {
  if (!confirm('Delete this report permanently?')) return;
  const { error } = await sb.from('flood_reports').delete().eq('id', id);
  if (error) { showToast('Error: ' + error.message, 'error'); return; }
  showToast('🗑️ Report deleted!', 'success');
  loadAllReports();
}

// ── ALERTS ────────────────────────────────────────────────────
function selectSeverity(sev, el) {
  selectedSeverity = sev;
  document.querySelectorAll('.sev-option').forEach(o => o.classList.remove('selected'));
  el.classList.add('selected');
}

async function postAlert() {
  const title   = document.getElementById('alertTitle').value.trim();
  const message = document.getElementById('alertMessage').value.trim();

  if (!title || !message) { showToast('Please fill in title and message', 'error'); return; }

  const btn = document.querySelector('.btn-post-alert');
  btn.textContent = 'Posting…';
  btn.disabled = true;

  const { error } = await sb.from('flood_alerts').insert({
    title, message,
    severity: selectedSeverity,
    is_active: true
  });

  btn.textContent = 'Post Alert →';
  btn.disabled = false;

  if (error) { showToast('Error: ' + error.message, 'error'); return; }

  showToast('📢 Alert posted!', 'success');
  document.getElementById('alertTitle').value = '';
  document.getElementById('alertMessage').value = '';
  loadActiveAlerts();
}

async function loadActiveAlerts() {
  const { data, error } = await sb
    .from('flood_alerts')
    .select('*')
    .eq('is_active', true)
    .order('created_at', { ascending: false });

  const list = document.getElementById('activeAlertsList');
  document.getElementById('statAlerts').textContent = data?.length || 0;

  if (error || !data?.length) {
    list.innerHTML = '<div style="color:var(--text-dim);font-size:12px;padding:8px 0">No active alerts</div>';
    return;
  }

  list.innerHTML = data.map(a => {
    const safeId = encodeURIComponent(String(a.id || ''));
    const title = escapeHtml(a.title || 'Untitled alert');
    const severity = escapeHtml(a.severity || 'ADVISORY');
    const rawMessage = String(a.message || '');
    const preview = escapeHtml(rawMessage.substring(0, 50));
    return `
    <div class="alert-item">
      <div class="alert-item-info">
        <div class="title">${title}</div>
        <div class="msg">${severity} · ${preview}${rawMessage.length > 50 ? '…' : ''}</div>
      </div>
      <button class="btn-deactivate" onclick="deactivateAlert(decodeURIComponent('${safeId}'))">Deactivate</button>
    </div>
  `;
  }).join('');
}

async function deactivateAlert(id) {
  const { error } = await sb.from('flood_alerts').update({ is_active: false }).eq('id', id);
  if (error) { showToast('Error: ' + error.message, 'error'); return; }
  showToast('Alert deactivated', '');
  loadActiveAlerts();
}

// ── LIGHTBOX ──────────────────────────────────────────────────
function openLightbox(url) {
  const lightbox = document.getElementById('lightbox');
  const img      = document.getElementById('lightboxImg');

  img.style.opacity = '0';
  img.style.transform = 'scale(0.95)';
  img.src = '';
  lightbox.classList.add('open');

  const newImg = new Image();
  newImg.onload = () => {
    img.src = url;
    requestAnimationFrame(() => {
      img.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
      img.style.opacity = '1';
      img.style.transform = 'scale(1)';
    });
  };
  newImg.onerror = () => {
    img.src = url;
    img.style.opacity = '1';
    img.style.transform = 'scale(1)';
  };
  newImg.src = url;
}

function closeLightbox() {
  const lightbox = document.getElementById('lightbox');
  const img      = document.getElementById('lightboxImg');

  img.style.transition = 'opacity 0.2s ease, transform 0.2s ease';
  img.style.opacity = '0';
  img.style.transform = 'scale(0.95)';

  setTimeout(() => {
    lightbox.classList.remove('open');
    img.src = '';
    img.style.transition = '';
    img.style.opacity = '0';
    img.style.transform = 'scale(0.95)';
  }, 200);
}

// ── UTILS ─────────────────────────────────────────────────────
function formatDate(str) {
  if (!str) return '';
  const date = new Date(str);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleDateString('en-PH', {
    month: 'short', day: 'numeric', year: 'numeric'
  });
}

function normalizeStatus(value) {
  return String(value || 'PENDING').trim().toUpperCase();
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function escapeAttribute(value) {
  return escapeHtml(value).replaceAll('`', '&#096;');
}

function sanitizeImageUrl(value) {
  if (!value) return '';
  try {
    const url = new URL(String(value), window.location.href);
    return ['https:', 'http:'].includes(url.protocol) ? url.href : '';
  } catch (_) {
    return '';
  }
}

function showToast(msg, type) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.className = `toast ${type} show`;
  setTimeout(() => t.className = 'toast', 3000);
}

// ── INIT ──────────────────────────────────────────────────────
sb.auth.getSession().then(({ data }) => {
  if (data.session?.user?.email?.toLowerCase() === ADMIN_EMAIL.toLowerCase()) {
    document.getElementById('adminEmailDisplay').textContent = data.session.user.email;
    showDashboard();
  } else if (data.session) {
    sb.auth.signOut();
  }
});

document.addEventListener('DOMContentLoaded', () => {
  document.getElementById('adminPassword').addEventListener('keydown', e => {
    if (e.key === 'Enter') adminLogin();
  });

  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') closeLightbox();
  });
});
