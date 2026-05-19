package com.hospital.entity;

/**
 * Роль пользователя системы для разграничения прав доступа (RBAC).
 *
 * <p>Текущая модель прав:
 * <ul>
 *   <li>ROLE_ADMIN  — полный доступ ко всем операциям системы.</li>
 *   <li>ROLE_DOCTOR — доступ к медицинским данным пациентов, назначению услуг.</li>
 *   <li>ROLE_NURSE  — размещение пациентов по палатам, базовые операции.</li>
 *   <li>ROLE_CLIENT — пациентский портал: запись на приём, заказ услуг.</li>
 * </ul>
 */
public enum Role {
    ROLE_ADMIN,
    ROLE_DOCTOR,
    ROLE_NURSE,
    ROLE_CLIENT
}
