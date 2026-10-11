package com.complaint.management.service;

import com.complaint.management.dto.DashboardStatistics;
import com.complaint.management.dto.UserResponse;
import com.complaint.management.entity.ComplaintStatus;
import com.complaint.management.entity.Role;
import com.complaint.management.repository.ComplaintRepository;
import com.complaint.management.repository.DepartmentRepository;
import com.complaint.management.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    public AdminService(
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            DepartmentRepository departmentRepository
    ) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers() {
        return userRepository.findAll().stream()
                .map(user -> new UserResponse(
                        user.getId(), user.getFullName(), user.getEmail(), user.getRole().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardStatistics getStatistics() {
        return new DashboardStatistics(
                complaintRepository.count(),
                complaintRepository.countByStatus(ComplaintStatus.SUBMITTED),
                complaintRepository.countByStatus(ComplaintStatus.ASSIGNED),
                complaintRepository.countByStatus(ComplaintStatus.IN_PROGRESS),
                complaintRepository.countByStatus(ComplaintStatus.RESOLVED),
                userRepository.countByRole(Role.USER),
                userRepository.countByRole(Role.STAFF),
                departmentRepository.count()
        );
    }
}
