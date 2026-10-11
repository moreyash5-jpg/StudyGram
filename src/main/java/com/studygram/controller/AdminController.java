package com.studygram.controller;

import com.studygram.model.Resource;
import com.studygram.model.ResourceReport;
import com.studygram.repository.ResourceRepository;
import com.studygram.repository.ResourceReportRepository;
import com.studygram.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class AdminController {

    @Value("${studygram.admin.username:studygram-admin}")
    private String adminUsername;

    @Value("${studygram.admin.password-hash:}")
    private String adminPasswordHash;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceReportRepository resourceReportRepository;

    public AdminController(
            UserRepository userRepository,
            ResourceRepository resourceRepository,
            ResourceReportRepository resourceReportRepository) {
        this.userRepository = userRepository;
        this.resourceRepository = resourceRepository;
        this.resourceReportRepository = resourceReportRepository;
    }

    @GetMapping("/admin/login")
    public String adminLogin(HttpSession session) {
        return isAdmin(session)
                ? "redirect:/admin/dashboard"
                : "admin-login";
    }

    @PostMapping("/admin/login")
    public String processAdminLogin(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        boolean valid = username != null
                && password != null
                && username.equals(adminUsername)
                && adminPasswordHash != null
                && !adminPasswordHash.isBlank()
                && passwordEncoder.matches(password, adminPasswordHash);

        if (!valid) {
            redirectAttributes.addFlashAttribute(
                    "error", "Invalid admin username or password.");
            return "redirect:/admin/login";
        }

        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }

        HttpSession newSession = request.getSession(true);
        newSession.setAttribute("adminAuthenticated", true);
        newSession.setAttribute("adminUsername", adminUsername);

        return "redirect:/admin/dashboard";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/admin/login";
        }

        List<Resource> resources = resourceRepository.findAll();

        List<ResourceReport> reports =
                resourceReportRepository.findAllByOrderByReportedAtDesc();

        long totalDownloads = resources.stream()
                .mapToLong(Resource::getDownloads)
                .sum();

        long totalLikes = resources.stream()
                .mapToLong(Resource::getLikes)
                .sum();

        Map<String, Long> reportsByStatus = reports.stream()
                .collect(Collectors.groupingBy(
                        report -> report.getStatus() == null
                                ? "PENDING"
                                : report.getStatus().toUpperCase(Locale.ROOT),
                        Collectors.counting()));

        Map<String, Long> resourcesByCategory = resources.stream()
                .collect(Collectors.groupingBy(
                        resource -> resource.getCategory() == null
                                || resource.getCategory().isBlank()
                                ? "Uncategorized"
                                : resource.getCategory(),
                        Collectors.counting()));

        List<Resource> topResources = resources.stream()
                .sorted(Comparator.comparingInt(Resource::getDownloads)
                        .reversed())
                .limit(5)
                .toList();

        model.addAttribute("adminUsername", adminUsername);
        model.addAttribute("totalStudents", userRepository.count());
        model.addAttribute("totalResources", resources.size());
        model.addAttribute("totalReports", reports.size());

        model.addAttribute(
                "pendingReports",
                resourceReportRepository.countByStatus("PENDING"));

        model.addAttribute(
                "reviewedReports",
                resourceReportRepository.countByStatus("REVIEWED"));

        model.addAttribute(
                "resolvedReports",
                resourceReportRepository.countByStatus("RESOLVED"));

        model.addAttribute("totalDownloads", totalDownloads);
        model.addAttribute("totalLikes", totalLikes);
        model.addAttribute("reportsByStatus", reportsByStatus);
        model.addAttribute("resourcesByCategory", resourcesByCategory);
        model.addAttribute("topResources", topResources);
        model.addAttribute("resources", resources);
        model.addAttribute("reports", reports);

        return "admin-dashboard";
    }

    @PostMapping("/admin/reports/{id}/status")
    public String updateReportStatus(
            @PathVariable Long id,
            @RequestParam String status,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!isAdmin(session)) {
            return "redirect:/admin/login";
        }

        if (status == null
                || !List.of("PENDING", "REVIEWED", "RESOLVED")
                        .contains(status.toUpperCase(Locale.ROOT))) {
            redirectAttributes.addFlashAttribute(
                    "adminMessage", "Invalid report status.");
            return "redirect:/admin/dashboard#reports";
        }

        String safeStatus = status.toUpperCase(Locale.ROOT);

        ResourceReport report =
                resourceReportRepository.findById(id).orElse(null);

        if (report == null) {
            redirectAttributes.addFlashAttribute(
                    "adminMessage", "Report not found.");
        } else {
            report.setStatus(safeStatus);
            resourceReportRepository.save(report);

            redirectAttributes.addFlashAttribute(
                    "adminMessage", "Report status updated successfully.");
        }

        return "redirect:/admin/dashboard#reports";
    }

    @PostMapping("/admin/reports/{id}/delete-resource")
    public String deleteReportedResource(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!isAdmin(session)) {
            return "redirect:/admin/login";
        }

        ResourceReport report =
                resourceReportRepository.findById(id).orElse(null);

        if (report == null) {
            redirectAttributes.addFlashAttribute(
                    "adminMessage", "Report not found.");
            return "redirect:/admin/dashboard#reports";
        }

        Resource resource = report.getResource();

        if (resource == null) {
            resourceReportRepository.delete(report);
            redirectAttributes.addFlashAttribute(
                    "adminMessage", "Report removed.");
            return "redirect:/admin/dashboard#reports";
        }

        Long resourceId = resource.getId();

        deleteReportsForResource(resourceId);
        resourceRepository.deleteById(resourceId);

        redirectAttributes.addFlashAttribute(
                "adminMessage",
                "Reported resource and associated reports deleted.");

        return "redirect:/admin/dashboard#reports";
    }

    @PostMapping("/admin/resources/{id}/delete")
    public String deleteResource(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!isAdmin(session)) {
            return "redirect:/admin/login";
        }

        if (!resourceRepository.existsById(id)) {
            redirectAttributes.addFlashAttribute(
                    "adminMessage", "Resource not found.");
            return "redirect:/admin/dashboard#resources";
        }

        deleteReportsForResource(id);
        resourceRepository.deleteById(id);

        redirectAttributes.addFlashAttribute(
                "adminMessage",
                "Resource and associated reports deleted successfully.");

        return "redirect:/admin/dashboard#resources";
    }

    private void deleteReportsForResource(Long resourceId) {
        List<ResourceReport> relatedReports =
                resourceReportRepository.findAll().stream()
                        .filter(report ->
                                report.getResource() != null
                                && resourceId.equals(
                                        report.getResource().getId()))
                        .toList();

        resourceReportRepository.deleteAll(relatedReports);
    }

    @PostMapping("/admin/logout")
    public String adminLogout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return "redirect:/";
    }

    private boolean isAdmin(HttpSession session) {
        return session != null
                && Boolean.TRUE.equals(
                        session.getAttribute("adminAuthenticated"))
                && adminUsername.equals(
                        session.getAttribute("adminUsername"));
    }
}