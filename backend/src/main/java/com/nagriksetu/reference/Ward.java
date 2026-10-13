package com.nagriksetu.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "wards")
public class Ward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    /** JSON array of [lat, lng] vertices; null when no reliable boundary is configured. */
    @Column(name = "boundary_json")
    private String boundaryJson;

    @Column(name = "boundary_source")
    private String boundarySource;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Ward() {
    }

    public Ward(String code, String name, String city, String boundaryJson, String boundarySource) {
        this.code = code;
        this.name = name;
        this.city = city;
        this.boundaryJson = boundaryJson;
        this.boundarySource = boundarySource;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public String getBoundaryJson() {
        return boundaryJson;
    }

    public String getBoundarySource() {
        return boundarySource;
    }
}
