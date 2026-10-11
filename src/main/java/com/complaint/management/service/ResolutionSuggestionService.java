package com.complaint.management.service;

import com.complaint.management.dto.ResolutionExampleResponse;
import com.complaint.management.dto.ResolutionSuggestionsResponse;
import com.complaint.management.entity.Complaint;
import com.complaint.management.entity.ComplaintStatus;
import com.complaint.management.entity.ComplaintStatusHistory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ResolutionSuggestionService {

    private static final Pattern WORD_SEPARATOR = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> COMMON_WORDS = Set.of(
            "about", "after", "again", "also", "and", "are", "been", "being", "but", "can",
            "could", "from", "have", "into", "issue", "its", "our", "please", "that", "the",
            "their", "there", "this", "was", "were", "with", "would"
    );

    public ResolutionSuggestionsResponse suggest(Complaint target, List<Complaint> authorizedComplaints) {
        Set<String> targetTerms = terms(target);
        List<ResolutionExampleResponse> examples = authorizedComplaints.stream()
                .filter(candidate -> !candidate.getId().equals(target.getId()))
                .map(candidate -> toExample(target, candidate, targetTerms))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator
                        .comparingInt((ResolutionExampleResponse example) -> categoryMatch(target, example) ? 0 : 1)
                        .thenComparing(Comparator.comparingInt(
                                (ResolutionExampleResponse example) -> overlap(targetTerms, terms(example.title(), example.resolution()))
                        ).reversed())
                        .thenComparing(ResolutionExampleResponse::complaintId))
                .limit(3)
                .toList();

        List<String> guidance = examples.isEmpty() ? categoryGuidance(target) : List.of();
        return new ResolutionSuggestionsResponse(examples, guidance);
    }

    private java.util.Optional<ResolutionExampleResponse> toExample(
            Complaint target,
            Complaint complaint,
            Set<String> targetTerms
    ) {
        if (complaint.getStatus() != ComplaintStatus.RESOLVED && complaint.getStatus() != ComplaintStatus.CLOSED) {
            return java.util.Optional.empty();
        }

        String resolution = complaint.getStatusHistory().stream()
                .filter(history -> history.getStatus() == ComplaintStatus.RESOLVED)
                .filter(history -> history.getRemarks() != null && !history.getRemarks().isBlank())
                .max(Comparator.comparing(ComplaintStatusHistory::getUpdatedAt))
                .map(ComplaintStatusHistory::getRemarks)
                .orElse(null);
        if (resolution == null) return java.util.Optional.empty();

        boolean sameCategory = target.getCategory() != null
                && target.getCategory().equalsIgnoreCase(complaint.getCategory());
        boolean sharedTerms = overlap(targetTerms, terms(complaint.getTitle(), complaint.getDescription(), resolution)) > 0;
        if (!sameCategory && !sharedTerms) return java.util.Optional.empty();

        return java.util.Optional.of(new ResolutionExampleResponse(
                complaint.getId(),
                complaint.getTitle(),
                complaint.getCategory(),
                resolution
        ));
    }

    private boolean categoryMatch(Complaint target, ResolutionExampleResponse example) {
        return target.getCategory() != null
                && target.getCategory().equalsIgnoreCase(example.category());
    }

    private Set<String> terms(Complaint complaint) {
        return terms(complaint.getTitle(), complaint.getDescription(), complaint.getCategory());
    }

    private Set<String> terms(String... values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (value == null) continue;
            for (String term : WORD_SEPARATOR.split(value.toLowerCase(Locale.ROOT))) {
                if (term.length() > 2 && !COMMON_WORDS.contains(term)) result.add(term);
            }
        }
        return result;
    }

    private int overlap(Set<String> first, Set<String> second) {
        Set<String> common = new HashSet<>(first);
        common.retainAll(second);
        return common.size();
    }

    private List<String> categoryGuidance(Complaint complaint) {
        String text = String.join(" ",
                complaint.getTitle() == null ? "" : complaint.getTitle(),
                complaint.getDescription() == null ? "" : complaint.getDescription()
        ).toLowerCase(Locale.ROOT);
        List<String> guidance = new ArrayList<>();
        switch (complaint.getCategory() == null ? "" : complaint.getCategory().toUpperCase(Locale.ROOT)) {
            case "TECHNICAL" -> {
                guidance.add("Confirm the affected device, application, or service and reproduce the issue if safe.");
                guidance.add("Check recent changes, error messages, connectivity, and relevant service logs.");
                guidance.add("Apply an approved fix in a controlled way, then verify the affected function with the reporter.");
            }
            case "BILLING" -> {
                guidance.add("Compare the reported charge with the account, invoice, and applicable billing period.");
                guidance.add("Check for duplicate transactions, adjustments, or payment-processing delays.");
                guidance.add("Explain any correction clearly and verify the updated balance with the reporter.");
            }
            case "SERVICE" -> {
                guidance.add("Confirm the affected service, time, location, and the impact reported.");
                guidance.add("Check service records and coordinate with the responsible service team.");
                guidance.add("Verify restoration with the reporter and document any remaining impact.");
            }
            case "SECURITY" -> {
                guidance.add("Follow the organization's incident reporting and evidence-handling procedures.");
                guidance.add("Limit unnecessary access to sensitive information and escalate suspected active risks.");
                guidance.add("Verify the approved containment or corrective action without exposing credentials or private data.");
            }
            case "FACILITY" -> {
                guidance.add("Inspect the reported area and verify the issue and any immediate safety concern.");
                guidance.add("Identify the responsible maintenance team and check relevant inspection or repair records.");
                guidance.add("Confirm the repair and that the area is safe for use before closing the investigation.");
            }
            case "ACADEMIC" -> {
                guidance.add("Confirm the relevant course, assessment, policy, and dates with the appropriate office.");
                guidance.add("Review applicable records and route the matter to the responsible academic contact.");
                guidance.add("Explain the reviewed outcome and next steps to the reporter.");
            }
            default -> {
                guidance.add("Clarify the reported impact and gather only the information needed to investigate.");
                guidance.add("Check relevant records and consult the team responsible for this type of issue.");
                guidance.add("Agree on a safe corrective action and verify the outcome with the reporter.");
            }
        }
        if (text.contains("leak") || text.contains("flood")) {
            guidance.add("If water is actively entering an occupied or electrical area, follow local safety procedures and escalate promptly.");
        }
        return List.copyOf(guidance);
    }
}
