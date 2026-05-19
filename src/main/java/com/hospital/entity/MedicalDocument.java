package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medical_document")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MedicalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @ToString.Exclude
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    @ToString.Exclude
    private Doctor doctor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MedicalDocumentType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    /** Дата окончания действия документа (необязательна, например для рецептов). */
    @Column
    private LocalDate validUntil;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @PrePersist
    void onCreate() {
        issuedAt = LocalDateTime.now();
    }
}
