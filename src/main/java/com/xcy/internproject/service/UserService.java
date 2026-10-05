package com.xcy.internproject.service;

import com.xcy.internproject.exception.ResourceNotFoundException;
import com.xcy.internproject.model.User;
import com.xcy.internproject.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(String username, String email) {
        return userRepository.save(username, email);
    }

    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
