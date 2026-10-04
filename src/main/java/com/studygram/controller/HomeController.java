package com.studygram.controller;

import com.studygram.model.Resource;
import com.studygram.model.User;
import com.studygram.repository.ResourceRepository;
import com.studygram.repository.UserRepository;
import com.studygram.service.UserService;

import jakarta.servlet.http.HttpSession;

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
import java.util.List;
import java.util.UUID;

@Controller
public class HomeController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public HomeController(
            UserService userService,
            UserRepository userRepository,
            ResourceRepository resourceRepository) {

        this.userService = userService;
        this.userRepository = userRepository;
        this.resourceRepository = resourceRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    // =========================
    // LOGIN / HOME
    // =========================

    @GetMapping("/")
    public String home() {
        return "login";
    }

    // =========================
    // SIGNUP
    // =========================

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    @PostMapping("/signup")
    public String register(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password) {

        boolean registered =
                userService.registerUser(name, email, password);

        if (registered) {
            return "redirect:/";
        }

        return "redirect:/signup?error=exists";
    }

    // =========================
    // LOGIN
    // =========================

    @PostMapping("/login")
    public String login(
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session) {

        User user =
                userRepository.findByEmail(email).orElse(null);

        if (user != null &&
                passwordEncoder.matches(
                        password,
                        user.getPassword())) {

            session.setAttribute(
                    "userName",
                    user.getName());

            session.setAttribute(
                    "userEmail",
                    user.getEmail());

            return "redirect:/dashboard";
        }

        return "redirect:/?error=invalid";
    }

    // =========================
    // DASHBOARD
    // =========================

    @GetMapping("/dashboard")
    public String dashboard(
            HttpSession session,
            Model model) {

        if (session.getAttribute("userName") == null) {
            return "redirect:/";
        }

        model.addAttribute(
                "resources",
                resourceRepository.findAll());

        return "dashboard";
    }

    // =========================
    // CATEGORY RESOURCE LIST
    // =========================

    @GetMapping("/category/{category}")
    public String category(
            @PathVariable String category,
            HttpSession session,
            Model model) {

        if (session.getAttribute("userName") == null) {
            return "redirect:/";
        }

        List<Resource> resources =
                resourceRepository
                        .findByCategoryOrderByUploadDateDesc(category);

        model.addAttribute(
                "resources",
                resources);

        model.addAttribute(
                "category",
                category);

        return "resource-list";
    }

    // =========================
    // LOGOUT
    // =========================

    @GetMapping("/logout")
    public String logout(HttpSession session) {

        session.invalidate();

        return "redirect:/";
    }

    // =========================
    // RESOURCE DETAILS
    // =========================

    @GetMapping("/resource/{id}")
    public String resource(
            @PathVariable Long id,
            Model model) {

        Resource resource =
                resourceRepository
                        .findById(id)
                        .orElse(null);

        if (resource == null) {
            return "redirect:/dashboard";
        }

        model.addAttribute(
                "resource",
                resource);

        return "resource";
    }

    // =========================
    // TEST RESOURCE
    // =========================

    @GetMapping("/add-test-resource")
    public String addTestResource() {

        Resource resource =
                new Resource();

        resource.setTitle(
                "Data Structures Complete Notes");

        resource.setCategory(
                "Notes");

        resource.setSubject(
                "Data Structures");

        resource.setDescription(
                "Complete notes covering important concepts of Data Structures and Algorithms.");

        resource.setTags(
                "DSA, Algorithms, Notes");

        resource.setUploader(
                "StudyGram");

        resource.setFileName(
                "data-structures-notes.pdf");

        resourceRepository.save(resource);

        return "redirect:/dashboard";
    }

    // =========================
    // UPLOAD PAGE
    // =========================

    @GetMapping("/upload")
    public String uploadPage(
            HttpSession session) {

        if (session.getAttribute("userName") == null) {
            return "redirect:/";
        }

        return "upload";
    }

    // =========================
    // UPLOAD RESOURCE
    // =========================

    @PostMapping("/upload")
    public String uploadResource(
            @RequestParam String title,
            @RequestParam String category,
            @RequestParam String subject,
            @RequestParam String description,
            @RequestParam String tags,
            @RequestParam("file") MultipartFile file,
            HttpSession session) {

        if (session.getAttribute("userName") == null) {
            return "redirect:/";
        }

        if (file.isEmpty()) {
            return "redirect:/upload?error=nofile";
        }

        try {

            // Create uploads folder
            Path uploadPath =
                    Paths.get("uploads");

            Files.createDirectories(
                    uploadPath);

            // Get original file name
            String originalFileName =
                    file.getOriginalFilename();

            String extension = "";

            if (originalFileName != null &&
                    originalFileName.contains(".")) {

                extension =
                        originalFileName.substring(
                                originalFileName.lastIndexOf("."));
            }

            // Generate unique file name
            String storedFileName =
                    UUID.randomUUID() + extension;

            Path filePath =
                    uploadPath.resolve(
                            storedFileName);

            // Save physical file
            Files.copy(
                    file.getInputStream(),
                    filePath);

            // Create database resource
            Resource resource =
                    new Resource();

            resource.setTitle(title);

            resource.setCategory(category);

            resource.setSubject(subject);

            resource.setDescription(description);

            resource.setTags(tags);

            resource.setUploader(
                    (String) session.getAttribute(
                            "userName"));

            resource.setFileName(
                    storedFileName);

            resourceRepository.save(
                    resource);

            return "redirect:/dashboard";

        } catch (IOException e) {

            e.printStackTrace();

            return "redirect:/upload?error=upload";
        }
    }
}