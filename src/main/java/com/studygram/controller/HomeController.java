package com.studygram.controller;

import com.studygram.model.Resource;
import com.studygram.model.User;
import com.studygram.repository.ResourceRepository;
import com.studygram.repository.UserRepository;
import com.studygram.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;

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

        boolean registered = userService.registerUser(name, email, password);

        if (registered) {
            return "redirect:/";
        }

        return "redirect:/signup?error=exists";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session) {

        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null &&
                passwordEncoder.matches(password, user.getPassword())) {

            session.setAttribute("userName", user.getName());
            session.setAttribute("userEmail", user.getEmail());

            return "redirect:/dashboard";
        }

        return "redirect:/?error=invalid";
    }

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

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/resource/{id}")
    public String resource(
            @PathVariable Long id,
            Model model) {

        Resource resource = resourceRepository.findById(id).orElse(null);

        if (resource == null) {
            return "redirect:/dashboard";
        }

        model.addAttribute("resource", resource);

        return "resource";
    }

    @GetMapping("/add-test-resource")
    public String addTestResource() {

        Resource resource = new Resource();

        resource.setTitle("Data Structures Complete Notes");
        resource.setCategory("Notes");
        resource.setSubject("Data Structures");
        resource.setDescription(
                "Complete notes covering important concepts of Data Structures and Algorithms.");
        resource.setTags("DSA, Algorithms, Notes");
        resource.setUploader("StudyGram");
        resource.setFileName("data-structures-notes.pdf");

        resourceRepository.save(resource);

        return "redirect:/dashboard";
    }
}