// ============================================
// EVAC Command Center — Dashboard Logic
// Firestore realtime listener + Leaflet map
// ============================================

// --- Firebase Config ---
// ⚠️ Replace with YOUR Firebase project config
const firebaseConfig = {
    apiKey: "YOUR_API_KEY",
    authDomain: "evac-project.firebaseapp.com",
    projectId: "evac-project",
    storageBucket: "evac-project.appspot.com",
    messagingSenderId: "000000000000",
    appId: "YOUR_APP_ID"
};

firebase.initializeApp(firebaseConfig);
const db = firebase.firestore();

// --- Color Map ---
const STATUS_COLORS = {
    MEDICAL: '#ff4444',
    TRAPPED: '#ff8f00',
    HAZARD:  '#fdd835',
    SAFE:    '#43a047'
};

const STATUS_EMOJI = {
    MEDICAL: '🏥',
    TRAPPED: '🧱',
    HAZARD:  '⚠️',
    SAFE:    '✅'
};

// --- Map Init ---
const map = L.map('map', {
    zoomControl: true,
    attributionControl: false
}).setView([17.385, 78.4867], 12);  // Default: Hyderabad

// Dark tile layer
L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
    maxZoom: 19
}).addTo(map);

// --- State ---
const markers = {};
let totalCount = 0;
let criticalCount = 0;
let safeCount = 0;

// --- Stats Update ---
function updateStats() {
    document.querySelector('#stat-total .stat-num').textContent = totalCount;
    document.querySelector('#stat-critical .stat-num').textContent = criticalCount;
    document.querySelector('#stat-safe .stat-num').textContent = safeCount;
}

// --- Create Marker ---
function createCircleMarker(data) {
    const status = data.status || 'MEDICAL';
    const color = STATUS_COLORS[status] || '#888';
    const lat = data.lat || 0;
    const lng = data.lng || 0;

    if (lat === 0 && lng === 0) return null;

    const marker = L.circleMarker([lat, lng], {
        radius: 10,
        color: color,
        fillColor: color,
        fillOpacity: 0.7,
        weight: 2
    }).addTo(map);

    // Popup content
    const emoji = STATUS_EMOJI[status] || '📍';
    const people = data.people_count || 1;
    const battery = data.battery_pct != null ? data.battery_pct : '?';
    const note = data.note || '—';
    const time = data.timestamp ? new Date(data.timestamp).toLocaleTimeString() : '—';

    marker.bindPopup(`
        <div class="popup-title">${emoji} ${status}</div>
        <div class="popup-detail">
            👥 People: <strong>${people}</strong><br>
            🔋 Battery: <strong>${battery}%</strong><br>
            📝 ${note}<br>
            🕐 ${time}<br>
            🔄 Hops: ${data.hop_count || 0}/${data.max_hops || 10}
        </div>
    `);

    return marker;
}

// --- Feed Card ---
function addFeedCard(data) {
    const feed = document.getElementById('feed');
    const status = data.status || 'MEDICAL';
    const emoji = STATUS_EMOJI[status] || '📍';
    const people = data.people_count || 1;
    const battery = data.battery_pct != null ? data.battery_pct : '?';
    const note = data.note || '';
    const time = data.timestamp ? new Date(data.timestamp).toLocaleTimeString() : '—';

    const card = document.createElement('div');
    card.className = `feed-card ${status}`;
    card.innerHTML = `
        <div class="feed-status" style="color: ${STATUS_COLORS[status]}">${emoji} ${status}</div>
        <div class="feed-detail">
            👥 ${people} people · 🔋 ${battery}%
            ${note ? '<br>📝 ' + note : ''}
        </div>
        <div class="feed-time">${time}</div>
    `;

    // Insert at top
    feed.insertBefore(card, feed.firstChild);

    // Keep max 50 cards
    while (feed.children.length > 50) {
        feed.removeChild(feed.lastChild);
    }
}

// --- Firestore Realtime Listener ---
db.collection('mesh_messages')
    .orderBy('timestamp', 'desc')
    .limit(200)
    .onSnapshot((snapshot) => {
        snapshot.docChanges().forEach((change) => {
            const data = change.doc.data();
            const id = change.doc.id;

            if (change.type === 'added') {
                // Only process SOS-type messages on the map
                if (data.type === 'SOS' || !data.type) {
                    const marker = createCircleMarker(data);
                    if (marker) {
                        markers[id] = marker;
                    }

                    // Update stats
                    totalCount++;
                    if (data.status === 'MEDICAL' || data.status === 'TRAPPED') {
                        criticalCount++;
                    }
                    if (data.status === 'SAFE') {
                        safeCount++;
                    }
                    updateStats();

                    // Add to feed
                    addFeedCard(data);
                }
            }

            if (change.type === 'modified') {
                // Update existing marker
                if (markers[id]) {
                    map.removeLayer(markers[id]);
                }
                const marker = createCircleMarker(data);
                if (marker) {
                    markers[id] = marker;
                }
            }

            if (change.type === 'removed') {
                if (markers[id]) {
                    map.removeLayer(markers[id]);
                    delete markers[id];
                    totalCount = Math.max(0, totalCount - 1);
                    updateStats();
                }
            }
        });
    }, (error) => {
        console.error('Firestore listener error:', error);
    });

// --- Auto-fit map to markers ---
setInterval(() => {
    const markerList = Object.values(markers);
    if (markerList.length > 0) {
        const group = L.featureGroup(markerList);
        map.fitBounds(group.getBounds().pad(0.3));
    }
}, 10000);

console.log('🚨 EVAC Command Center Dashboard initialized');
