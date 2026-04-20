package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "patient_paid_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PatientPaidService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @ToString.Exclude
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_service_id", nullable = false)
    @ToString.Exclude
    private PaidService paidService;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime assignedDate = LocalDateTime.now();

    @Column(name = "is_paid", nullable = false)
    @Builder.Default
    private boolean paid = false;
}
