package com.orbitcrm.identity.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.identity.dto.UserSummary;
import com.orbitcrm.identity.entity.User;
import com.orbitcrm.identity.repository.UserRepository;
import com.orbitcrm.identity.service.UserDirectory;

/**
 * Implementation of {@link com.orbitcrm.identity.service.UserDirectory} backed by the user repository.
 *
 * <p>Purpose: answer other modules' user questions within the current tenant. Email lookups are case-insensitive;
 * lookups by full name succeed only when exactly one active user matches, so an ambiguous name never assigns a
 * record to the wrong person.
 *
 * @author Abhay
 */
@Component
public class UserDirectoryImpl implements UserDirectory {

    private final UserRepository users;

    UserDirectoryImpl(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> displayNames(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllByIdIn(userIds).stream().collect(Collectors.toMap(User::getId, User::displayName));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> find(UUID userId) {
        return users.findById(userId).map(UserDirectoryImpl::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserSummary> summaries(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllByIdIn(userIds).stream().collect(Collectors.toMap(User::getId, UserDirectoryImpl::summary));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(UUID userId) {
        return userId != null && users.findById(userId).map(User::isActive).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findActiveId(String emailOrName) {
        if (emailOrName == null || emailOrName.isBlank()) {
            return Optional.empty();
        }
        String value = emailOrName.trim();
        if (value.contains("@")) {
            return users.findByEmail(User.normalizeEmail(value)).filter(User::isActive).map(User::getId);
        }
        List<User> matches = users.findByStatus(User.Status.ACTIVE,
                org.springframework.data.domain.Pageable.unpaged()).stream()
                .filter(u -> u.displayName().equalsIgnoreCase(value))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0).getId()) : Optional.empty();
    }

    private static UserSummary summary(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.displayName(), user.getStatus().name(),
                user.getTeamId());
    }
}
