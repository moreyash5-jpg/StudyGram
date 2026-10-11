package com.studygram.repository;

import com.studygram.model.ResourceReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResourceReportRepository
        extends JpaRepository<ResourceReport, Long> {

    List<ResourceReport> findAllByOrderByReportedAtDesc();

    List<ResourceReport> findByReporterEmailOrderByReportedAtDesc(String reporterEmail);

    long countByStatus(String status);
}