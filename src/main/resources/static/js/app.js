'use strict';

// =============================================
// Hospital IS — Frontend SPA
// =============================================

const API = '';  // same-origin; backend on :8080

let currentRole = '';

const SECTION_ACCESS = {
  departments: ['ROLE_ADMIN', 'ROLE_DOCTOR'],
  admin:       ['ROLE_ADMIN'],
  chats:       ['ROLE_ADMIN', 'ROLE_DOCTOR'],
};

const PERMISSIONS = {
  'patient:add':           ['ROLE_ADMIN', 'ROLE_DOCTOR'],
  'patient:delete':        ['ROLE_ADMIN'],
  'patient:assign-doctor': ['ROLE_ADMIN', 'ROLE_DOCTOR'],
  'patient:history':       ['ROLE_DOCTOR'],
  'doctor:manage':         ['ROLE_ADMIN'],
  'department:manage':     ['ROLE_ADMIN'],
  'ward:manage':           ['ROLE_ADMIN'],
  'service:manage':        ['ROLE_ADMIN'],
};

function canDo(action) {
  const allowed = PERMISSIONS[action];
  return !allowed || allowed.includes(currentRole);
}

function applyRoleVisibility() {
  document.querySelectorAll('[data-show-roles]').forEach(el => {
    const allowed = el.dataset.showRoles.split(',');
    if (!allowed.includes(currentRole)) el.style.display = 'none';
  });
}

// ---- Utils ----

const $ = id => document.getElementById(id);

function toast(msg, type = 'success') {
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  const icon = type === 'success' ? '✓' : type === 'error' ? '✗' : '⚠';
  el.innerHTML = `<span>${icon}</span><span>${msg}</span>`;
  $('toastContainer').appendChild(el);
  setTimeout(() => el.remove(), 3500);
}

async function api(path, options = {}) {
  const token = localStorage.getItem('token');
  const headers = { 'Content-Type': 'application/json', ...options.headers };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  try {
    const res = await fetch(API + path, { headers, ...options });
    if (res.status === 401) {
      localStorage.clear();
      window.location.href = '/login.html';
      throw new Error('Unauthorized');
    }
    if (res.status === 204) return null;
    const data = await res.json();
    if (!res.ok) {
      const msg = data.message || data.error || 'Server error';
      toast(msg, 'error');
      throw new Error(msg);
    }
    return data;
  } catch (e) {
    if (!(e instanceof Error && e.message)) toast(e.toString(), 'error');
    throw e;
  }
}

function logout() {
  stopChatPoll();
  localStorage.clear();
  window.location.href = '/login.html';
}

function roleLabel(role) {
  const map = { ROLE_ADMIN: 'Администратор', ROLE_DOCTOR: 'Врач', ROLE_NURSE: 'Медсестра' };
  return map[role] || role;
}

function escapeHtml(str) {
  if (str == null) return '—';
  return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

function statusBadge(status) {
  const map = {
    TREATMENT: ['badge-treatment', 'На лечении'],
    DISCHARGED: ['badge-discharged', 'Выписан'],
    TRANSFERRED: ['badge-transferred', 'Переведён'],
  };
  const [cls, label] = map[status] || ['', status];
  return `<span class="badge ${cls}">${label}</span>`;
}

function activeBadge(active) {
  return active
    ? `<span class="badge badge-active">Активен</span>`
    : `<span class="badge badge-inactive">Неактивен</span>`;
}

function paidBadge(paid) {
  return paid
    ? `<span class="badge badge-paid">Оплачено</span>`
    : `<span class="badge badge-unpaid">Не оплачено</span>`;
}

function occupancyBar(occupied, capacity) {
  const pct = capacity ? Math.round((occupied / capacity) * 100) : 0;
  const cls = pct < 60 ? 'occ-low' : pct < 90 ? 'occ-mid' : 'occ-high';
  return `
    <div style="display:flex;align-items:center;gap:8px">
      <div class="occ-bar"><div class="occ-bar-fill ${cls}" style="width:${pct}%"></div></div>
      <small>${occupied}/${capacity}</small>
    </div>`;
}

function buildPagination(containerId, pageData, onPageChange) {
  const c = $(containerId);
  if (!c) return;
  if (pageData.totalPages <= 1) { c.innerHTML = ''; return; }
  let html = `<button ${pageData.page === 0 ? 'disabled' : ''} onclick="${onPageChange}(${pageData.page - 1})">‹</button>`;
  for (let i = 0; i < pageData.totalPages; i++) {
    html += `<button class="${i === pageData.page ? 'active' : ''}" onclick="${onPageChange}(${i})">${i + 1}</button>`;
  }
  html += `<button ${pageData.last ? 'disabled' : ''} onclick="${onPageChange}(${pageData.page + 1})">›</button>`;
  c.innerHTML = html;
}

// ---- Navigation ----

function navigate(section) {
  const sectionAllowed = SECTION_ACCESS[section];
  if (sectionAllowed && !sectionAllowed.includes(currentRole)) {
    toast('Недостаточно прав для просмотра этого раздела', 'warning');
    return;
  }
  document.querySelectorAll('.section-page').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.sidebar-nav a').forEach(a => a.classList.remove('active'));
  const page = $(`page-${section}`);
  if (page) page.classList.add('active');
  const link = document.querySelector(`[data-nav="${section}"]`);
  if (link) link.classList.add('active');
  if (section !== 'chats') stopChatPoll();

  $('topbarTitle').textContent = {
    dashboard:   'Дашборд',
    patients:    'Пациенты',
    doctors:     'Врачи',
    departments: 'Отделения',
    wards:       'Палаты',
    services:    'Платные услуги',
    admin:       'Администрация',
    chats:       'Чаты',
  }[section] || section;

  const loaders = {
    dashboard:   loadDashboard,
    patients:    () => loadPatients(0),
    doctors:     () => loadDoctors(0),
    departments: loadDepartments,
    wards:       loadWards,
    services:    () => loadServices(0),
    admin:       loadAdmin,
    chats:       loadChats,
  };
  if (loaders[section]) loaders[section]();
}

// ---- Modal helpers ----

function openModal(id) { $(id).classList.add('open'); }
function closeModal(id) { $(id).classList.remove('open'); }

document.addEventListener('click', e => {
  if (e.target.classList.contains('modal-overlay')) {
    e.target.classList.remove('open');
  }
});

// =============================================
// DASHBOARD
// =============================================

async function loadDashboard() {
  try {
    const [patients, doctors, depts, wards, services] = await Promise.all([
      api('/api/patients?size=1'),
      api('/api/doctors?size=1'),
      api('/api/departments'),
      api('/api/wards'),
      api('/api/paid-services?size=1'),
    ]);

    const totalCap = wards.reduce((s, w) => s + w.capacity, 0);
    const totalOcc = wards.reduce((s, w) => s + w.currentOccupancy, 0);

    $('stat-patients').textContent = patients.totalElements ?? '—';
    $('stat-doctors').textContent = doctors.totalElements ?? '—';
    $('stat-depts').textContent = depts.length ?? '—';
    $('stat-wards').textContent = wards.length ?? '—';
    $('stat-free').textContent = (totalCap - totalOcc) + ' / ' + totalCap;
    $('stat-services').textContent = services.totalElements ?? '—';

    // Recent patients
    const recent = await api('/api/patients?size=5&page=0');
    $('recent-patients').innerHTML = recent.content.length === 0
      ? '<tr><td colspan="4" class="empty-state">Нет пациентов</td></tr>'
      : recent.content.map(p => `
          <tr>
            <td>${escapeHtml(p.fullName)}</td>
            <td>${statusBadge(p.status)}</td>
            <td>${escapeHtml(p.currentDoctorName)}</td>
            <td>${escapeHtml(p.currentWardNumber)}</td>
          </tr>`).join('');
  } catch (e) { /* errors shown via toast */ }
}

// =============================================
// PATIENTS
// =============================================

let patientsPage = 0;

async function loadPatients(page = 0) {
  patientsPage = page;
  const data = await api(`/api/patients?page=${page}&size=15`);
  if (!data) return;

  $('patients-table').innerHTML = data.content.length === 0
    ? `<tr><td colspan="7"><div class="empty-state"><i class="fa fa-users"></i>Пациентов нет</div></td></tr>`
    : data.content.map(p => `
        <tr>
          <td><strong>#${p.id}</strong></td>
          <td>${escapeHtml(p.fullName)}</td>
          <td>${escapeHtml(p.snils)}</td>
          <td>${statusBadge(p.status)}</td>
          <td>${escapeHtml(p.currentDoctorName)}</td>
          <td>${escapeHtml(p.currentWardNumber)}</td>
          <td>
            ${canDo('patient:assign-doctor') ? `<button class="btn btn-outline btn-sm btn-icon" title="Назначить врача" onclick="openAssignDoctorModal(${p.id})">👨‍⚕️</button>` : ''}
            <button class="btn btn-outline btn-sm btn-icon" title="Услуги" onclick="openPatientServicesModal(${p.id}, '${escapeHtml(p.fullName)}')">🧾</button>
            ${canDo('patient:history') ? `<button class="btn btn-outline btn-sm btn-icon" title="История пациента" onclick="openPatientHistoryModal(${p.id})">📋</button>` : ''}
            ${canDo('patient:delete') ? `<button class="btn btn-danger btn-sm btn-icon" title="Удалить" onclick="deletePatient(${p.id})">🗑</button>` : ''}
          </td>
        </tr>`).join('');

  buildPagination('patients-pagination', data, 'loadPatients');
}

window.loadPatients = loadPatients;

let patientSearchTimer = null;
function debouncedPatientSearch() {
  clearTimeout(patientSearchTimer);
  patientSearchTimer = setTimeout(() => searchPatients(0), 300);
}

async function searchPatients(page = 0) {
  patientsPage = page;
  const q      = $('patient-search')?.value?.trim() || '';
  const status = $('patient-status-filter')?.value  || '';

  const isFiltered = q || status;
  if (!isFiltered) { loadPatients(0); return; }

  const params = new URLSearchParams({ page, size: 15 });
  if (q)      params.append('q', q);
  if (status) params.append('status', status);

  const data = await api(`/api/patients/search?${params}`);
  if (!data) return;

  $('patients-table').innerHTML = data.content.length === 0
    ? `<tr><td colspan="7"><div class="empty-state"><i class="fa fa-users"></i>Пациентов не найдено</div></td></tr>`
    : data.content.map(p => `
        <tr>
          <td><strong>#${p.id}</strong></td>
          <td>${escapeHtml(p.fullName)}</td>
          <td>${escapeHtml(p.snils)}</td>
          <td>${statusBadge(p.status)}</td>
          <td>${escapeHtml(p.currentDoctorName)}</td>
          <td>${escapeHtml(p.currentWardNumber)}</td>
          <td>
            ${canDo('patient:assign-doctor') ? `<button class="btn btn-outline btn-sm btn-icon" title="Назначить врача" onclick="openAssignDoctorModal(${p.id})">👨‍⚕️</button>` : ''}
            <button class="btn btn-outline btn-sm btn-icon" title="Услуги" onclick="openPatientServicesModal(${p.id}, '${escapeHtml(p.fullName)}')">🧾</button>
            ${canDo('patient:history') ? `<button class="btn btn-outline btn-sm btn-icon" title="История пациента" onclick="openPatientHistoryModal(${p.id})">📋</button>` : ''}
            ${canDo('patient:delete') ? `<button class="btn btn-danger btn-sm btn-icon" title="Удалить" onclick="deletePatient(${p.id})">🗑</button>` : ''}
          </td>
        </tr>`).join('');

  buildPagination('patients-pagination', data, 'searchPatients');
}

window.searchPatients = searchPatients;

async function savePatient() {
  const body = {
    fullName: $('p-fullName').value.trim(),
    birthDate: $('p-birthDate').value,
    gender: $('p-gender').value,
    snils: $('p-snils').value.trim(),
    phone: $('p-phone').value.trim(),
    address: $('p-address').value.trim(),
  };
  try {
    await api('/api/patients', { method: 'POST', body: JSON.stringify(body) });
    toast('Пациент создан');
    closeModal('modal-patient');
    loadPatients(0);
  } catch (_) {}
}

async function deletePatient(id) {
  if (!confirm('Удалить пациента?')) return;
  try {
    await api(`/api/patients/${id}`, { method: 'DELETE' });
    toast('Пациент удалён');
    loadPatients(patientsPage);
  } catch (_) {}
}

async function openAssignDoctorModal(patientId) {
  $('assign-patientId').value = patientId;
  const doctors = await api('/api/doctors?size=100');
  if (!doctors) return;
  $('assign-doctorId').innerHTML = doctors.content.map(d =>
    `<option value="${d.id}">${escapeHtml(d.fullName)} (${d.specialty})</option>`
  ).join('');
  openModal('modal-assign-doctor');
}

async function doAssignDoctor() {
  const patientId = $('assign-patientId').value;
  const doctorId = $('assign-doctorId').value;
  try {
    await api(`/api/patients/${patientId}/assign-doctor/${doctorId}`, { method: 'PUT' });
    toast('Врач назначен');
    closeModal('modal-assign-doctor');
    loadPatients(patientsPage);
  } catch (_) {}
}

async function openPatientServicesModal(patientId, name) {
  $('services-patient-name').textContent = name;
  $('services-assign-patientId').value = patientId;
  const [svcData, assigned] = await Promise.all([
    api('/api/paid-services?size=100'),
    api(`/api/patients/${patientId}/services`),
  ]);
  if (!svcData) return;

  $('services-select').innerHTML = svcData.content.map(s =>
    `<option value="${s.id}">${escapeHtml(s.name)} — ${s.price} ₽</option>`
  ).join('');

  $('patient-services-list').innerHTML = assigned.length === 0
    ? '<div class="empty-state" style="padding:20px"><i>🧾</i>Услуг нет</div>'
    : assigned.map(ps => `
        <div style="display:flex;justify-content:space-between;align-items:center;padding:8px 0;border-bottom:1px solid #f1f5f9">
          <div>
            <strong>${escapeHtml(ps.serviceName)}</strong>
            <small style="color:#64748b;margin-left:8px">${ps.price} ₽</small>
          </div>
          <div style="display:flex;align-items:center;gap:8px">
            ${paidBadge(ps.paid)}
            ${!ps.paid ? `<button class="btn btn-success btn-sm" onclick="markPaid(${patientId}, ${ps.id})">Оплатить</button>` : ''}
          </div>
        </div>`).join('');

  openModal('modal-patient-services');
}

async function doAssignService() {
  const patientId = $('services-assign-patientId').value;
  const serviceId = $('services-select').value;
  try {
    await api(`/api/patients/${patientId}/paid-services/${serviceId}`, { method: 'POST' });
    toast('Услуга назначена');
    openPatientServicesModal(patientId, $('services-patient-name').textContent);
  } catch (_) {}
}

async function markPaid(patientId, linkId) {
  try {
    await api(`/api/patients/${patientId}/paid-services/${linkId}/pay`, { method: 'PATCH' });
    toast('Отмечено как оплачено');
    openPatientServicesModal(patientId, $('services-patient-name').textContent);
  } catch (_) {}
}

window.markPaid = markPaid;

// =============================================
// DOCTORS
// =============================================

let doctorsPage = 0;

async function loadDoctors(page = 0) {
  doctorsPage = page;
  const specialty = $('filter-specialty')?.value || '';
  const url = specialty
    ? `/api/doctors?specialty=${specialty}&page=${page}&size=15`
    : `/api/doctors?page=${page}&size=15`;
  const data = await api(url);
  if (!data) return;

  $('doctors-table').innerHTML = data.content.length === 0
    ? `<tr><td colspan="6"><div class="empty-state"><i class="fa fa-user-md"></i>Врачей нет</div></td></tr>`
    : data.content.map(d => `
        <tr>
          <td><strong>#${d.id}</strong></td>
          <td>${escapeHtml(d.fullName)}</td>
          <td>${escapeHtml(specialtyLabel(d.specialty))}</td>
          <td>${escapeHtml(d.cabinetNumber)}</td>
          <td>${escapeHtml(d.departmentName)}</td>
          <td>
            ${activeBadge(d.active)}
            ${canDo('doctor:manage') ? `<button class="btn btn-danger btn-sm btn-icon" style="margin-left:6px" onclick="deleteDoctor(${d.id})">🗑</button>` : ''}
          </td>
        </tr>`).join('');

  buildPagination('doctors-pagination', data, 'loadDoctors');
}

window.loadDoctors = loadDoctors;

function specialtyLabel(s) {
  const map = {
    CARDIOLOGIST: 'Кардиолог', SURGEON: 'Хирург', THERAPIST: 'Терапевт',
    NEUROLOGIST: 'Невролог', PEDIATRICIAN: 'Педиатр', ORTHOPEDIST: 'Ортопед',
    ONCOLOGIST: 'Онколог', UROLOGIST: 'Уролог',
  };
  return map[s] || s;
}

async function saveDoctor() {
  const deptId = $('d-departmentId').value;
  const body = {
    fullName: $('d-fullName').value.trim(),
    specialty: $('d-specialty').value,
    cabinetNumber: $('d-cabinet').value.trim(),
    phone: $('d-phone').value.trim(),
    departmentId: deptId ? Number(deptId) : null,
  };
  try {
    await api('/api/doctors', { method: 'POST', body: JSON.stringify(body) });
    toast('Врач создан');
    closeModal('modal-doctor');
    loadDoctors(0);
  } catch (_) {}
}

async function deleteDoctor(id) {
  if (!confirm('Удалить врача?')) return;
  try {
    await api(`/api/doctors/${id}`, { method: 'DELETE' });
    toast('Врач удалён');
    loadDoctors(doctorsPage);
  } catch (_) {}
}

async function populateDoctorDeptSelect() {
  const depts = await api('/api/departments');
  if (!depts) return;
  $('d-departmentId').innerHTML = `<option value="">— Без отделения —</option>`
    + depts.map(dep => `<option value="${dep.id}">${escapeHtml(dep.name)}</option>`).join('');
}

// =============================================
// DEPARTMENTS
// =============================================

async function loadDepartments() {
  const data = await api('/api/departments');
  if (!data) return;

  $('departments-table').innerHTML = data.length === 0
    ? `<tr><td colspan="4"><div class="empty-state"><i>🏥</i>Отделений нет</div></td></tr>`
    : data.map(d => `
        <tr>
          <td><strong>#${d.id}</strong></td>
          <td>${escapeHtml(d.name)}</td>
          <td>${escapeHtml(d.location)}</td>
          <td>
            ${escapeHtml(d.headDoctorName)}
            ${canDo('department:manage') ? `<button class="btn btn-danger btn-sm btn-icon" style="margin-left:8px" onclick="deleteDepartment(${d.id})">🗑</button>` : ''}
          </td>
        </tr>`).join('');
}

async function saveDepartment() {
  const headId = $('dep-headDoctorId').value;
  const body = {
    name: $('dep-name').value.trim(),
    description: $('dep-description').value.trim(),
    location: $('dep-location').value.trim(),
    headDoctorId: headId ? Number(headId) : null,
  };
  try {
    await api('/api/departments', { method: 'POST', body: JSON.stringify(body) });
    toast('Отделение создано');
    closeModal('modal-department');
    loadDepartments();
  } catch (_) {}
}

async function deleteDepartment(id) {
  if (!confirm('Удалить отделение?')) return;
  try {
    await api(`/api/departments/${id}`, { method: 'DELETE' });
    toast('Отделение удалено');
    loadDepartments();
  } catch (_) {}
}

async function populateDeptHeadSelect() {
  const docs = await api('/api/doctors?size=100');
  if (!docs) return;
  $('dep-headDoctorId').innerHTML = `<option value="">— Без заведующего —</option>`
    + docs.content.map(d => `<option value="${d.id}">${escapeHtml(d.fullName)}</option>`).join('');
}

// =============================================
// WARDS
// =============================================

async function loadWards() {
  const data = await api('/api/wards');
  if (!data) return;

  $('wards-table').innerHTML = data.length === 0
    ? `<tr><td colspan="5"><div class="empty-state"><i>🛏</i>Палат нет</div></td></tr>`
    : data.map(w => `
        <tr>
          <td><strong>#${w.id}</strong></td>
          <td>${escapeHtml(w.wardNumber)}</td>
          <td>${escapeHtml(w.departmentName)}</td>
          <td>${occupancyBar(w.currentOccupancy, w.capacity)}</td>
          <td>
            <button class="btn btn-primary btn-sm" onclick="openAdmitModal(${w.id}, '${escapeHtml(w.wardNumber)}', ${w.freeSlots})">
              + Заселить
            </button>
          </td>
        </tr>`).join('');
}

async function saveWard() {
  const body = {
    wardNumber: $('w-number').value.trim(),
    capacity: Number($('w-capacity').value),
    departmentId: Number($('w-deptId').value),
  };
  try {
    await api('/api/wards', { method: 'POST', body: JSON.stringify(body) });
    toast('Палата создана');
    closeModal('modal-ward');
    loadWards();
  } catch (_) {}
}

async function populateWardDeptSelect() {
  const depts = await api('/api/departments');
  if (!depts) return;
  $('w-deptId').innerHTML = depts.map(d => `<option value="${d.id}">${escapeHtml(d.name)}</option>`).join('');
}

async function openAdmitModal(wardId, wardNumber, freeSlots) {
  if (freeSlots === 0) { toast('В палате нет свободных мест', 'warning'); return; }
  $('admit-wardId').value = wardId;
  $('admit-wardLabel').textContent = `Палата ${wardNumber} (свободно: ${freeSlots})`;
  const patients = await api('/api/patients?size=100');
  if (!patients) return;
  const unassigned = patients.content.filter(p => p.status === 'TREATMENT' && !p.currentWardNumber);
  $('admit-patientId').innerHTML = unassigned.length === 0
    ? `<option value="">Нет доступных пациентов</option>`
    : unassigned.map(p => `<option value="${p.id}">${escapeHtml(p.fullName)}</option>`).join('');
  openModal('modal-admit');
}

async function doAdmit() {
  const wardId = $('admit-wardId').value;
  const patientId = $('admit-patientId').value;
  if (!patientId) { toast('Выберите пациента', 'warning'); return; }
  try {
    await api(`/api/wards/${wardId}/admit/${patientId}`, { method: 'POST' });
    toast('Пациент заселён');
    closeModal('modal-admit');
    loadWards();
  } catch (_) {}
}

// =============================================
// PAID SERVICES
// =============================================

let servicesPage = 0;

async function loadServices(page = 0) {
  servicesPage = page;
  const data = await api(`/api/paid-services?page=${page}&size=15`);
  if (!data) return;

  $('services-table').innerHTML = data.content.length === 0
    ? `<tr><td colspan="4"><div class="empty-state"><i>🧾</i>Услуг нет</div></td></tr>`
    : data.content.map(s => `
        <tr>
          <td><strong>#${s.id}</strong></td>
          <td>${escapeHtml(s.name)}</td>
          <td><strong>${s.price} ₽</strong></td>
          <td>${activeBadge(s.active)}</td>
        </tr>`).join('');

  buildPagination('services-pagination', data, 'loadServices');
}

window.loadServices = loadServices;

async function saveService() {
  const body = {
    name: $('svc-name').value.trim(),
    price: parseFloat($('svc-price').value),
    description: $('svc-description').value.trim(),
  };
  try {
    await api('/api/paid-services', { method: 'POST', body: JSON.stringify(body) });
    toast('Услуга создана');
    closeModal('modal-service');
    loadServices(0);
  } catch (_) {}
}

// =============================================
// ADMIN
// =============================================

async function loadAdmin() {
  await Promise.all([loadWardReport(), loadServicesSummary()]);
}

async function loadWardReport() {
  const data = await api('/api/admin/reports/ward-occupancy');
  if (!data) return;

  $('ward-report').innerHTML = data.map(dept => `
    <div style="margin-bottom:16px">
      <div style="font-weight:600;color:#1e293b;margin-bottom:8px">
        🏥 ${escapeHtml(dept.departmentName)}
        <small style="font-weight:normal;color:#64748b;margin-left:8px">
          Итого: ${dept.totalOccupied}/${dept.totalCapacity} мест занято
        </small>
      </div>
      <table style="width:100%">
        <thead><tr><th>Палата</th><th>Занято</th><th>Свободно</th><th>Загрузка</th></tr></thead>
        <tbody>
          ${dept.wards.map(w => `
            <tr>
              <td>${escapeHtml(w.wardNumber)}</td>
              <td>${w.occupied}</td>
              <td>${w.free}</td>
              <td>${occupancyBar(w.occupied, w.capacity)}</td>
            </tr>`).join('')}
        </tbody>
      </table>
    </div>`).join('');
}

async function loadServicesSummary() {
  const data = await api('/api/admin/reports/paid-services-summary');
  if (!data) return;

  $('services-summary').innerHTML = `
    <p style="font-weight:700;font-size:1.05rem;margin-bottom:12px">
      Итого по всем: <span style="color:#1a6fc4">${data.grandTotal} ₽</span>
    </p>
    <table>
      <thead><tr><th>#</th><th>Пациент</th><th>Услуг</th><th>Сумма</th></tr></thead>
      <tbody>
        ${data.byPatient.length === 0
          ? `<tr><td colspan="4"><div class="empty-state">Нет данных</div></td></tr>`
          : data.byPatient.map(p => `
              <tr>
                <td>${p.patientId}</td>
                <td>${escapeHtml(p.patientName)}</td>
                <td>${p.serviceCount}</td>
                <td><strong>${p.total} ₽</strong></td>
              </tr>`).join('')}
      </tbody>
    </table>`;
}

async function openDischargeModal() {
  const patients = await api('/api/patients?size=100');
  if (!patients) return;
  const active = patients.content.filter(p => p.status === 'TREATMENT');
  $('discharge-patientId').innerHTML = active.length === 0
    ? `<option value="">Нет пациентов на лечении</option>`
    : active.map(p => `<option value="${p.id}">${escapeHtml(p.fullName)}</option>`).join('');
  openModal('modal-discharge');
}

async function doDischarge() {
  const patientId = $('discharge-patientId').value;
  const type = $('discharge-type').value;
  if (!patientId) { toast('Выберите пациента', 'warning'); return; }
  try {
    const result = await api(`/api/admin/patients/${patientId}/discharge?dischargeType=${type}`, { method: 'POST' });
    toast(`Пациент выписан (${type})`);
    closeModal('modal-discharge');
    loadAdmin();
  } catch (_) {}
}

// =============================================
// CHATS (Admin: support; Doctor: patient chats)
// =============================================

let activeChatRoomId = null;
let chatPollTimer = null;
let chatLastMsgId = 0;

async function loadChats() {
  const isAdmin = currentRole === 'ROLE_ADMIN';
  const url = isAdmin ? '/api/chat/support' : '/api/chat/doctor/rooms';
  const titleEl = $('chats-panel-title');
  if (titleEl) titleEl.textContent = isAdmin ? 'Чаты поддержки' : 'Чаты с пациентами';

  const rooms = await api(url);
  if (!rooms) return;

  const listEl = $('chat-rooms-list');
  if (!listEl) return;

  if (rooms.length === 0) {
    listEl.innerHTML = '<div class="empty-state" style="padding:24px">Чатов нет</div>';
    return;
  }

  listEl.innerHTML = rooms.map(r => {
    const name = escapeHtml(r.clientUserName || '—');
    const staff = escapeHtml(r.staffUserName || 'Поддержка');
    const displayName = isAdmin ? name : name;
    const sub = isAdmin ? `Клиент: ${name}` : `Пациент: ${name}`;
    const preview = r.lastMessage
      ? escapeHtml(r.lastMessage.substring(0, 48)) + (r.lastMessage.length > 48 ? '…' : '')
      : '<em style="color:#94a3b8">Нет сообщений</em>';
    const unreadBadge = r.unreadCount > 0
      ? `<span style="background:#ef4444;color:#fff;border-radius:10px;padding:1px 7px;font-size:.7rem;font-weight:700">${r.unreadCount}</span>`
      : '';
    const isActive = activeChatRoomId === r.id;
    return `
      <div onclick="openChat(${r.id},'${name.replace(/'/g,'\\\'')}')"
           style="padding:10px 12px;border:1px solid ${isActive ? '#93c5fd' : '#e2e8f0'};border-radius:8px;
                  cursor:pointer;margin-bottom:6px;background:${isActive ? '#eff6ff' : '#fff'};transition:background .12s"
           onmouseenter="this.style.background='#f8fafc'"
           onmouseleave="this.style.background='${isActive ? '#eff6ff' : '#fff'}'">
        <div style="display:flex;justify-content:space-between;align-items:center;gap:4px">
          <span style="font-weight:600;font-size:.875rem">${displayName}</span>
          ${unreadBadge}
        </div>
        <div style="font-size:.78rem;color:#94a3b8;margin-top:2px">${preview}</div>
      </div>`;
  }).join('');
}

async function openChat(roomId, label) {
  stopChatPoll();
  activeChatRoomId = roomId;
  chatLastMsgId = 0;

  const titleEl = $('chat-active-title');
  if (titleEl) titleEl.textContent = label;

  const area = $('chat-messages-area');
  area.innerHTML = '<div style="text-align:center;padding:32px"><div class="spinner"></div></div>';

  const inputArea = $('chat-input-area');
  if (inputArea) inputArea.style.display = 'flex';

  const msgs = await api(`/api/chat/rooms/${roomId}/messages`);
  if (!msgs) return;

  renderChatMessages(msgs, true);
  if (msgs.length > 0) chatLastMsgId = msgs[msgs.length - 1].id;

  startChatPoll();
  loadChats();
}

function renderChatMessages(msgs, replace) {
  const area = $('chat-messages-area');
  const myName = localStorage.getItem('fullName') || '';

  if (replace) {
    if (msgs.length === 0) {
      area.innerHTML = '<div style="text-align:center;color:#94a3b8;padding:48px 0">Нет сообщений. Начните диалог!</div>';
      return;
    }
    area.innerHTML = msgs.map(m => chatBubble(m, myName)).join('');
  } else {
    msgs.forEach(m => {
      const wrap = document.createElement('div');
      wrap.innerHTML = chatBubble(m, myName);
      area.appendChild(wrap.firstElementChild);
    });
  }
  area.scrollTop = area.scrollHeight;
}

function chatBubble(m, myName) {
  const mine = m.senderName === myName;
  const justify = mine ? 'flex-end' : 'flex-start';
  const bg    = mine ? '#1a6fc4' : '#f1f5f9';
  const color = mine ? '#fff'    : '#1e293b';
  const sub   = mine ? 'rgba(255,255,255,.65)' : '#94a3b8';
  const radius = mine ? '12px 12px 3px 12px' : '12px 12px 12px 3px';
  const time = m.sentAt ? new Date(m.sentAt).toLocaleTimeString('ru', { hour: '2-digit', minute: '2-digit' }) : '';
  const content = escapeHtml(m.content || '');

  return `
    <div style="display:flex;justify-content:${justify};margin-bottom:10px">
      <div style="max-width:68%;background:${bg};color:${color};padding:8px 12px;border-radius:${radius};word-break:break-word">
        ${!mine ? `<div style="font-size:.72rem;font-weight:700;color:${sub};margin-bottom:3px">${escapeHtml(m.senderName || '')}</div>` : ''}
        <div style="white-space:pre-wrap">${content}</div>
        <div style="font-size:.68rem;color:${sub};text-align:right;margin-top:3px">${time}</div>
      </div>
    </div>`;
}

function startChatPoll() {
  stopChatPoll();
  chatPollTimer = setInterval(async () => {
    if (!activeChatRoomId) return;
    try {
      const msgs = await api(`/api/chat/rooms/${activeChatRoomId}/messages/poll?sinceId=${chatLastMsgId}`);
      if (msgs && msgs.length > 0) {
        renderChatMessages(msgs, false);
        chatLastMsgId = msgs[msgs.length - 1].id;
      }
    } catch (_) {}
  }, 3000);
}

function stopChatPoll() {
  if (chatPollTimer) { clearInterval(chatPollTimer); chatPollTimer = null; }
}

async function sendChatMsg() {
  if (!activeChatRoomId) return;
  const input = $('chat-msg-input');
  const content = input?.value?.trim();
  if (!content) return;
  input.value = '';
  try {
    const msg = await api(`/api/chat/rooms/${activeChatRoomId}/messages`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    });
    if (msg) {
      renderChatMessages([msg], false);
      chatLastMsgId = msg.id;
    }
  } catch (_) {}
}

// =============================================
// PATIENT HISTORY (Doctor)
// =============================================

function showHistTab(tab) {
  ['notes', 'docs'].forEach(t => {
    const content = $(`hist-tab-${t}`);
    const btn     = $(`hist-tab-btn-${t}`);
    if (!content || !btn) return;
    const active = t === tab;
    content.style.display = active ? '' : 'none';
    btn.style.color       = active ? '#1a6fc4' : '#64748b';
    btn.style.fontWeight  = active ? '600' : '500';
    btn.style.borderBottom = active ? '2px solid #1a6fc4' : 'none';
    btn.style.marginBottom = active ? '-2px' : '0';
  });
}

async function openPatientHistoryModal(patientId) {
  $('hist-patient-id').value = patientId;
  $('hist-patient-name').textContent = `#${patientId}`;
  showHistTab('notes');
  openModal('modal-patient-history');
  await loadPatientHistory(patientId);
}

window.openPatientHistoryModal = openPatientHistoryModal;

async function loadPatientHistory(patientId) {
  const data = await api(`/api/medical/history/patient/${patientId}`);
  if (!data) return;
  $('hist-patient-name').textContent = data.patientName || `#${patientId}`;

  const noteBg     = { DIAGNOSIS: '#fef2f2', OBSERVATION: '#f0fdf4', NOTE: '#eff6ff' };
  const noteBorder = { DIAGNOSIS: '#fecaca', OBSERVATION: '#bbf7d0', NOTE: '#bfdbfe' };

  $('hist-notes-list').innerHTML = data.notes.length === 0
    ? '<div class="empty-state" style="padding:16px 0">Заметок нет</div>'
    : data.notes.map(n => `
        <div style="border:1px solid ${noteBorder[n.type]||'#e2e8f0'};background:${noteBg[n.type]||'#fff'};
                    border-radius:8px;padding:12px;margin-bottom:8px">
          <div style="display:flex;justify-content:space-between;align-items:flex-start">
            <span style="font-size:.75rem;font-weight:700;text-transform:uppercase;letter-spacing:.04em;color:#475569">
              ${escapeHtml(n.typeLabel)}
            </span>
            <span style="font-size:.72rem;color:#94a3b8">
              ${n.createdAt ? new Date(n.createdAt).toLocaleDateString('ru') : ''}
            </span>
          </div>
          <div style="margin-top:2px;font-size:.78rem;color:#64748b">Врач: ${escapeHtml(n.doctorName)}</div>
          <div style="margin-top:8px;white-space:pre-wrap;color:#1e293b;font-size:.9rem">${escapeHtml(n.content)}</div>
          ${!n.visibleToClient
            ? '<div style="margin-top:4px;font-size:.72rem;color:#94a3b8">🔒 Не видна пациенту</div>'
            : ''}
        </div>`).join('');

  $('hist-docs-list').innerHTML = data.documents.length === 0
    ? '<div class="empty-state" style="padding:16px 0">Документов нет</div>'
    : data.documents.map(d => `
        <div style="border:1px solid #e2e8f0;border-radius:8px;padding:12px;margin-bottom:8px;
                    background:${d.active ? '#fff' : '#f8fafc'}">
          <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:8px">
            <div>
              <strong style="font-size:.9rem">${escapeHtml(d.title)}</strong>
              <span style="font-size:.75rem;font-weight:600;padding:2px 8px;border-radius:4px;
                           background:#dbeafe;color:#1d4ed8;margin-left:6px">${escapeHtml(d.typeLabel)}</span>
            </div>
            <span style="font-size:.72rem;color:#94a3b8;white-space:nowrap">
              ${d.issuedAt ? new Date(d.issuedAt).toLocaleDateString('ru') : ''}
            </span>
          </div>
          <div style="margin-top:2px;font-size:.78rem;color:#64748b">Врач: ${escapeHtml(d.doctorName)}</div>
          <div style="margin-top:8px;white-space:pre-wrap;color:#475569;font-size:.875rem">${escapeHtml(d.content)}</div>
          ${d.validUntil ? `<div style="margin-top:4px;font-size:.72rem;color:#64748b">Действителен до: ${escapeHtml(d.validUntil)}</div>` : ''}
          ${!d.active ? '<span style="font-size:.72rem;color:#94a3b8">Недействителен</span>' : ''}
        </div>`).join('');
}

async function savePatientNote() {
  const patientId = $('hist-patient-id').value;
  if (!patientId) return;
  const content = $('note-content').value.trim();
  if (!content) { toast('Введите содержание заметки', 'warning'); return; }
  try {
    await api('/api/medical/notes', {
      method: 'POST',
      body: JSON.stringify({
        patientId: Number(patientId),
        type: $('note-type').value,
        content,
        visibleToClient: $('note-visible').value === 'true',
      }),
    });
    toast('Заметка добавлена');
    $('note-content').value = '';
    await loadPatientHistory(patientId);
  } catch (_) {}
}

async function savePatientDoc() {
  const patientId = $('hist-patient-id').value;
  if (!patientId) return;
  const title   = $('doc-title').value.trim();
  const content = $('doc-content').value.trim();
  if (!title || !content) { toast('Заполните название и содержание', 'warning'); return; }
  try {
    await api('/api/medical/documents', {
      method: 'POST',
      body: JSON.stringify({
        patientId: Number(patientId),
        type: $('doc-type').value,
        title,
        content,
        validUntil: $('doc-valid-until').value || null,
      }),
    });
    toast('Документ создан');
    $('doc-title').value = '';
    $('doc-content').value = '';
    $('doc-valid-until').value = '';
    await loadPatientHistory(patientId);
  } catch (_) {}
}

// =============================================
// Init
// =============================================

document.addEventListener('DOMContentLoaded', () => {
  const token = localStorage.getItem('token');
  if (!token) {
    window.location.href = '/login.html';
    return;
  }

  const fullName = localStorage.getItem('fullName') || localStorage.getItem('username') || '';
  currentRole = localStorage.getItem('role') || '';
  if ($('user-name'))  $('user-name').textContent  = fullName;
  if ($('role-badge')) $('role-badge').textContent = roleLabel(currentRole);

  applyRoleVisibility();
  navigate('dashboard');
});
