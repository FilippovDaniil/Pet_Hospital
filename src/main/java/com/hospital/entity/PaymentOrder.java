package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "payment_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 64)
    private String orderNumber;

    @Column(name = "alfa_order_id", length = 64)
    private String alfaOrderId;

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

    @Column(name = "amount_kopecks", nullable = false)
    private Long amountKopecks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentOrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = PaymentOrderStatus.PENDING;
        }
    }
}
