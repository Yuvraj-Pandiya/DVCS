package com.dvcs.auth.service;

import com.dvcs.auth.domain.User;
import com.dvcs.auth.dto.UserProfileDto;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.common.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user-related operations outside of authentication.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Retrieves a user profile by username.
     *
     * @param username the username to look up
     * @return the user profile DTO
     * @throws EntityNotFoundException if the user does not exist
     */
    public UserProfileDto getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));

        return new UserProfileDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getCreatedAt()
        );
    }
}
