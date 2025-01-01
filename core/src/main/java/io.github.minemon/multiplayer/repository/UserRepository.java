package io.github.minemon.multiplayer.repository;

import io.github.minemon.multiplayer.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
    User findByUsername(String username);
}
