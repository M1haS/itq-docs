package com.itq.docservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "document_history")
@Getter
@Setter
public class DocumentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentAction action;

    @Column(name = "performed_at", nullable = false)
    private OffsetDateTime performedAt;

    @Column(length = 1000)
    private String comment;
}
