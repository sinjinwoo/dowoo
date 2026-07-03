package io.dedyn.jwlabs.dowoo.auth.repository;

import io.dedyn.jwlabs.dowoo.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
}
