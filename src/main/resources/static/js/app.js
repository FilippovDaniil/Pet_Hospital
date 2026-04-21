'use strict';

// =============================================
// Hospital IS — Frontend SPA
// =============================================

const API = '';  // same-origin; backend on :8080

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
  document.querySelectorAll('.section-page').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.sidebar-nav a').forEach(a => a.classList.remove('active'));
  const page = $(`page-${section}`);
  if (page) page.classList.add('active');
  const link = document.querySelector(`[data-nav="${section}"]`);
  if (link) link.classList.add('active');
  $('topbarTitle').textContent = {
    dashboard: 'Дашборд',
    patients: 'Пациенты',
    doctors: 'Врачи',
    departments: 'Отделения',
    wards: 'Палаты',
    services: 'Платные услуги',
    admin: 'Администрация',
  }[section] || section;

  const loaders = {
    dashboard: loadDashboard,
    patients: () => loadPatients(0),
    doctors: () => loadDoctors(0),
    departments: loadDepartments,
    wards: loadWards,
    services: () => loadServices(0),
    admin: loadAdmin,
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
            <button class="btn btn-outline btn-sm btn-icon" title="Назначить врача" onclick="openAssignDoctorModal(${p.id})">👨‍⚕️</button>
            <button class="btn btn-outline btn-sm btn-icon" title="Услуги" onclick="openPatientServicesModal(${p.id}, '${escapeHtml(p.fullName)}')">🧾</button>
            <button class="btn btn-danger btn-sm btn-icon" title="Удалить" onclick="deletePatient(${p.id})">🗑</button>
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
            <button class="btn btn-outline btn-sm btn-icon" title="Назначить врача" onclick="openAssignDoctorModal(${p.id})">👨‍⚕️</button>
            <button class="btn btn-outline btn-sm btn-icon" title="Услуги" onclick="openPatientServicesModal(${p.id}, '${escapeHtml(p.fullName)}')">🧾</button>
            <button class="btn btn-danger btn-sm btn-icon" title="Удалить" onclick="deletePatient(${p.id})">🗑</button>
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
            <button class="btn btn-danger btn-sm btn-icon" style="margin-left:6px" onclick="deleteDoctor(${d.id})">🗑</button>
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
            <button class="btn btn-danger btn-sm btn-icon" style="margin-left:8px" onclick="deleteDepartment(${d.id})">🗑</button>
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
// Init
// =============================================

document.addEventListener('DOMContentLoaded', () => {
  const token = localStorage.getItem('token');
  if (!token) {
    window.location.href = '/login.html';
    return;
  }

  const fullName = localStorage.getItem('fullName') || localStorage.getItem('username') || '';
  const role = localStorage.getItem('role') || '';
  if ($('user-name'))  $('user-name').textContent  = fullName;
  if ($('role-badge')) $('role-badge').textContent = roleLabel(role);

  navigate('dashboard');
});
