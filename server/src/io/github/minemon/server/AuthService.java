package io.github.minemon.server;

import io.github.minemon.server.model.User;
import io.github.minemon.server.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean authenticate(String username, String password) {
        User user = userRepository.findByUsername(username);
        return user != null && BCrypt.checkpw(password, user.getPasswordHash());
    }

    public boolean createUser(String username, String rawPassword) {
        if (userRepository.findByUsername(username) != null) {
            return false;
        }
        String hashed = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
        User newUser = new User(username, hashed);
        userRepository.save(newUser);
        return true;
    }
}
