package com.studygram.controller;

import com.studygram.model.Resource;
import com.studygram.model.ResourceLike;
import com.studygram.model.ResourceReport;
import com.studygram.model.User;
import com.studygram.repository.ResourceRepository;
import com.studygram.repository.ResourceLikeRepository;
import com.studygram.repository.ResourceReportRepository;
import com.studygram.repository.UserRepository;
import com.studygram.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Controller
public class HomeController {

    private static final long MAX_UPLOAD_SIZE = 10L * 1024 * 1024;

    private static final List<String> ALLOWED_REPORT_REASONS = List.of(
            "Copyright",
            "Inappropriate",
            "Misleading",
            "Spam",
            "Other"
    );

    private final UserService userService;
    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceLikeRepository resourceLikeRepository;
    private final ResourceReportRepository resourceReportRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public HomeController(
            UserService userService,
            UserRepository userRepository,
            ResourceRepository resourceRepository,
            ResourceLikeRepository resourceLikeRepository,
            ResourceReportRepository resourceReportRepository) {

        this.userService = userService;
        this.userRepository = userRepository;
        this.resourceRepository = resourceRepository;
        this.resourceLikeRepository = resourceLikeRepository;
        this.resourceReportRepository = resourceReportRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @GetMapping("/")
    public String home() {
        return "login";
    }

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    @PostMapping("/signup")
    public String register(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password) {

        if (name == null || email == null || password == null
                || name.isBlank() || email.isBlank()
                || password.isBlank()) {
            return "redirect:/signup?error=invalid";
        }

        boolean registered = userService.registerUser(
                name.trim(),
                email.trim().toLowerCase(),
                password
        );

        return registered
                ? "redirect:/"
                : "redirect:/signup?error=exists";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam String email,
            @RequestParam String password,
            HttpServletRequest request) {

        if (email == null || password == null
                || email.isBlank() || password.isBlank()) {
            return "redirect:/?error=invalid";
        }

        User user = userRepository
                .findByEmail(email.trim().toLowerCase())
                .orElse(null);

        if (user == null
                || user.getPassword() == null
                || !passwordEncoder.matches(password, user.getPassword())) {
            return "redirect:/?error=invalid";
        }

        // Replace the previous session after successful authentication.
        HttpSession oldSession = request.getSession(false);

        if (oldSession != null) {
            oldSession.invalidate();
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("userName", user.getName());
        session.setAttribute("userEmail", user.getEmail());

        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {

        if (!isStudentLoggedIn(session)) {
            return "redirect:/";
        }

        String userEmail = (String) session.getAttribute("userEmail");

        model.addAttribute("resources", resourceRepository.findAll());

        model.addAttribute(
                "myReports",
                resourceReportRepository
                        .findByReporterEmailOrderByReportedAtDesc(userEmail)
        );

        return "dashboard";
    }

    @GetMapping("/category/{category}")
    public String category(
            @PathVariable String category,
            HttpSession session,
            Model model) {

        if (!isStudentLoggedIn(session)) {
            return "redirect:/";
        }

        List<Resource> resources =
                resourceRepository.findByCategoryOrderByUploadDateDesc(category);

        model.addAttribute("resources", resources);
        model.addAttribute("category", category);

        return "resource-list";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return "redirect:/";
    }

    @GetMapping("/resource/{id}")
    public String resource(
            @PathVariable Long id,
            HttpSession session,
            Model model) {

        if (!isStudentLoggedIn(session)) {
            return "redirect:/";
        }

        Resource resource = resourceRepository.findById(id).orElse(null);

        if (resource == null) {
            return "redirect:/dashboard";
        }

        model.addAttribute("resource", resource);

        return "resource";
    }

    @GetMapping("/upload")
    public String uploadPage(HttpSession session) {

        if (!isStudentLoggedIn(session)) {
            return "redirect:/";
        }

        return "upload";
    }

    @PostMapping("/upload")
    public String uploadResource(
            @RequestParam String title,
            @RequestParam String category,
            @RequestParam String subject,
            @RequestParam String description,
            @RequestParam String tags,
            @RequestParam("file") MultipartFile file,
            HttpSession session) {

        if (!isStudentLoggedIn(session)) {
            return "redirect:/";
        }

        if (title == null || category == null || subject == null
                || description == null || tags == null
                || title.isBlank() || category.isBlank()
                || subject.isBlank()) {
            return "redirect:/upload?error=invalid";
        }

        if (file == null || file.isEmpty()) {
            return "redirect:/upload?error=nofile";
        }

        if (file.getSize() > MAX_UPLOAD_SIZE) {
            return "redirect:/upload?error=filesize";
        }

        String originalFileName = file.getOriginalFilename();

        if (originalFileName == null
                || !originalFileName.toLowerCase().endsWith(".pdf")) {
            return "redirect:/upload?error=filetype";
        }

        try {
            byte[] fileBytes = file.getBytes();

            // Check the PDF signature instead of trusting the supplied MIME type.
            if (!isPdfFile(fileBytes)) {
                return "redirect:/upload?error=filetype";
            }

            Path uploadDirectory = Paths.get("uploads")
                    .toAbsolutePath()
                    .normalize();

            Files.createDirectories(uploadDirectory);

            // Never use the submitted filename as the stored filename.
            String storedFileName = UUID.randomUUID() + ".pdf";

            Path destination = uploadDirectory
                    .resolve(storedFileName)
                    .normalize();

            if (!destination.startsWith(uploadDirectory)) {
                return "redirect:/upload?error=upload";
            }

            Files.copy(
                    new java.io.ByteArrayInputStream(fileBytes),
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );

            Resource resource = new Resource();
            resource.setTitle(title.trim());
            resource.setCategory(category.trim());
            resource.setSubject(subject.trim());
            resource.setDescription(description.trim());
            resource.setTags(tags.trim());
            resource.setUploader(
                    (String) session.getAttribute("userName")
            );
            resource.setFileName(storedFileName);

            resourceRepository.save(resource);

            return "redirect:/dashboard";

        } catch (IOException e) {
            return "redirect:/upload?error=upload";
        }
    }

    @PostMapping("/resource/{id}/like")
    public String likeResource(
            @PathVariable Long id,
            HttpSession session) {

        if (!isStudentLoggedIn(session)) {
            return "redirect:/";
        }

        String userEmail = (String) session.getAttribute("userEmail");

        Resource resource = resourceRepository.findById(id).orElse(null);

        if (resource == null) {
            return "redirect:/dashboard";
        }

        boolean alreadyLiked =
                resourceLikeRepository
                        .existsByUserEmailAndResourceId(userEmail, id);

        if (!alreadyLiked) {
            ResourceLike resourceLike = new ResourceLike(userEmail, id);
            resourceLikeRepository.save(resourceLike);

            resource.incrementLikes();
            resourceRepository.save(resource);
        }

        return "redirect:/resource/" + id;
    }

    @PostMapping("/resource/{id}/report")
    public String reportResource(
            @PathVariable Long id,
            @RequestParam String reason,
            @RequestParam(required = false, defaultValue = "") String details,
            HttpSession session) {

        if (!isStudentLoggedIn(session)) {
            return "redirect:/";
        }

        String userEmail = (String) session.getAttribute("userEmail");

        Resource resource = resourceRepository.findById(id).orElse(null);

        if (resource == null) {
            return "redirect:/dashboard";
        }

        if (reason == null || !ALLOWED_REPORT_REASONS.contains(reason)) {
            return "redirect:/resource/" + id + "?error=invalidreport";
        }

        String safeDetails = details == null ? "" : details.trim();

        if (safeDetails.length() > 1000) {
            safeDetails = safeDetails.substring(0, 1000);
        }

        ResourceReport report = new ResourceReport();
        report.setResource(resource);
        report.setReporterEmail(userEmail);
        report.setReason(reason);
        report.setDetails(safeDetails);
        report.setStatus("PENDING");

        resourceReportRepository.save(report);

        return "redirect:/resource/" + id + "?reported=true";
    }

    @GetMapping("/resource/{id}/download")
    public ResponseEntity<byte[]> downloadResource(
            @PathVariable Long id,
            HttpSession session) {

        // Reuse the existing login session; no second password prompt.
        if (!isStudentLoggedIn(session)) {
            return ResponseEntity.status(401).build();
        }

        Resource resource = resourceRepository.findById(id).orElse(null);

        if (resource == null || resource.getFileName() == null
                || resource.getFileName().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path uploadDirectory = Paths.get("uploads")
                    .toAbsolutePath()
                    .normalize();

            Path filePath = uploadDirectory
                    .resolve(resource.getFileName())
                    .normalize();

            if (!filePath.startsWith(uploadDirectory)
                    || !Files.isRegularFile(filePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] fileContent = Files.readAllBytes(filePath);

            resource.incrementDownloads();
            resourceRepository.save(resource);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"studygram-resource.pdf\""
                    )
                    .body(fileContent);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private boolean isStudentLoggedIn(HttpSession session) {
        return session != null
                && session.getAttribute("userName") instanceof String
                && session.getAttribute("userEmail") instanceof String;
    }

    private boolean isPdfFile(byte[] bytes) {
        return bytes != null
                && bytes.length >= 5
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F'
                && bytes[4] == '-';
    }
}