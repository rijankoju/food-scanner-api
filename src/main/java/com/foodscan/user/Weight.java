package com.foodscan.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One body-weight measurement per user per day. */
@Entity
@Table(name = "weights")
@IdClass(Weight.Key.class)
public class Weight {

    public record Key(Long userId, LocalDate date) implements Serializable {
    }

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    private LocalDate date;

    private BigDecimal kg;

    protected Weight() {
    }

    public Weight(Long userId, LocalDate date, BigDecimal kg) {
        this.userId = userId;
        this.date = date;
        this.kg = kg;
    }

    public Long getUserId() { return userId; }
    public LocalDate getDate() { return date; }
    public BigDecimal getKg() { return kg; }
    public void setKg(BigDecimal kg) { this.kg = kg; }
}
