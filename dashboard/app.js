// =============================================================================
// FIREBASE CONFIG
// =============================================================================

const firebaseConfig = {
    apiKey: "AIzaSyDHmS0ZHUKJWCw_FXPDfxPenHhCSKSfkgI",
    authDomain: "evac-dcb1a.firebaseapp.com",
    projectId: "evac-dcb1a",
    storageBucket: "evac-dcb1a.firebasestorage.app",
    messagingSenderId: "925392061448",
    appId: "1:925392061448:web:1351cbdacb0c318359bdbd"
};

firebase.initializeApp(firebaseConfig);
const db = firebase.firestore();
const auth = firebase.auth();

// =============================================================================
// STATE
// =============================================================================

let map = null;
const markers = {};
const sosData = {};
const respondersData = {};
let bulletinCount = 0;
let ackCount = 0;
const activityEntries = [];
let firestoreListeners = [];
let heatLayer = null;
let isHeatmapActive = false;

// =============================================================================
// AUTH
// =============================================================================

function handleLogin() {
    const email = document.getElementById('login-email').value.trim();
    const password = document.getElementById('login-password').value;
    const errorEl = document.getElementById('login-error');

    if (!email || !password) {
        errorEl.textContent = 'Please enter email and access code.';
        return;
    }

    errorEl.textContent = 'Authenticating...';
    document.getElementById('login-btn').disabled = true;

    auth.signInWithEmailAndPassword(email, password)
        .then(() => {
            errorEl.textContent = '';
        })
        .catch((err) => {
            errorEl.textContent = getAuthError(err.code);
            document.getElementById('login-btn').disabled = false;
        });
}

function handleLogout() {
    // Detach Firestore listeners
    firestoreListeners.forEach(unsub => unsub());
    firestoreListeners = [];
    auth.signOut();
}

function getAuthError(code) {
    const errors = {
        'auth/user-not-found': 'No operator found with this email.',
        'auth/wrong-password': 'Incorrect access code.',
        'auth/invalid-email': 'Invalid email format.',
        'auth/too-many-requests': 'Too many attempts. Try again later.',
        'auth/invalid-credential': 'Invalid credentials. Please try again.'
    };
    return errors[code] || 'Authentication failed. Code: ' + code;
}

// Listen for auth state changes
auth.onAuthStateChanged((user) => {
    if (user) {
        // User is signed in — show command center
        document.getElementById('login-page').classList.add('hidden');
        showCommandCenter(user);
    } else {
        // User is signed out — show login
        document.getElementById('login-page').classList.remove('hidden');
        document.getElementById('command-center').classList.remove('visible');
        document.getElementById('login-btn').disabled = false;
    }
});

// Allow Enter key on password field
document.getElementById('login-password').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') handleLogin();
});

// =============================================================================
// STARTUP + COMMAND CENTER INIT
// =============================================================================

function showCommandCenter(user) {
    // Set operator name
    document.getElementById('operator-name').textContent = user.email;

    // Show startup overlay, then reveal command center
    const overlay = document.getElementById('startup-overlay');
    overlay.classList.remove('hidden');

    setTimeout(() => {
        overlay.classList.add('hidden');
        const cc = document.getElementById('command-center');
        cc.classList.add('visible');

        // Initialize after grid is visible
        setTimeout(() => {
            initMap();
            startClock();
            startFooterDate();
            attachFirestoreListeners();
            initSafeSpots();
            addLog('🟢', 'System online. Operator authenticated.');
        }, 100);
    }, 2500);
}

// =============================================================================
// LIVE CLOCK
// =============================================================================

function startClock() {
    function tick() {
        const now = new Date();
        document.getElementById('live-clock').textContent =
            now.toLocaleTimeString('en-US', { hour12: false });
    }
    tick();
    setInterval(tick, 1000);
}

function startFooterDate() {
    const now = new Date();
    document.getElementById('footer-date').textContent =
        now.toLocaleDateString('en-US', { weekday: 'short', year: 'numeric', month: 'short', day: 'numeric' });
}

// =============================================================================
// MAP
// =============================================================================

function initMap() {
    map = L.map('map').setView([12.9716, 77.5946], 12);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap',
        maxZoom: 19
    }).addTo(map);

    map.on('click', (e) => {
        const tab = document.getElementById('safespot-tab');
        if (tab && tab.classList.contains('active')) {
            document.getElementById('spotLat').value = e.latlng.lat.toFixed(6);
            document.getElementById('spotLng').value = e.latlng.lng.toFixed(6);
        }
    });

    // Fix Leaflet sizing inside grid
    setTimeout(() => map.invalidateSize(), 200);
}

function updateMarker(id, data) {
    if (!map) return;
    if (markers[id]) map.removeLayer(markers[id]);

    if (!data.lat || !data.lng) return;

    const colorMap = {
        'MEDICAL': '#ff003c', 'TRAPPED': '#ff9500',
        'HAZARD': '#ffd600', 'SAFE': '#00ff88'
    };
    const color = colorMap[data.status] || '#666';

    const marker = L.circleMarker([data.lat, data.lng], {
        radius: 10, fillColor: color, color: '#fff',
        weight: 2, opacity: 1, fillOpacity: 0.85
    }).addTo(map);

    const popup = `
        <div style="min-width:200px; font-family: 'Exo 2', sans-serif;">
            <h3 style="margin:0 0 8px; color:${color}; font-size:15px; letter-spacing:1px;">${data.status || 'UNKNOWN'}</h3>
            <div style="display:grid; grid-template-columns:1fr 1fr; gap:6px; margin-bottom:8px;">
                <div style="background:rgba(0,0,0,0.3); padding:5px 8px; border-radius:4px; font-size:11px;">
                    <div style="color:#6a7090; font-size:9px;">PEOPLE</div>${data.people_count || 1}
                </div>
                <div style="background:rgba(0,0,0,0.3); padding:5px 8px; border-radius:4px; font-size:11px;">
                    <div style="color:#6a7090; font-size:9px;">BATTERY</div>${data.battery_pct || '?'}%
                </div>
            </div>
            <div style="background:rgba(0,0,0,0.4); padding:4px 8px; border-radius:4px; font-size:10px; color:#00d4ff; word-break:break-all; margin-bottom:8px;">
                ${data.device_id || id}
            </div>
            ${data.note ? `<div style="font-size:11px; font-style:italic; color:#999;">"${data.note}"</div>` : ''}
        </div>`;

    marker.bindPopup(popup);

    marker.on('click', () => {
        document.getElementById('ackDeviceId').value = data.device_id || id;
        switchTab('ack-tab');
    });

    markers[id] = marker;
}

function removeMarker(id) {
    if (markers[id] && map) {
        map.removeLayer(markers[id]);
        delete markers[id];
    }
}

// =============================================================================
// FIRESTORE LISTENERS
// =============================================================================

function attachFirestoreListeners() {
    // SOS listener
    const unsubSos = db.collection('sos_messages').onSnapshot((snapshot) => {
        snapshot.docChanges().forEach((change) => {
            const data = change.doc.data();
            const id = change.doc.id;

            if (change.type === 'added') {
                sosData[id] = data;
                const ts = parseTs(data.timestamp);
                addLog('🆘', `New SOS: <strong>${data.status}</strong> — ${data.people_count || 1} people at [${(data.lat || 0).toFixed(6)}, ${(data.lng || 0).toFixed(6)}]`, ts);
                updateLastSync();
            }
            if (change.type === 'modified') {
                sosData[id] = data;
                const ts = parseTs(data.timestamp);
                addLog('🔄', `SOS updated: <strong>${id.substring(0, 8)}...</strong>`, ts);
                updateLastSync();
            }
            if (change.type === 'removed') {
                delete sosData[id];
                addLog('✅', `SOS resolved: <strong>${id.substring(0, 8)}...</strong>`);
            }
        });
        refreshUi();
    }, (err) => {
        console.error('SOS listener error:', err);
        document.getElementById('sys-firestore').textContent = 'Error';
        document.getElementById('sys-firestore').style.color = '#ff003c';
    });

    // Bulletins listener (count only)
    const unsubBulletins = db.collection('bulletins').onSnapshot((snapshot) => {
        bulletinCount = snapshot.size;
        refreshUi();
    });

    // ACKs listener (count only)
    const unsubAcks = db.collection('acks').onSnapshot((snapshot) => {
        ackCount = snapshot.size;
        refreshUi();
    });

    firestoreListeners.push(unsubSos, unsubBulletins, unsubAcks);
}

function updateLastSync() {
    const now = new Date();
    document.getElementById('footer-last-update').textContent =
        'Last update: ' + now.toLocaleTimeString('en-US', { hour12: false });
}

// =============================================================================
// SOS FEED (Right Column)
// =============================================================================

function refreshUi() {
    const list = document.getElementById('sos-list');
    
    // Deduplicate by deviceId, keeping the latest document
    const deviceMap = {};
    Object.entries(sosData).forEach(([docId, data]) => {
        const dId = data.device_id || data.deviceId || docId;
        const ts = parseTs(data.timestamp) || 0;
        if (!deviceMap[dId] || ts > deviceMap[dId]._ts) {
            deviceMap[dId] = { id: docId, ...data, _ts: ts, deviceId: dId };
        }
    });

    const uniqueSos = Object.values(deviceMap);
    
    // Stats: Mesh Nodes Online = active devices reporting (min 1 for gateway itself)
    const active = uniqueSos.length;
    const critical = uniqueSos.filter(s => s.status === 'MEDICAL' || s.status === 'TRAPPED').length;
    const people = uniqueSos.reduce((sum, s) => sum + (s.people_count || s.peopleCount || 1), 0);
    
    animateNumber('stat-active', active);
    animateNumber('stat-critical', critical);
    animateNumber('stat-people', people);
    animateNumber('stat-bulletins', bulletinCount);
    animateNumber('stat-nodes', Math.max(1, active)); 

    // Map Markers: remove stale, update active
    const currentDeviceIds = new Set(uniqueSos.map(s => s.deviceId));
    Object.keys(markers).forEach(dId => {
        if (!currentDeviceIds.has(dId)) {
            removeMarker(dId);
        }
    });
    uniqueSos.forEach(sos => {
        updateMarker(sos.deviceId, sos);
    });

    // -------------------------------------------------------------------------
    // UNIFIED LIVE FEED (Combines Active SOS + System Actions)
    // -------------------------------------------------------------------------
    if (list) list.innerHTML = '';
    
    const feedItems = [];

    // 1. Add Active SOS Signals as Rich Cards
    uniqueSos.forEach(sos => {
        feedItems.push({
            type: 'SOS_CARD',
            ts: sos._ts,
            data: sos
        });
    });

    // 2. Add System Actions (Bulletins, ACKs, Resolves) from Activity Log
    // We skip 'New SOS' and 'SOS updated' logs to prevent duplicate clutter, 
    // since the Rich Cards already represent them.
    activityEntries.forEach(entry => {
        if (entry.message.includes('New SOS') || entry.message.includes('SOS updated')) return;
        feedItems.push({
            type: 'LOG_ENTRY',
            ts: entry.ts,
            data: entry
        });
    });

    // Sort descending by timestamp
    feedItems.sort((a, b) => b.ts - a.ts);

    if (feedItems.length === 0) {
        if (list) list.innerHTML = '<div class="no-data">No active signals or events</div>';
    } else {
        feedItems.forEach(item => {
            if (item.type === 'SOS_CARD') {
                const sos = item.data;
                try {
                    const div = document.createElement('div');
                    div.className = `sos-card status-${(sos.status || 'UNKNOWN').toUpperCase()}`;
                    div.onclick = () => {
                        if (sos.lat && sos.lng && map) {
                            map.flyTo([sos.lat, sos.lng], 17, { animate: true, duration: 1.5 });
                            if (markers[sos.deviceId]) markers[sos.deviceId].openPopup();
                        }
                    };
                    
                    // Show both original time and relative time
                    const msgTime = new Date(item.ts);
                    const absTime = isNaN(msgTime.getTime()) ? '\u2014' : msgTime.toLocaleTimeString('en-US', {hour12: false, hour: '2-digit', minute:'2-digit', second:'2-digit'});
                    const relTime = getTimeAgo(item.ts);
                    
                    const latStr = (sos.lat != null && sos.lng != null)
                        ? `[${Number(sos.lat).toFixed(6)}, ${Number(sos.lng).toFixed(6)}]`
                        : 'No GPS';
                    
                    div.innerHTML = `
                        <div class="sos-card-header">
                            <span class="sos-status-badge">${(sos.status || 'UNKNOWN').toUpperCase()}</span>
                            <span class="sos-time" style="font-family: var(--mono); text-align:right; line-height:1.4;">
                                <span style="font-size:11px; color:#fff;">${absTime}</span><br>
                                <span style="font-size:9px; color:#6a7090;">${relTime}</span>
                            </span>
                        </div>
                        <div style="font-size: 10px; color: #00d4ff; font-family: var(--mono); margin-bottom: 6px; word-break: break-all; display:flex; align-items:flex-start; gap:6px;">
                            <span style="flex:1;">ID: ${sos.deviceId}</span>
                            <button class="copy-btn" title="Copy Device ID" onclick="navigator.clipboard.writeText('${sos.deviceId}').then(() => { this.style.color='#00ff88'; setTimeout(()=>this.style.color='', 1000); }); event.stopPropagation();" style="background:transparent; border:none; color:var(--text-dim); cursor:pointer; font-size:12px; padding:2px;">📋</button>
                        </div>
                        <div class="sos-meta">
                            <span>👥 ${sos.people_count || sos.peopleCount || 1}</span>
                            <span>🔋 ${sos.battery_pct || sos.batteryPct || '?'}%</span>
                            <span>📍 ${latStr}</span>
                        </div>
                        ${sos.note ? `<div class="sos-note">"${sos.note}"</div>` : ''}
                    `;
                    list.appendChild(div);
                } catch (err) {
                    console.error('[SOS Feed] Error rendering card:', sos.deviceId, err);
                }
            } else if (item.type === 'LOG_ENTRY') {
                const entry = item.data;
                const div = document.createElement('div');
                div.className = 'unified-log-entry';
                // Inline styles for the system log entries in the live feed
                div.style.cssText = 'background: rgba(0,0,0,0.2); border-left: 2px solid var(--text-dim); padding: 8px 10px; margin-bottom: 8px; border-radius: 0 4px 4px 0; display: flex; align-items: flex-start; gap: 8px; font-size: 11px;';
                
                const msgTime = new Date(item.ts);
                const timeStr = isNaN(msgTime.getTime()) ? '—' : msgTime.toLocaleTimeString('en-US', {hour12: false, hour: '2-digit', minute:'2-digit', second:'2-digit'});
                
                div.innerHTML = `
                    <span style="font-size: 14px; line-height: 1;">${entry.icon}</span>
                    <div style="flex: 1;">
                        <div style="color: #fff; line-height: 1.3;">${entry.message}</div>
                    </div>
                    <span style="font-size: 10px; color: #999; font-family: var(--mono); white-space: nowrap;">${timeStr}</span>
                `;
                list.appendChild(div);
            }
        });
    }

    // Update Heatmap if active (use ALL messages for area/intensity based on # of messages)
    if (isHeatmapActive && heatLayer && map) {
        const pts = Object.values(sosData).filter(s => s.lat != null && s.lng != null).map(s => [s.lat, s.lng, 1]);
        heatLayer.setLatLngs(pts);
    }
}

function animateNumber(elId, target) {
    const el = document.getElementById(elId);
    if (!el) return;
    const current = parseInt(el.textContent) || 0;
    if (current === target) return;
    el.textContent = target;
    el.style.transform = 'scale(1.15)';
    setTimeout(() => el.style.transform = 'scale(1)', 200);
}

// =============================================================================
// ACTIVITY LOG (Center Bottom)
// =============================================================================

function addLog(icon, message, eventTs) {
    const timestamp = eventTs ? new Date(eventTs) : new Date();

    activityEntries.push({ icon, message, ts: timestamp.getTime() });
    // Sort descending (newest first)
    activityEntries.sort((a, b) => b.ts - a.ts);

    // Cap at 50 entries
    if (activityEntries.length > 50) {
        activityEntries.length = 50;
    }

    renderLogs();
}

function renderLogs() {
    const log = document.getElementById('activity-log');
    if (!log) return;
    
    log.innerHTML = '';
    
    if (activityEntries.length === 0) {
        log.innerHTML = '<div class="no-data">Waiting for events...</div>';
        return;
    }

    activityEntries.forEach(entry => {
        const time = new Date(entry.ts).toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
        const div = document.createElement('div');
        div.className = 'log-entry';
        div.innerHTML = `
            <span class="log-icon">${entry.icon}</span>
            <span class="log-time" style="font-family: var(--mono); color: #999; font-size: 11px; margin-right: 8px;">${time}</span>
            <span class="log-msg">${entry.message}</span>
        `;
        log.appendChild(div);
    });
}

function toggleHeatmap() {
    isHeatmapActive = !isHeatmapActive;
    const btn = document.getElementById('toggle-heatmap-btn');
    if (isHeatmapActive) {
        btn.textContent = 'Hide Heatmap';
        btn.style.background = '#ff003c';
        
        const pts = Object.values(sosData).filter(s => s.lat != null && s.lng != null).map(s => [s.lat, s.lng, 1]);
        heatLayer = L.heatLayer(pts, {radius: 40, blur: 25, maxZoom: 17}).addTo(map);
    } else {
        btn.textContent = 'Toggle Heatmap';
        btn.style.background = '';
        if (heatLayer && map) {
            map.removeLayer(heatLayer);
        }
        heatLayer = null;
    }
}

// =============================================================================
// TAB SWITCHING
// =============================================================================

function switchTab(tabId) {
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(tc => tc.classList.remove('active'));

    document.getElementById(tabId).classList.add('active');

    // Find corresponding button
    const buttons = document.querySelectorAll('.tab-btn');
    if (tabId === 'bulletin-tab') buttons[0].classList.add('active');
    if (tabId === 'ack-tab') buttons[1].classList.add('active');
    if (tabId === 'safespot-tab') buttons[2].classList.add('active');
}

// =============================================================================
// BULLETIN
// =============================================================================

function sendBulletin() {
    const type = document.getElementById('bulletinType').value;
    const body = document.getElementById('bulletinBody').value.trim();

    if (!body) { alert('Please enter a bulletin message.'); return; }

    const bulletin = {
        id: 'bulletin_' + Date.now(),
        type: 'BULLETIN',
        alert_type: type,
        body: body,
        timestamp: Date.now(),
        ttlHours: 12,
        hopCount: 0,
        maxHops: 10,
        sent_by: auth.currentUser ? auth.currentUser.email : 'unknown',
        signature: 'demo_signature'
    };

    db.collection('bulletins').add(bulletin)
        .then(() => {
            document.getElementById('bulletinBody').value = '';
            addLog('📢', `Bulletin sent: <strong>${type}</strong> — "${body.substring(0, 50)}..."`);
        })
        .catch((err) => {
            alert('Failed to send bulletin: ' + err.message);
            addLog('❌', `Bulletin failed: ${err.message}`);
        });
}

// =============================================================================
// ACK
// =============================================================================

function sendAck() {
    const deviceId = document.getElementById('ackDeviceId').value.trim();
    const body = document.getElementById('ackBody').value.trim();

    if (!deviceId) { alert('Please select a device or enter device ID.'); return; }
    if (!body) { alert('Please enter a response message.'); return; }

    const ack = {
        id: 'ack_' + Date.now(),
        type: 'ACK',
        targetDeviceId: deviceId,
        body: body,
        timestamp: Date.now(),
        ttlHours: 6,
        hopCount: 0,
        maxHops: 10,
        sent_by: auth.currentUser ? auth.currentUser.email : 'unknown',
        signature: 'demo_signature'
    };

    db.collection('acks').add(ack)
        .then(() => {
            document.getElementById('ackDeviceId').value = '';
            document.getElementById('ackBody').value = '';
            addLog('✅', `ACK sent to device: <strong>${deviceId.substring(0, 16)}...</strong>`);
        })
        .catch((err) => {
            alert('Failed to send ACK: ' + err.message);
            addLog('❌', `ACK failed: ${err.message}`);
        });
}

// Manual cleanup of documents older than 4 hours
async function cleanupOldLogs() {
    const confirmDelete = confirm("This will permanently delete ALL messages older than 4 hours from the database. Proceed?");
    if (!confirmDelete) return;

    const fourHoursAgo = Date.now() - (4 * 60 * 60 * 1000);
    addLog('🧹', 'Cleaning up database logs...');

    const collections = ['sos_messages', 'bulletins', 'acks'];
    let count = 0;

    try {
        for (const coll of collections) {
            const snapshot = await db.collection(coll).get();
            for (const doc of snapshot.docs) {
                const ts = parseTs(doc.data().timestamp);
                if (ts < fourHoursAgo) {
                    await db.collection(coll).doc(doc.id).delete();
                    count++;
                }
            }
        }
        addLog('✅', `Cleanup finished. Removed ${count} old documents.`);
        alert(`Cleanup complete. Deleted ${count} records older than 4 hours.`);
    } catch (err) {
        console.error('Cleanup error:', err);
        addLog('❌', 'Cleanup failed: ' + err.message);
    }
}

// =============================================================================
// UTILITIES
// =============================================================================

function parseTs(ts) {
    if (!ts) return 0;
    if (typeof ts === 'string') return new Date(ts).getTime();
    if (typeof ts === 'object' && ts.seconds) return ts.seconds * 1000; // Firestore Timestamp

    // Fix for old data stored with IST timezone bug (off by +5:30 = 19800000ms).
    // Old Android code stored local-time epoch instead of UTC epoch.
    // If timestamp is more than 5h30m behind current time, check if adding offset fixes it.
    const IST_OFFSET_MS = 19800000; // 5 hours 30 minutes
    const corrected = ts + IST_OFFSET_MS;
    // Use corrected if it's in the past but closer to now (old bug data)
    // If corrected would be in the future, use original (already fixed data)
    if (corrected <= Date.now()) {
        return corrected;
    }
    return ts;
}

function getTimeAgo(timestamp) {
    if (!timestamp) return '—';
    const diff = Date.now() - timestamp;
    if (diff < 0) return 'Just now';
    const secs = Math.floor(diff / 1000);
    const mins = Math.floor(secs / 60);
    const hrs = Math.floor(mins / 60);
    const days = Math.floor(hrs / 24);
    if (days > 0) return `${days}d ago`;
    if (hrs > 0) return `${hrs}h ${mins % 60}m ago`;
    if (mins > 0) return `${mins}m ago`;
    if (secs >= 10) return `${secs}s ago`;
    return 'Just now';
}

// =============================================================================
// RESPONDERS UI
// =============================================================================

function refreshRespondersUi() {
    const container = document.getElementById('responder-list');
    if (!container) return;

    const responders = Object.values(respondersData);

    if (responders.length === 0) {
        container.innerHTML = '<div class="no-data">No responders currently active</div>';
        return;
    }

    container.innerHTML = '';

    responders.forEach(r => {
        const email = r.email || 'Unknown';

        // Count assigned (non-SAFE) victims for this responder
        const assignedVictims = Object.values(sosData).filter(
            s => (s.assigned_to === email || s.assignedTo === email) && s.status !== 'SAFE'
        );
        // Count saved victims for this responder
        const savedVictims = Object.values(sosData).filter(
            s => (s.assigned_to === email || s.assignedTo === email) && s.status === 'SAFE'
        );

        const lastSeen = r.timestamp ? getTimeAgo(r.timestamp) : '—';
        const lat = r.lat ? r.lat.toFixed(5) : '?';
        const lng = r.lng ? r.lng.toFixed(5) : '?';

        const card = document.createElement('div');
        card.style.cssText = 'background: rgba(0,212,255,0.05); border: 1px solid rgba(0,212,255,0.15); border-radius: 6px; padding: 10px 12px; margin-bottom: 8px;';
        card.innerHTML = `
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
                <span style="color:#00d4ff; font-family:var(--mono); font-size:11px; font-weight:bold;">🛡️ ${email}</span>
                <span style="background:rgba(0,255,136,0.15); color:#00ff88; padding:1px 6px; border-radius:3px; font-size:9px; font-family:var(--mono);">LIVE</span>
            </div>
            <div style="display:grid; grid-template-columns:1fr 1fr 1fr; gap:6px; margin-bottom:6px;">
                <div style="background:rgba(255,0,60,0.1); border:1px solid rgba(255,0,60,0.2); border-radius:4px; padding:6px; text-align:center;">
                    <div style="font-size:16px; font-weight:bold; color:#ff003c;">${assignedVictims.length}</div>
                    <div style="font-size:8px; color:#6a7090; text-transform:uppercase; letter-spacing:1px;">Assigned</div>
                </div>
                <div style="background:rgba(0,255,136,0.1); border:1px solid rgba(0,255,136,0.2); border-radius:4px; padding:6px; text-align:center;">
                    <div style="font-size:16px; font-weight:bold; color:#00ff88;">${savedVictims.length}</div>
                    <div style="font-size:8px; color:#6a7090; text-transform:uppercase; letter-spacing:1px;">Saved</div>
                </div>
                <div style="background:rgba(0,212,255,0.1); border:1px solid rgba(0,212,255,0.2); border-radius:4px; padding:6px; text-align:center;">
                    <div style="font-size:16px; font-weight:bold; color:#00d4ff;">${assignedVictims.length + savedVictims.length}</div>
                    <div style="font-size:8px; color:#6a7090; text-transform:uppercase; letter-spacing:1px;">Total</div>
                </div>
            </div>
            <div style="display:flex; justify-content:space-between; font-size:9px; color:#6a7090; font-family:var(--mono);">
                <span>📍 [${lat}, ${lng}]</span>
                <span>Last ping: ${lastSeen}</span>
            </div>
        `;
        container.appendChild(card);
    });
}


// =============================================================================
// HIDE STARTUP ON INITIAL LOAD (if not logged in)
// =============================================================================

window.addEventListener('load', () => {
    // If no user, just hide the startup overlay immediately
    setTimeout(() => {
        if (!auth.currentUser) {
            document.getElementById('startup-overlay').classList.add('hidden');
        }
    }, 300);
});

console.log('🚨 EVAC Command Center v1.0 loaded.');

// =============================================================================
// SAFE SPOTS
// =============================================================================

let safeSpots = [];
let spotMarkers = {};

function initSafeSpots() {
    firestoreListeners.push(
        db.collection('safespots').onSnapshot((snapshot) => {
            safeSpots = [];
            snapshot.forEach(doc => {
                safeSpots.push(doc.data());
            });
            renderSafeSpots();
            renderSpotMarkers();
        }, (err) => {
            console.error('SafeSpot listener error:', err);
        })
    );
}

function addSafeSpot() {
    const name = document.getElementById('spotName').value.trim();
    const lat = parseFloat(document.getElementById('spotLat').value);
    const lng = parseFloat(document.getElementById('spotLng').value);
    const type = document.getElementById('spotType').value;

    if (!name || isNaN(lat) || isNaN(lng)) {
        alert('Please provide Name, Latitude, and Longitude. Click on the map to set coordinates automatically.');
        return;
    }

    const spot = {
        id: (typeof crypto !== 'undefined' && crypto.randomUUID) ? crypto.randomUUID() : 'spot_' + Date.now(),
        name: name,
        latitude: lat,
        longitude: lng,
        type: type,
        is_active: true,
        updated_at: Date.now(),
        signature: null
    };

    db.collection('safespots').doc(spot.id).set(spot)
        .then(() => {
            addLog('📍', `SafeSpot added: <strong>${name}</strong>`);
        })
        .catch(err => {
            alert('Failed to add SafeSpot: ' + err.message);
        });

    document.getElementById('spotName').value = '';
    document.getElementById('spotLat').value = '';
    document.getElementById('spotLng').value = '';
}

function removeSafeSpot(id) {
    if(!confirm('Are you sure you want to delete this SafeSpot?')) return;
    const spot = safeSpots.find(s => s.id === id);
    
    db.collection('safespots').doc(id).delete()
        .then(() => {
            if(spot) addLog('🗑️', `SafeSpot removed: <strong>${spot.name}</strong>`);
        })
        .catch(err => {
            alert('Failed to delete SafeSpot: ' + err.message);
        });
}

function renderSafeSpots() {
    const container = document.getElementById('safespot-registry');
    if (!container) return;

    if (safeSpots.length === 0) {
        container.innerHTML = '<div style="font-size:11px; color:#6a7090; text-align:center; padding-top: 10px;">No SafeSpots added. Click map to define.</div>';
        return;
    }

    const sorted = [...safeSpots].sort((a,b) => b.updated_at - a.updated_at);

    container.innerHTML = sorted.map(s => `
        <div style="background:rgba(0,212,255,0.05); padding:10px; border-radius:6px; margin-bottom:8px; border: 1px solid rgba(0,212,255,0.1); display:flex; justify-content:space-between; align-items:center;">
            <div>
                <strong style="font-size:12px; color:var(--primary);">${s.name}</strong>
                <div style="font-size:9px; color:var(--text-dim); margin-top:2px;">
                    ${s.type} · [${s.latitude.toFixed(4)}, ${s.longitude.toFixed(4)}]
                </div>
            </div>
            <button onclick="removeSafeSpot('${s.id}')" style="background:none; border:none; padding:4px; font-size:14px; cursor:pointer;" title="Delete">🗑️</button>
        </div>
    `).join('');
}

function renderSpotMarkers() {
    Object.values(spotMarkers).forEach(m => map.removeLayer(m));
    spotMarkers = {};

    safeSpots.forEach(s => {
        const typeColors = {
            'SHELTER': '#00d4ff', 'MEDICAL': '#ff003c',
            'FOOD': '#00ff88', 'WATER': '#00d4ff', 'POLICE': '#ff9500'
        };
        const color = typeColors[s.type] || '#00d4ff';

        const emojis = {
            'SHELTER': '🏠', 'MEDICAL': '🏥',
            'FOOD': '🍽️', 'WATER': '💧', 'POLICE': '🚔'
        };
        const emoji = emojis[s.type] || '📍';

        const customIcon = L.divIcon({
            html: `<div style="font-size:20px; text-shadow:0 0 5px #000; padding:2px; background: ${color}40; border-radius:50%; border:1px solid ${color}; display:flex; align-items:center; justify-content:center; width:30px; height:30px;">${emoji}</div>`,
            className: 'safespot-custom-icon',
            iconSize: [36, 36],
            iconAnchor: [18, 18]
        });

        const marker = L.marker([s.latitude, s.longitude], { icon: customIcon }).addTo(map);

        marker.bindPopup(`
            <div style="font-family: var(--sans);">
                <strong style="color: ${color}; font-size:14px;">${s.name}</strong><br>
                <div style="font-size:11px; margin-top:4px;">Type: ${s.type}</div>
                <div style="font-size:10px; color:#aaa;">${s.latitude.toFixed(4)}, ${s.longitude.toFixed(4)}</div>
            </div>
        `);
        spotMarkers[s.id] = marker;
    });
}

function exportSafeSpotsJson() {
    if (safeSpots.length === 0) { alert('No safe spots to export.'); return; }
    const json = JSON.stringify(safeSpots, null, 2);
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = 'safespots.json'; a.click();
    URL.revokeObjectURL(url);
    addLog('📦', `Exported ${safeSpots.length} SafeSpots to JSON.`);
}

function importSafeSpotsJson(event) {
    const file = event.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (e) => {
        try {
            const data = JSON.parse(e.target.result);
            if (!Array.isArray(data)) throw new Error("Expected JSON array");
            let inserted = 0;
            data.forEach(item => {
                if (item.id && item.name && item.latitude != null && item.longitude != null) {
                    db.collection('safespots').doc(item.id).set(item);
                    inserted++;
                }
            });
            addLog('📂', `Imported ${inserted} SafeSpots from JSON.`);
        } catch (err) {
            alert('Import failed: ' + err.message);
        }
    };
    reader.readAsText(file);
}