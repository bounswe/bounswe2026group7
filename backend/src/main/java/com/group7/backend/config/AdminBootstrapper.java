package com.group7.backend.config;

import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

public class AdminBootstrapper implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapper.class);

    private final AdminBootstrapProperties props;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapper(AdminBootstrapProperties props,
                             UserRepository userRepository,
                             PasswordEncoder passwordEncoder) {
        this.props = props;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        // No outer @Transactional: Spring Data JPA repository methods each
        // run in their own transaction, so a UNIQUE-constraint violation on
        // save() throws DataIntegrityViolationException cleanly without the
        // commit-time UnexpectedRollbackException that would arise if save()
        // ran inside an outer @Transactional. This makes the bootstrapper
        // tolerant of multi-replica startup races: the losing replica catches
        // the violation, logs, and starts cleanly.
        try {
            userRepository.findByEmail(props.getEmail()).ifPresentOrElse(
                    existing -> {
                        if (existing instanceof Admin) {
                            log.info("Admin bootstrap: admin already exists, skipping (userId={})", existing.getId());
                        } else {
                            log.warn("Admin bootstrap: email {} is already used by a non-admin user "
                                            + "(type={}, userId={}); admin NOT created. Resolve by changing "
                                            + "app.admin-bootstrap.email or removing the conflicting account.",
                                    props.getEmail(),
                                    userTypeName(existing),
                                    existing.getId());
                        }
                    },
                    this::createAdmin
            );
        } catch (DataIntegrityViolationException e) {
            log.info("Admin bootstrap: lost cluster-startup race (UNIQUE constraint hit); "
                    + "another replica created the admin first. Continuing without retry.");
        }
    }

    /**
     * Returns a stable, proxy-safe type name for logs. {@code getClass()} on an
     * entity loaded via Hibernate may return a generated proxy class (e.g.
     * {@code Mentor$HibernateProxy$abc}); {@code instanceof} unwraps that.
     */
    private static String userTypeName(User user) {
        if (user instanceof Admin) return "Admin";
        if (user instanceof Mentor) return "Mentor";
        if (user instanceof Mentee) return "Mentee";
        return "Unknown";
    }

    private void createAdmin() {
        Admin admin = new Admin();
        admin.setEmail(props.getEmail());
        admin.setFirstName(props.getFirstName());
        admin.setLastName(props.getLastName());
        admin.setPasswordHash(passwordEncoder.encode(props.getPassword()));
        admin.setIsEmailVerified(true);
        Admin saved = userRepository.save(admin);
        log.info("Admin bootstrap: created admin userId={}", saved.getId());
    }
}
