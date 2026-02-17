package com.itq.docservice.repository;

import com.itq.docservice.entity.Document;
import com.itq.docservice.entity.DocumentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;

public class DocumentSpecification {

    public static Specification<Document> hasStatus(DocumentStatus status) {
        return (root, query, cb) ->
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Document> hasAuthor(String author) {
        return (root, query, cb) ->
                (author == null || author.isBlank()) ? cb.conjunction()
                        : cb.equal(cb.lower(root.get("author")), author.toLowerCase());
    }

    public static Specification<Document> createdAfter(OffsetDateTime from) {
        return (root, query, cb) ->
                from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Document> createdBefore(OffsetDateTime to) {
        return (root, query, cb) ->
                to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
