package com.nagriksetu.complaint;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class ComplaintSpecifications {

    private ComplaintSpecifications() {
    }

    /** Public complaints anyone may browse, plus the viewer's own complaints. */
    static Specification<Complaint> visibleTo(Long viewerId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("reporter").get("id"), viewerId),
                cb.and(cb.isTrue(root.get("publicVisible")), cb.notEqual(root.get("status"), ComplaintStatus.REJECTED)));
    }

    static Specification<Complaint> reportedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("reporter").get("id"), userId);
    }

    static Specification<Complaint> filters(String q, String categoryCode, Collection<ComplaintStatus> statuses,
                                            String locality, Instant from, Instant to) {
        return (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("category", JoinType.INNER);
            }
            List<Predicate> p = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + escape(q.trim().toLowerCase(Locale.ROOT)) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like, '\\'),
                        cb.like(cb.lower(root.get("description")), like, '\\'),
                        cb.like(cb.lower(root.get("referenceNo")), like, '\\'),
                        cb.like(cb.lower(root.get("locality")), like, '\\')));
            }
            if (categoryCode != null && !categoryCode.isBlank()) {
                p.add(cb.equal(root.get("category").get("code"), categoryCode));
            }
            if (statuses != null && !statuses.isEmpty()) {
                p.add(root.get("status").in(statuses));
            }
            if (locality != null && !locality.isBlank()) {
                p.add(cb.like(cb.lower(root.get("locality")), "%" + escape(locality.trim().toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("createdAt"), to));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    static Specification<Complaint> inBounds(Double minLat, Double maxLat, Double minLng, Double maxLng) {
        return (root, query, cb) -> {
            if (minLat == null || maxLat == null || minLng == null || maxLng == null) {
                return cb.conjunction();
            }
            return cb.and(
                    cb.between(root.get("latitude"), java.math.BigDecimal.valueOf(minLat), java.math.BigDecimal.valueOf(maxLat)),
                    cb.between(root.get("longitude"), java.math.BigDecimal.valueOf(minLng), java.math.BigDecimal.valueOf(maxLng)));
        };
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
