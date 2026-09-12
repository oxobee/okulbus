// Okulbus Akıllı Servis Takip ve Yönetim Sistemi
// Frontend State, Simulation & Real Management (CRUD) Engine

// 1. DEMO HESAP TANIMLARI
const DEMO_ACCOUNTS = {
    admin: {
        role: 'admin',
        title: 'Okul Yöneticisi',
        email: 'yonetici@okulbus.com',
        pass: 'okulbus123',
        name: 'Murat Erdem (Müdür Yardımcısı)'
    },
    driver: {
        role: 'driver',
        title: 'Servis Şoförü',
        email: 'sofor@okulbus.com',
        pass: 'okulbus123',
        name: 'Mehmet Kaptan',
        busPlate: '34 OKL 001'
    },
    parent: {
        role: 'parent',
        title: 'Öğrenci Velisi',
        email: 'veli@okulbus.com',
        pass: 'okulbus123',
        name: 'Selin Yılmaz (Ahmet Yılmaz\'ın Velisi)'
    }
};

// 2. SERVİS FİLOSU VERİLERİ (VARSAYILAN & LOCALSTORAGE)
const DEFAULT_FLEET = [
    { plate: '34 OKL 001', driver: 'Mehmet Kaptan', phone: '0532 111 22 33', route: 'Kadıköy - Ataşehir', cap: '22/22', speed: '38 km/s', status: 'Yolda', lat: 40.9930, lng: 29.1120 },
    { plate: '34 OKL 002', driver: 'Ali Yıldız', phone: '0533 222 33 44', route: 'Üsküdar - Çamlıca', cap: '18/20', speed: '42 km/s', status: 'Yolda', lat: 41.0250, lng: 29.0450 },
    { plate: '34 OKL 003', driver: 'Hasan Aksoy', phone: '0535 333 44 55', route: 'Maltepe - Bostancı', cap: '24/24', speed: '0 km/s', status: 'Durakta', lat: 40.9420, lng: 29.1450 },
    { plate: '34 OKL 004', driver: 'Kemal Çetin', phone: '0536 444 55 66', route: 'Ümraniye - Çekmeköy', cap: '19/22', speed: '45 km/s', status: 'Yolda', lat: 41.0320, lng: 29.1680 },
    { plate: '34 OKL 005', driver: 'Serkan Kurt', phone: '0537 555 66 77', route: 'Kartal - Pendik', cap: '21/22', speed: '35 km/s', status: 'Yolda', lat: 40.8980, lng: 29.2310 },
    { plate: '34 OKL 006', driver: 'Okan Şahin', phone: '0538 666 77 88', route: 'Beşiktaş - Levent', cap: '16/18', speed: '28 km/s', status: 'Yolda', lat: 41.0710, lng: 29.0120 },
    { plate: '34 OKL 007', driver: 'Murat Koç', phone: '0539 777 88 99', route: 'Sarıyer - Maslak', cap: '20/20', speed: '40 km/s', status: 'Yolda', lat: 41.1150, lng: 29.0350 },
    { plate: '34 OKL 008', driver: 'Burak Arslan', phone: '0541 888 99 00', route: 'Beykoz - Kavacık', cap: '15/18', speed: '32 km/s', status: 'Yolda', lat: 41.0920, lng: 29.0950 }
];

let FLEET_DATA = JSON.parse(localStorage.getItem('okulbus_fleet')) || DEFAULT_FLEET;

function saveFleet() {
    localStorage.setItem('okulbus_fleet', JSON.stringify(FLEET_DATA));
}

// 3. ÖĞRENCİ LİSTESİ VERİLERİ (VARSAYILAN & LOCALSTORAGE)
const DEFAULT_STUDENTS = [
    { id: 1, name: 'Ahmet Yılmaz', grade: '4-B', bus: '34 OKL 001', stop: 'Ataşehir Migros Durağı', parentName: 'Selin Yılmaz', parentPhone: '0532 999 11 22', status: 'Bindi (07:42)' },
    { id: 2, name: 'Elif Demir', grade: '3-A', bus: '34 OKL 001', stop: 'Batı Ataşehir Konutları', parentName: 'Fatma Demir', parentPhone: '0533 888 22 33', status: 'Bindi (07:46)' },
    { id: 3, name: 'Can Öztürk', grade: '5-C', bus: '34 OKL 001', stop: 'Ataşehir Bulvarı', parentName: 'Tolga Öztürk', parentPhone: '0535 777 33 44', status: 'Bekleniyor' },
    { id: 4, name: 'Zeynep Kaya', grade: '2-B', bus: '34 OKL 001', stop: 'Brandium Durağı', parentName: 'Merve Kaya', parentPhone: '0536 666 44 55', status: 'Bekleniyor' },
    { id: 5, name: 'Emre Şen', grade: '6-A', bus: '34 OKL 001', stop: 'İçerenköy Çıkışı', parentName: 'Gökhan Şen', parentPhone: '0537 555 55 66', status: 'Bindi (07:35)' },
    { id: 6, name: 'Buse Aydın', grade: '1-A', bus: '34 OKL 002', stop: 'Kısıklı Meydan', parentName: 'Nur Aydın', parentPhone: '0538 444 66 77', status: 'Bindi (07:40)' },
    { id: 7, name: 'Mert Aktaş', grade: '7-B', bus: '34 OKL 003', stop: 'Bostancı İskele', parentName: 'Hakan Aktaş', parentPhone: '0539 333 77 88', status: 'Bindi (07:38)' },
    { id: 8, name: 'Yağmur Koç', grade: '4-C', bus: '34 OKL 004', stop: 'Madenler Meydan', parentName: 'Pınar Koç', parentPhone: '0540 222 88 99', status: 'Bindi (07:44)' }
];

let STUDENTS_DATA = JSON.parse(localStorage.getItem('okulbus_students')) || DEFAULT_STUDENTS;

function saveStudents() {
    localStorage.setItem('okulbus_students', JSON.stringify(STUDENTS_DATA));
}

// 4. ŞOFÖR DURAK GÜZERGAHI
const DRIVER_STOPS = [
    { title: '1. Kadıköy Rıhtım Kalkış', time: '07:15', done: true },
    { title: '2. Kozyatağı E-5 Durağı', time: '07:30', done: true },
    { title: '3. Ataşehir Migros Kavşağı (Şu Anki Durak)', time: '07:45', active: true },
    { title: '4. Batı Ataşehir Konutları', time: '07:55', done: false },
    { title: '5. Brandium Önü', time: '08:05', done: false },
    { title: '6. Okul Kampüsü Varış', time: '08:15', done: false }
];

// GLOBAL INSTANCES
let currentRole = null;
let adminMapInstance = null;
let driverMapInstance = null;
let parentMapInstance = null;
let mapMarkers = [];

// ==========================================
// TOAST BİLDİRİM FONKSİYONU
// ==========================================
function showToast(message, type = 'success') {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    
    let icon = 'fa-check-circle';
    if (type === 'error' || type === 'sos') icon = 'fa-exclamation-triangle';
    if (type === 'info') icon = 'fa-info-circle';

    toast.innerHTML = `<i class="fas ${icon}"></i> <span>${message}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.classList.add('toast-show');
    }, 50);

    setTimeout(() => {
        toast.classList.remove('toast-show');
        setTimeout(() => toast.remove(), 400);
    }, 4000);
}

// ==========================================
// GİRİŞ & TEST MANTIĞI
// ==========================================
function quickLogin(role) {
    if (!DEMO_ACCOUNTS[role]) return;
    const account = DEMO_ACCOUNTS[role];
    currentRole = role;

    document.getElementById('landingView').style.display = 'none';
    document.getElementById('dashboardView').style.display = 'block';

    document.getElementById('currentRoleTag').innerText = account.title.toUpperCase();
    document.getElementById('currentUserEmail').innerText = account.email;

    document.getElementById('navAuthArea').innerHTML = `
        <div class="user-pill" onclick="quickLogin('${role}')">
            <span class="user-pill-dot"></span>
            <span>${account.title}</span>
        </div>
        <button class="btn-logout-nav" onclick="logout()" title="Çıkış Yap">
            <i class="fas fa-sign-out-alt"></i>
        </button>
    `;

    document.querySelectorAll('.role-panel').forEach(p => p.style.display = 'none');
    
    if (role === 'admin') {
        document.getElementById('panelAdmin').style.display = 'block';
        initAdminDashboard();
        showToast(`Hoş geldiniz, ${account.name}! Okul Yönetim Paneli aktif.`, 'success');
    } else if (role === 'driver') {
        document.getElementById('panelDriver').style.display = 'block';
        initDriverDashboard();
        showToast(`Hoş geldiniz, ${account.name}! 34 OKL 001 Servis Hattı aktif.`, 'success');
    } else if (role === 'parent') {
        document.getElementById('panelParent').style.display = 'block';
        initParentDashboard();
        showToast(`Hoş geldiniz, ${account.name}! Ahmet Yılmaz'ın canlı takibi aktif.`, 'success');
    }

    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function handleManualLogin(e) {
    e.preventDefault();
    const email = document.getElementById('loginEmail').value.trim().toLowerCase();
    const pass = document.getElementById('loginPassword').value.trim();
    const errBox = document.getElementById('loginErrorMessage');

    if (email === DEMO_ACCOUNTS.admin.email && pass === DEMO_ACCOUNTS.admin.pass) {
        quickLogin('admin');
        errBox.style.display = 'none';
    } else if (email === DEMO_ACCOUNTS.driver.email && pass === DEMO_ACCOUNTS.driver.pass) {
        quickLogin('driver');
        errBox.style.display = 'none';
    } else if (email === DEMO_ACCOUNTS.parent.email && pass === DEMO_ACCOUNTS.parent.pass) {
        quickLogin('parent');
        errBox.style.display = 'none';
    } else {
        errBox.style.display = 'block';
        errBox.innerHTML = '<i class="fas fa-exclamation-circle"></i> Geçersiz e-posta veya şifre! Lütfen yukarıdaki hazır butonları kullanın veya demo şifresi <code>okulbus123</code> girin.';
        showToast('Giriş başarısız! Demo bilgileriyle deneyin.', 'error');
    }
}

function logout() {
    currentRole = null;
    document.getElementById('dashboardView').style.display = 'none';
    document.getElementById('landingView').style.display = 'block';

    document.getElementById('navAuthArea').innerHTML = `
        <a href="#login-section" class="btn-nav-login" onclick="scrollToLogin()">
            <i class="fas fa-sign-in-alt"></i> Giriş Yap / Test Et
        </a>
    `;

    showToast('Oturum kapatıldı. Kolay demo giriş ekranına yönlendirildiniz.', 'info');
    scrollToLogin();
}

function showLandingView() {
    if (currentRole) {
        const res = confirm('Giriş yapmış durumdasınız. Ana sayfaya dönmek istiyor musunuz? Panel oturumunuz korunur.');
        if (!res) return;
    }
    document.getElementById('dashboardView').style.display = 'none';
    document.getElementById('landingView').style.display = 'block';
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function scrollToLogin() {
    document.getElementById('dashboardView').style.display = 'none';
    document.getElementById('landingView').style.display = 'block';
    const loginSec = document.getElementById('login-section');
    if (loginSec) {
        loginSec.scrollIntoView({ behavior: 'smooth' });
    }
}

function toggleRoleMenu() {
    const menu = document.getElementById('roleDropdownMenu');
    if (menu) {
        menu.classList.toggle('show');
    }
}

window.addEventListener('click', (e) => {
    if (!e.target.closest('.role-switch-dropdown')) {
        const menu = document.getElementById('roleDropdownMenu');
        if (menu && menu.classList.contains('show')) {
            menu.classList.remove('show');
        }
    }
    // Modalların dışına tıklanınca kapanması
    if (e.target.classList.contains('modal-overlay')) {
        e.target.style.display = 'none';
    }
});

// ==========================================
// 1. ADMIN DASHBOARD & CRUD MANTIĞI
// ==========================================
function initAdminDashboard() {
    renderAdminFleetTable();
    renderAdminStudentTable();
    updateKpiCounters();
    populateBusSelect();

    setTimeout(() => {
        if (!adminMapInstance) {
            adminMapInstance = L.map('adminMap').setView([41.0082, 29.0400], 12);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap Katkıda Bulunanlar | Okulbus Filo Takip'
            }).addTo(adminMapInstance);

            // Okul Kampüsü İkonu
            const schoolIcon = L.divIcon({
                className: 'custom-map-marker school-marker',
                html: '<div class="marker-pin school-pin"><i class="fas fa-school"></i></div><div class="marker-label">Okul Kampüsü</div>',
                iconSize: [40, 40],
                iconAnchor: [20, 20]
            });
            L.marker([41.0150, 29.0850], { icon: schoolIcon }).addTo(adminMapInstance)
                .bindPopup('<strong>Ana Okul Kampüsü</strong><br>Servislerin Varış Noktası');

            renderAdminMapMarkers();
        } else {
            adminMapInstance.invalidateSize();
            renderAdminMapMarkers();
        }
    }, 300);
}

function renderAdminMapMarkers() {
    if (!adminMapInstance) return;

    // Önceki işaretçileri temizle
    mapMarkers.forEach(m => adminMapInstance.removeLayer(m));
    mapMarkers = [];

    FLEET_DATA.forEach(bus => {
        const busIcon = L.divIcon({
            className: 'custom-map-marker bus-marker',
            html: `<div class="marker-pin bus-pin"><i class="fas fa-bus"></i></div><div class="marker-label">${bus.plate}</div>`,
            iconSize: [35, 35],
            iconAnchor: [17, 17]
        });
        
        const m = L.marker([bus.lat, bus.lng], { icon: busIcon }).addTo(adminMapInstance)
            .bindPopup(`
                <div style="font-family: Poppins, sans-serif; font-size: 13px;">
                    <strong style="color: #2D2D2D; font-size: 14px;">🚌 ${bus.plate}</strong><br>
                    <strong>Sürücü:</strong> ${bus.driver} (${bus.phone})<br>
                    <strong>Güzergah:</strong> ${bus.route}<br>
                    <strong>Hız:</strong> <span style="color: #10B981; font-weight: 600;">${bus.speed}</span><br>
                    <strong>Doluluk:</strong> ${bus.cap}
                </div>
            `);
        mapMarkers.push(m);
    });
}

function updateKpiCounters() {
    const busKpi = document.querySelector('.kpi-bus + .kpi-content .kpi-val');
    if (busKpi) busKpi.innerText = `${FLEET_DATA.length} / ${FLEET_DATA.length}`;

    const studentKpi = document.querySelector('.kpi-students + .kpi-content .kpi-val');
    if (studentKpi) studentKpi.innerText = `${300 + STUDENTS_DATA.length}`;
}

function renderAdminFleetTable() {
    const tbody = document.getElementById('adminBusesTbody');
    if (!tbody) return;
    tbody.innerHTML = FLEET_DATA.map(bus => `
        <tr>
            <td><strong>${bus.plate}</strong></td>
            <td>${bus.driver}</td>
            <td><code>${bus.phone}</code></td>
            <td>${bus.route}</td>
            <td><span class="cap-pill">${bus.cap}</span></td>
            <td><span class="text-green font-bold">${bus.speed}</span></td>
            <td><span class="status-badge ${bus.status === 'Yolda' ? 'status-moving' : 'status-waiting'}"><i class="fas fa-circle"></i> ${bus.status}</span></td>
            <td>
                <div style="display: flex; gap: 6px;">
                    <button class="btn-table-action" onclick="focusBusOnAdminMap(${bus.lat}, ${bus.lng}, '${bus.plate}')" title="Haritada Odakla">
                        <i class="fas fa-search-location"></i> İzle
                    </button>
                    <button class="btn-table-delete" onclick="deleteBus('${bus.plate}')" title="Servisi Sil">
                        <i class="fas fa-trash-alt"></i>
                    </button>
                </div>
            </td>
        </tr>
    `).join('');
}

function focusBusOnAdminMap(lat, lng, plate) {
    if (adminMapInstance) {
        adminMapInstance.setView([lat, lng], 15, { animate: true });
        showToast(`${plate} plakalı servis haritada odaklandı.`, 'info');
        document.getElementById('adminMap').scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
}

function renderAdminStudentTable(filter = '') {
    const tbody = document.getElementById('adminStudentsTbody');
    if (!tbody) return;

    const filtered = STUDENTS_DATA.filter(s => 
        s.name.toLowerCase().includes(filter.toLowerCase()) || 
        s.bus.toLowerCase().includes(filter.toLowerCase()) ||
        s.stop.toLowerCase().includes(filter.toLowerCase())
    );

    tbody.innerHTML = filtered.map(s => `
        <tr>
            <td><strong>${s.name}</strong></td>
            <td><span class="badge-grade">${s.grade}</span></td>
            <td><code>${s.bus}</code></td>
            <td>${s.stop}</td>
            <td><code>${s.parentPhone}</code></td>
            <td>
                <span class="badge-student-status ${s.status.startsWith('Bindi') ? 'status-boarded' : 'status-pending'}">
                    <i class="fas ${s.status.startsWith('Bindi') ? 'fa-check' : 'fa-clock'}"></i> ${s.status}
                </span>
            </td>
            <td>
                <button class="btn-table-delete" onclick="deleteStudent(${s.id})" title="Öğrenciyi Sil">
                    <i class="fas fa-trash-alt"></i>
                </button>
            </td>
        </tr>
    `).join('');
}

function filterStudentList() {
    const query = document.getElementById('studentSearchInput').value;
    renderAdminStudentTable(query);
}

// ==========================================
// MODALLAR & CRUD EYLEMLERİ
// ==========================================
function openAddBusModal() {
    document.getElementById('modalAddBus').style.display = 'flex';
}

function openAddStudentModal() {
    populateBusSelect();
    document.getElementById('modalAddStudent').style.display = 'flex';
}

function closeModal(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.style.display = 'none';
}

function populateBusSelect() {
    const sel = document.getElementById('studentBusSelect');
    if (!sel) return;
    sel.innerHTML = FLEET_DATA.map(b => `<option value="${b.plate}">${b.plate} - ${b.route} (${b.driver})</option>`).join('');
}

function submitAddBus(e) {
    e.preventDefault();
    const plate = document.getElementById('busPlateInput').value.trim().toUpperCase();
    const driver = document.getElementById('busDriverInput').value.trim();
    const phone = document.getElementById('busPhoneInput').value.trim();
    const route = document.getElementById('busRouteInput').value.trim();
    const cap = document.getElementById('busCapInput').value.trim();
    const speed = document.getElementById('busSpeedInput').value.trim() || '35 km/s';

    if (FLEET_DATA.some(b => b.plate === plate)) {
        showToast('Bu plakaya sahip bir servis zaten mevcut!', 'error');
        return;
    }

    // İstanbul çevresinde hafif rastgele koordinat
    const randomOffsetLat = (Math.random() - 0.5) * 0.08;
    const randomOffsetLng = (Math.random() - 0.5) * 0.12;

    const newBus = {
        plate,
        driver,
        phone,
        route,
        cap: `0/${cap}`,
        speed,
        status: 'Yolda',
        lat: 41.0082 + randomOffsetLat,
        lng: 29.0400 + randomOffsetLng
    };

    FLEET_DATA.push(newBus);
    saveFleet();
    renderAdminFleetTable();
    updateKpiCounters();
    populateBusSelect();
    renderAdminMapMarkers();

    closeModal('modalAddBus');
    e.target.reset();
    showToast(`✅ ${plate} plakalı servis ve şoför ${driver} sisteme başarıyla eklendi!`, 'success');
}

function submitAddStudent(e) {
    e.preventDefault();
    const name = document.getElementById('studentNameInput').value.trim();
    const grade = document.getElementById('studentGradeInput').value.trim();
    const bus = document.getElementById('studentBusSelect').value;
    const stop = document.getElementById('studentStopInput').value.trim();
    const parentName = document.getElementById('parentNameInput').value.trim();
    const parentPhone = document.getElementById('parentPhoneInput').value.trim();

    const newStudent = {
        id: Date.now(),
        name,
        grade,
        bus,
        stop,
        parentName,
        parentPhone,
        status: 'Bekleniyor'
    };

    STUDENTS_DATA.push(newStudent);
    saveStudents();
    renderAdminStudentTable();
    updateKpiCounters();
    if (currentRole === 'driver') renderDriverAttendanceList();

    closeModal('modalAddStudent');
    e.target.reset();
    showToast(`✅ ${name} (${grade}) isimli öğrenci ve velisi ${bus} hattına eklendi!`, 'success');
}

function deleteBus(plate) {
    if (!confirm(`${plate} plakalı servisi ve şoför kaydını silmek istediğinize emin misiniz?`)) return;
    FLEET_DATA = FLEET_DATA.filter(b => b.plate !== plate);
    saveFleet();
    renderAdminFleetTable();
    updateKpiCounters();
    populateBusSelect();
    renderAdminMapMarkers();
    showToast(`${plate} plakalı servis sistemden silindi.`, 'info');
}

function deleteStudent(id) {
    const st = STUDENTS_DATA.find(s => s.id === id);
    if (!st) return;
    if (!confirm(`${st.name} isimli öğrenciyi listeden silmek istediğinize emin misiniz?`)) return;
    STUDENTS_DATA = STUDENTS_DATA.filter(s => s.id !== id);
    saveStudents();
    renderAdminStudentTable();
    updateKpiCounters();
    if (currentRole === 'driver') renderDriverAttendanceList();
    showToast(`${st.name} kaydı silindi.`, 'info');
}

// ==========================================
// 2. DRIVER DASHBOARD BAŞLATMA
// ==========================================
function initDriverDashboard() {
    renderDriverAttendanceList();
    renderDriverTimeline();

    setTimeout(() => {
        if (!driverMapInstance) {
            driverMapInstance = L.map('driverMap').setView([40.9930, 29.1120], 14);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap | Sürücü Navigasyon'
            }).addTo(driverMapInstance);

            const busIcon = L.divIcon({
                className: 'custom-map-marker bus-marker-driver',
                html: '<div class="marker-pin bus-pin-pulse"><i class="fas fa-bus"></i></div><div class="marker-label">Aracınız (34 OKL 001)</div>',
                iconSize: [40, 40],
                iconAnchor: [20, 20]
            });
            L.marker([40.9930, 29.1120], { icon: busIcon }).addTo(driverMapInstance)
                .bindPopup('<strong>Aracınız Seyir Halinde</strong><br>Sıradaki: Ataşehir Migros Kavşağı');

            // Duraklar
            L.marker([40.9980, 29.1180]).addTo(driverMapInstance).bindPopup('Durak: Ataşehir Migros');
            L.marker([41.0020, 29.1240]).addTo(driverMapInstance).bindPopup('Durak: Batı Ataşehir Konutları');
        } else {
            driverMapInstance.invalidateSize();
        }
    }, 300);
}

function renderDriverAttendanceList() {
    const list = document.getElementById('driverAttendanceList');
    if (!list) return;

    const busStudents = STUDENTS_DATA.filter(s => s.bus === '34 OKL 001');

    list.innerHTML = busStudents.map(s => {
        const isBoarded = s.status.startsWith('Bindi');
        return `
            <div class="attendance-item ${isBoarded ? 'boarded' : ''}">
                <div class="att-info">
                    <div class="att-avatar"><i class="fas fa-user-graduate"></i></div>
                    <div>
                        <h4>${s.name} <span class="badge-grade">${s.grade}</span></h4>
                        <span class="text-sm text-muted"><i class="fas fa-map-marker-alt"></i> ${s.stop}</span>
                    </div>
                </div>
                <div class="att-actions">
                    <button class="btn-att ${isBoarded ? 'btn-att-done' : 'btn-att-board'}" onclick="toggleStudentAttendance(${s.id})">
                        <i class="fas ${isBoarded ? 'fa-check-circle' : 'fa-sign-in-alt'}"></i> ${isBoarded ? 'Bindi (İptal Et)' : 'Bindi Olarak İşaretle'}
                    </button>
                    <button class="btn-att btn-att-absent" onclick="markStudentAbsent(${s.id})">
                        <i class="fas fa-times-circle"></i> Gelmedi
                    </button>
                </div>
            </div>
        `;
    }).join('');

    const boardedCount = busStudents.filter(s => s.status.startsWith('Bindi')).length;
    const countEl = document.getElementById('driverAttendanceCount');
    if (countEl) {
        countEl.innerText = `${boardedCount} / ${busStudents.length} Öğrenci Bindi`;
    }
}

function toggleStudentAttendance(id) {
    const student = STUDENTS_DATA.find(s => s.id === id);
    if (!student) return;

    if (student.status.startsWith('Bindi')) {
        student.status = 'Bekleniyor';
        showToast(`${student.name} biniş durumu geri alındı.`, 'info');
    } else {
        const now = new Date();
        const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
        student.status = `Bindi (${timeStr})`;
        showToast(`✔️ ${student.name} servise bindi! Veliye anlık bildirim iletildi.`, 'success');
    }
    saveStudents();
    renderDriverAttendanceList();
    if (adminMapInstance) renderAdminStudentTable();
}

function markStudentAbsent(id) {
    const student = STUDENTS_DATA.find(s => s.id === id);
    if (!student) return;
    student.status = 'Gelmedi (Bildirildi)';
    saveStudents();
    showToast(`⚠️ ${student.name} gelmedi olarak işaretlendi.`, 'info');
    renderDriverAttendanceList();
    if (adminMapInstance) renderAdminStudentTable();
}

function renderDriverTimeline() {
    const container = document.getElementById('driverStopsTimeline');
    if (!container) return;

    container.innerHTML = DRIVER_STOPS.map(stop => `
        <div class="timeline-row ${stop.active ? 'timeline-current' : (stop.done ? 'timeline-passed' : '')}">
            <div class="timeline-dot"><i class="fas ${stop.done ? 'fa-check' : (stop.active ? 'fa-bus' : 'fa-circle')}"></i></div>
            <div class="timeline-info">
                <strong>${stop.title}</strong>
                <span class="timeline-time">${stop.time}</span>
            </div>
        </div>
    `).join('');
}

function triggerSosAlarm() {
    const confirmed = confirm('DİKKAT! Okul yönetimine ve acil müdahale merkezine ACİL DURUM (SOS) sinyali göndermek istediğinize emin misiniz?');
    if (confirmed) {
        showToast('🚨 ACİL DURUM SİNYALİ GÖNDERİLDİ! Konumunuz okul merkezine iletildi.', 'sos');
        alert('🚨 ACİL DURUM BİLDİRİMİ İLETİLDİ!\n\nServis: 34 OKL 001\nKonum: Ataşehir Migros Önü\nOkul idaresi ve nöbetçi amirliği bilgilendirildi.');
    }
}

// ==========================================
// 3. PARENT DASHBOARD BAŞLATMA
// ==========================================
function initParentDashboard() {
    setTimeout(() => {
        if (!parentMapInstance) {
            parentMapInstance = L.map('parentMap').setView([40.9940, 29.1140], 15);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap | Veli Servis Takibi'
            }).addTo(parentMapInstance);

            // Çocuğun Evi
            const homeIcon = L.divIcon({
                className: 'custom-map-marker home-marker',
                html: '<div class="marker-pin home-pin"><i class="fas fa-home"></i></div><div class="marker-label">Eviniz (Ataşehir)</div>',
                iconSize: [40, 40],
                iconAnchor: [20, 20]
            });
            L.marker([40.9960, 29.1170], { icon: homeIcon }).addTo(parentMapInstance)
                .bindPopup('<strong>Eviniz</strong><br>Servis durağınız burada bulunmaktadır.');

            // Servis Aracı
            const busIcon = L.divIcon({
                className: 'custom-map-marker bus-marker-live',
                html: '<div class="marker-pin bus-pin-pulse"><i class="fas fa-bus"></i></div><div class="marker-label">Ahmet\'in Servisi (34 OKL 001)</div>',
                iconSize: [42, 42],
                iconAnchor: [21, 21]
            });
            L.marker([40.9910, 29.1090], { icon: busIcon }).addTo(parentMapInstance)
                .bindPopup('<strong>34 OKL 001</strong><br>Sürücü: Mehmet Kaptan<br>Hız: 36 km/s<br><strong>Yaklaşık 5 dakika içinde kapınızda!</strong>')
                .openPopup();

            // Rota Çizgisi
            const latlngs = [
                [40.9910, 29.1090],
                [40.9935, 29.1130],
                [40.9960, 29.1170]
            ];
            L.polyline(latlngs, { color: '#FEBE1E', weight: 6, dashArray: '8, 8' }).addTo(parentMapInstance);
        } else {
            parentMapInstance.invalidateSize();
        }
    }, 300);
}

// SAYFA YÜKLENDİĞİNDE
document.addEventListener('DOMContentLoaded', () => {
    const urlParams = new URLSearchParams(window.location.search);
    const roleParam = urlParams.get('role');
    if (roleParam && DEMO_ACCOUNTS[roleParam]) {
        quickLogin(roleParam);
    }
});
