package com.studygram.service;

import com.studygram.model.User;
import com.studygram.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public boolean registerUser(String name, String email, String password) {

        if (userRepository.findByEmail(email).isPresent()) {
            return false;
        }

        String hashedPassword = passwordEncoder.encode(password);

        User user = new User(
                email,
                hashedPassword,
                name
        );

        userRepository.save(user);

        return true;
    }
}