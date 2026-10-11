package com.complaint.management.config;

import com.complaint.management.entity.Department;
import com.complaint.management.entity.Role;
import com.complaint.management.entity.User;
import com.complaint.management.repository.DepartmentRepository;
import com.complaint.management.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
public class DevelopmentSeedData {

    @Bean
    public ApplicationRunner seedDevelopmentData(
            JdbcTemplate jdbcTemplate,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.enabled:true}") boolean seedEnabled,
            @Value("${app.seed.admin-email:admin@complaint.local}") String adminEmail,
            @Value("${app.seed.admin-password:Admin123!}") String adminPassword,
            @Value("${app.seed.staff-email:staff@complaint.local}") String staffEmail,
            @Value("${app.seed.staff-password:Staff123!}") String staffPassword
    ) {
        return args -> {
            jdbcTemplate.execute("""
                    ALTER TABLE complaints
                    MODIFY COLUMN category VARCHAR(255) NULL,
                    MODIFY COLUMN priority VARCHAR(255) NULL
                    """);
            jdbcTemplate.update("""
                    UPDATE complaints
                    SET status = CASE
                        WHEN status IN ('OPEN', 'NEW') THEN 'SUBMITTED'
                        WHEN status = 'IN PROGRESS' THEN 'IN_PROGRESS'
                        ELSE status
                    END,
                    created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
                    updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP)
                    WHERE status IN ('OPEN', 'NEW', 'IN PROGRESS')
                       OR created_at IS NULL
                       OR updated_at IS NULL
                    """);

            if (!seedEnabled) {
                return;
            }
            if (!userRepository.existsByEmailIgnoreCase(adminEmail)) {
                userRepository.save(new User("Development Admin", adminEmail,
                        passwordEncoder.encode(adminPassword), Role.ADMIN));
            }
            if (!userRepository.existsByEmailIgnoreCase(staffEmail)) {
                userRepository.save(new User("Development Staff", staffEmail,
                        passwordEncoder.encode(staffPassword), Role.STAFF));
            }
            if (!userRepository.existsByEmailIgnoreCase("staff2@complaint.local")) {
                userRepository.save(new User("Development Staff 2", "staff2@complaint.local",
                        passwordEncoder.encode(staffPassword), Role.STAFF));
            }
            if (!userRepository.existsByEmailIgnoreCase("staff3@complaint.local")) {
                userRepository.save(new User("Development Staff 3", "staff3@complaint.local",
                        passwordEncoder.encode(staffPassword), Role.STAFF));
            }
            if (!userRepository.existsByEmailIgnoreCase("staff4@complaint.local")) {
                userRepository.save(new User("Development Staff 4", "staff4@complaint.local",
                        passwordEncoder.encode(staffPassword), Role.STAFF));
            }

            List<Department> departments = List.of(
                    new Department("Water Supply", "Water and drainage services"),
                    new Department("Electricity", "Power and street lighting"),
                    new Department("Roads and Transport", "Road maintenance and transport"),
                    new Department("Sanitation", "Waste collection and public sanitation")
            );
            for (Department department : departments) {
                if (departmentRepository.findByNameIgnoreCase(department.getName()).isEmpty()) {
                    departmentRepository.save(department);
                }
            }
        };
    }
}
