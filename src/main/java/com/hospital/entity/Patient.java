package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "patient")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(unique = true, nullable = false)
    private String snils;

    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(nullable = false)
    private LocalDate registrationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PatientStatus status = PatientStatus.TREATMENT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_doctor_id")
    @ToString.Exclude
    private Doctor currentDoctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_ward_id")
    @ToString.Exclude
    private Ward currentWard;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
