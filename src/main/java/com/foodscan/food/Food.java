package com.foodscan.food;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A food with nutrient values per 100 g of edible portion. */
@Entity
@Table(name = "foods")
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "foods_seq")
    @SequenceGenerator(name = "foods_seq", sequenceName = "foods_seq", allocationSize = 50)
    private Long id;

    private String source;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "data_type")
    private String dataType;

    private String name;
    private String brand;
    private String category;

    @Column(name = "kcal_100g")
    private BigDecimal kcal100g;

    @Column(name = "protein_100g")
    private BigDecimal protein100g;

    @Column(name = "carbs_100g")
    private BigDecimal carbs100g;

    @Column(name = "fat_100g")
    private BigDecimal fat100g;

    @Column(name = "fiber_100g")
    private BigDecimal fiber100g;

    @Column(name = "sugar_100g")
    private BigDecimal sugar100g;

    @Column(name = "sodium_mg_100g")
    private BigDecimal sodiumMg100g;

    @Column(name = "energy_basis")
    private String energyBasis;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "food", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position")
    private List<FoodPortion> portions = new ArrayList<>();

    protected Food() {
    }

    public Food(String source, String sourceRef) {
        this.source = source;
        this.sourceRef = sourceRef;
    }

    /** Replaces the portions with the given (label, grams) pairs, in order. */
    public void replacePortions(List<FoodPortion.Measure> measures) {
        portions.clear();
        for (int i = 0; i < measures.size(); i++) {
            portions.add(new FoodPortion(this, measures.get(i).label(), measures.get(i).grams(), i));
        }
    }

    public Long getId() { return id; }
    public String getSource() { return source; }
    public String getSourceRef() { return sourceRef; }
    public String getDataType() { return dataType; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public String getCategory() { return category; }
    public BigDecimal getKcal100g() { return kcal100g; }
    public BigDecimal getProtein100g() { return protein100g; }
    public BigDecimal getCarbs100g() { return carbs100g; }
    public BigDecimal getFat100g() { return fat100g; }
    public BigDecimal getFiber100g() { return fiber100g; }
    public BigDecimal getSugar100g() { return sugar100g; }
    public BigDecimal getSodiumMg100g() { return sodiumMg100g; }
    public String getEnergyBasis() { return energyBasis; }
    public Long getOwnerUserId() { return ownerUserId; }
    public List<FoodPortion> getPortions() { return portions; }

    public void setDataType(String dataType) { this.dataType = dataType; }
    public void setName(String name) { this.name = name; }
    public void setBrand(String brand) { this.brand = brand; }
    public void setCategory(String category) { this.category = category; }
    public void setKcal100g(BigDecimal kcal100g) { this.kcal100g = kcal100g; }
    public void setProtein100g(BigDecimal protein100g) { this.protein100g = protein100g; }
    public void setCarbs100g(BigDecimal carbs100g) { this.carbs100g = carbs100g; }
    public void setFat100g(BigDecimal fat100g) { this.fat100g = fat100g; }
    public void setFiber100g(BigDecimal fiber100g) { this.fiber100g = fiber100g; }
    public void setSugar100g(BigDecimal sugar100g) { this.sugar100g = sugar100g; }
    public void setSodiumMg100g(BigDecimal sodiumMg100g) { this.sodiumMg100g = sodiumMg100g; }
    public void setEnergyBasis(String energyBasis) { this.energyBasis = energyBasis; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
}
