package com.itq.docservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "approval_registry")
@Getter
@Setter
public class ApprovalRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false, unique = true)
    private Long documentId;

    @Column(name = "document_number", nullable = false, length = 50)
    private String documentNumber;

    @Column(name = "approved_by", nullable = false)
    private String approvedBy;

    @Column(name = "approved_at", nullable = false)
    private OffsetDateTime approvedAt;
}
