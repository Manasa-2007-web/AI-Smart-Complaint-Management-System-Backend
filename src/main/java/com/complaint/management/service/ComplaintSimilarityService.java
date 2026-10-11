package com.complaint.management.service;

import com.complaint.management.dto.ComplaintSimilarityResponse;
import com.complaint.management.entity.Complaint;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ComplaintSimilarityService {

    private static final double DEFAULT_THRESHOLD = 0.60;
    private static final int DEFAULT_LIMIT = 5;

    public List<ComplaintSimilarityResponse> findSimilarComplaints(Complaint target, List<Complaint> visibleComplaints) {
        return findSimilarComplaints(target, visibleComplaints, DEFAULT_THRESHOLD, DEFAULT_LIMIT);
    }

    private List<ComplaintSimilarityResponse> findSimilarComplaints(
            Complaint target,
            List<Complaint> visibleComplaints,
            double threshold,
            int limit
    ) {
        List<Complaint> candidates = visibleComplaints.stream()
                .filter(complaint -> !complaint.getId().equals(target.getId()))
                .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        String targetText = buildText(target);
        List<String> candidateTexts = candidates.stream().map(this::buildText).toList();
        List<String> allTexts = new ArrayList<>();
        allTexts.add(targetText);
        allTexts.addAll(candidateTexts);

        Set<String> vocabulary = new HashSet<>();
        for (String text : allTexts) {
            vocabulary.addAll(tokenize(text));
        }

        if (vocabulary.isEmpty()) {
            return List.of();
        }

        List<String> vocabList = new ArrayList<>(vocabulary);
        Map<String, Integer> indexByTerm = new HashMap<>();
        for (int i = 0; i < vocabList.size(); i++) {
            indexByTerm.put(vocabList.get(i), i);
        }

        double[] idf = new double[vocabList.size()];
        for (int i = 0; i < vocabList.size(); i++) {
            String term = vocabList.get(i);
            int documentFrequency = 0;
            for (String text : allTexts) {
                if (tokenize(text).contains(term)) {
                    documentFrequency++;
                }
            }
            idf[i] = Math.log((allTexts.size() + 1.0) / (documentFrequency + 1.0)) + 1.0;
        }

        double[] targetVector = tfIdfVector(targetText, indexByTerm, idf);

        List<ComplaintSimilarityResult> similarityResults = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Complaint complaint = candidates.get(i);
            double[] candidateVector = tfIdfVector(candidateTexts.get(i), indexByTerm, idf);
            double similarity = cosineSimilarity(targetVector, candidateVector);

            if (similarity >= threshold) {
                similarityResults.add(new ComplaintSimilarityResult(
                        complaint,
                        similarity
                ));
            }
        }

        return similarityResults.stream()
                .sorted(Comparator.comparingDouble(ComplaintSimilarityResult::similarity).reversed())
                .limit(limit)
                .map(result -> new ComplaintSimilarityResponse(
                        result.complaint().getId(),
                        result.complaint().getTitle(),
                        result.complaint().getDescription(),
                        result.complaint().getCategory(),
                        result.complaint().getPriority(),
                        result.complaint().getStatus(),
                        roundToTwoDecimals(result.similarity() * 100.0)
                ))
                .toList();
    }

    private String buildText(Complaint complaint) {
        String title = complaint.getTitle() == null ? "" : complaint.getTitle();
        String description = complaint.getDescription() == null ? "" : complaint.getDescription();
        return (title + " " + description).trim();
    }

    private double[] tfIdfVector(String text, Map<String, Integer> indexByTerm, double[] idf) {
        List<String> tokens = tokenize(text);
        Map<String, Integer> termCounts = new HashMap<>();
        for (String token : tokens) {
            termCounts.merge(token, 1, Integer::sum);
        }

        double[] vector = new double[indexByTerm.size()];
        for (Map.Entry<String, Integer> entry : termCounts.entrySet()) {
            Integer index = indexByTerm.get(entry.getKey());
            if (index == null) {
                continue;
            }
            double termFrequency = (double) entry.getValue() / Math.max(tokens.size(), 1);
            vector[index] = termFrequency * idf[index];
        }
        return vector;
    }

    private double cosineSimilarity(double[] left, double[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("Vectors must be the same length");
        }

        double dotProduct = 0.0;
        double leftMagnitude = 0.0;
        double rightMagnitude = 0.0;

        for (int i = 0; i < left.length; i++) {
            dotProduct += left[i] * right[i];
            leftMagnitude += left[i] * left[i];
            rightMagnitude += right[i] * right[i];
        }

        if (leftMagnitude == 0.0 || rightMagnitude == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        return Arrays.stream(text.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9\\s]", " ")
                        .split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .toList();
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record ComplaintSimilarityResult(Complaint complaint, double similarity) {
    }
}
