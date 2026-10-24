package com.foodscan.scan;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scans")
public class Scan {

    public enum Status { processing, ready, failed }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "image_key")
    private String imageKey;

    @Enumerated(EnumType.STRING)
    private Status status = Status.processing;

    private String model;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    /** The model's output exactly as returned, kept for debugging and evaluation. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_ai_json")
    private String rawAiJson;

    private String error;

    @Column(name = "meal_name")
    private String mealName;

    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "scan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ScanItem> items = new ArrayList<>();

    protected Scan() {
    }

    public Scan(Long userId, String imageKey) {
        this.userId = userId;
        this.imageKey = imageKey;
    }

    void complete(FoodVisionClient.Result result, String rawJson, int latencyMs, List<ScanItem> newItems,
                  Instant now) {
        this.status = Status.ready;
        this.model = result.model();
        this.inputTokens = (int) result.inputTokens();
        this.outputTokens = (int) result.outputTokens();
        this.latencyMs = latencyMs;
        this.rawAiJson = rawJson;
        this.mealName = result.analysis().mealName();
        this.notes = result.analysis().notes();
        this.completedAt = now;
        items.clear();
        items.addAll(newItems);
    }

    void fail(String error, Instant now) {
        this.status = Status.failed;
        this.error = error;
        this.completedAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getImageKey() { return imageKey; }
    public Status getStatus() { return status; }
    public String getModel() { return model; }
    public String getError() { return error; }
    public String getMealName() { return mealName; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ScanItem> getItems() { return items; }
}
