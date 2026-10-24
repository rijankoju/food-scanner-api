package com.foodscan.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/** A user's change to a scan item; the training signal for improving scans. */
@Entity
@Table(name = "scan_corrections")
class ScanCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_item_id")
    private Long scanItemId;

    private String field;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected ScanCorrection() {
    }

    ScanCorrection(Long scanItemId, String field, Object oldValue, Object newValue) {
        this.scanItemId = scanItemId;
        this.field = field;
        this.oldValue = oldValue == null ? null : oldValue.toString();
        this.newValue = newValue == null ? null : newValue.toString();
    }
}
