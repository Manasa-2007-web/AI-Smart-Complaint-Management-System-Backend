package com.complaint.management.controller;

import com.complaint.management.dto.ComplaintSimilarityResponse;
import com.complaint.management.entity.ComplaintStatus;
import com.complaint.management.service.AppUserDetailsService;
import com.complaint.management.service.ComplaintService;
import com.complaint.management.service.CommunityService;
import com.complaint.management.dto.ResolutionSuggestionsResponse;
import com.complaint.management.dto.CommunityComplaintResponse;
import com.complaint.management.controller.StaffController;
import com.complaint.management.controller.CommunityController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebMvcTest({ComplaintController.class, StaffController.class, CommunityController.class})
@Import(com.complaint.management.config.SecurityConfig.class)
class ComplaintSimilaritySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ComplaintService complaintService;

    @MockBean
    private CommunityService communityService;

    @MockBean
    private AppUserDetailsService userDetailsService;

    @Test
    void assignedStaffCanCallSimilarityEndpointAndReceiveExpectedResponse() throws Exception {
        when(complaintService.getSimilarComplaints(42L, "staff@example.test"))
                .thenReturn(List.of(new ComplaintSimilarityResponse(
                        17L,
                        "Water leak",
                        "Water is leaking into the road",
                        "INFRASTRUCTURE",
                        "HIGH",
                        ComplaintStatus.IN_PROGRESS,
                        86.5
                )));

        mockMvc.perform(get("/api/complaints/42/similar").session(staffSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(17))
                .andExpect(jsonPath("$[0].title").value("Water leak"))
                .andExpect(jsonPath("$[0].similarityPercentage").value(86.5));

        verify(complaintService).getSimilarComplaints(42L, "staff@example.test");
    }

    @Test
    void similarityMatcherDoesNotGrantStaffAccessToUserComplaintRoutes() throws Exception {
        mockMvc.perform(get("/api/complaints/42").session(staffSession()))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffResolutionSuggestionsRouteReturnsServiceResponse() throws Exception {
        when(complaintService.getResolutionSuggestions(42L, "staff@example.test"))
                .thenReturn(new ResolutionSuggestionsResponse(
                        List.of(),
                        List.of("Check the affected service and verify the outcome.")
                ));

        mockMvc.perform(get("/api/staff/complaints/42/resolution-suggestions").session(staffSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pastResolutions").isArray())
                .andExpect(jsonPath("$.guidance[0]").value("Check the affected service and verify the outcome."));

        verify(complaintService).getResolutionSuggestions(42L, "staff@example.test");
    }

    @Test
    void communityDetailReturnsOnlyTheCommunityDtoShape() throws Exception {
        when(communityService.getComplaint(42L, "user@example.test"))
                .thenReturn(new CommunityComplaintResponse(
                        42L, "Road repair", "A road is damaged", "North Campus",
                        "FACILITY", ComplaintStatus.SUBMITTED, "MEDIUM", null, null,
                        3L, true, 0L, List.of()
                ));

        mockMvc.perform(get("/api/community/complaints/42").session(userSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Road repair"))
                .andExpect(jsonPath("$.area").value("North Campus"))
                .andExpect(jsonPath("$.supportCount").value(3))
                .andExpect(jsonPath("$.statusHistory").doesNotExist())
                .andExpect(jsonPath("$.location").doesNotExist())
                .andExpect(jsonPath("$.userEmail").doesNotExist());

        verify(communityService).getComplaint(42L, "user@example.test");
    }

    @Test
    void communityCommentsRejectBlankContent() throws Exception {
        mockMvc.perform(post("/api/community/complaints/42/comments")
                        .session(userSession())
                        .contentType("application/json")
                        .content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(communityService);
    }

    @Test
    void communityEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/community/complaints"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void communityComplaintListSupportsSortAndAllowsAuthenticatedUser() throws Exception {
        when(communityService.listComplaints("user@example.test", null, null, null, null, "newest"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/community/complaints")
                        .param("sort", "newest")
                        .session(userSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(communityService).listComplaints("user@example.test", null, null, null, null, "newest");
    }

    @Test
    void communityComplaintListAllowsAuthenticatedStaffAndAdmin() throws Exception {
        when(communityService.listComplaints("staff@example.test", null, null, null, null, "newest"))
                .thenReturn(List.of());
        when(communityService.listComplaints("admin@example.test", null, null, null, null, "newest"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/community/complaints")
                        .param("sort", "newest")
                        .session(staffSession()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/community/complaints")
                        .param("sort", "newest")
                        .session(adminSession()))
                .andExpect(status().isOk());
    }

    @Test
    void adminCannotCallStaffOnlyEndpoint() throws Exception {
        mockMvc.perform(get("/api/staff/complaints").session(adminSession()))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession staffSession() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "staff@example.test",
                "not-used",
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"))
        ));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        return session;
    }

    private MockHttpSession userSession() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "user@example.test",
                "not-used",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        return session;
    }

    private MockHttpSession adminSession() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@example.test",
                "not-used",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        return session;
    }
}
