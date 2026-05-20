package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "client_service_order")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientServiceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_user_id", nullable = false)
    private User clientUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_service_id", nullable = false)
    private PaidService paidService;

    @Column(name = "contact_phone", length = 25)
    private String contactPhone;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    @Column(name = "preferred_time")
    private LocalTime preferredTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientServiceOrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = ClientServiceOrderStatus.PENDING;
        }
    }
}
