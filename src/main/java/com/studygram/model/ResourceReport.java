
package com.studygram.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resource_reports")
public class ResourceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @Column(nullable = false)
    private String reporterEmail;

    @Column(nullable = false, length = 60)
    private String reason;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(nullable = false)
    private LocalDateTime reportedAt = LocalDateTime.now();

    public ResourceReport() {
    }

    public Long getId() { return id; }

    public Resource getResource() { return resource; }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public String getReporterEmail() { return reporterEmail; }

    public void setReporterEmail(String reporterEmail) {
        this.reporterEmail = reporterEmail;
    }

    public String getReason() { return reason; }

    public void setReason(String reason) { this.reason = reason; }

    public String getDetails() { return details; }

    public void setDetails(String details) { this.details = details; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getReportedAt() { return reportedAt; }
}
