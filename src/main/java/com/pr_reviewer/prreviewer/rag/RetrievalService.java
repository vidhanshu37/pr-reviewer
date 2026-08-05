package com.pr_reviewer.prreviewer.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private static final int TOP_K = 5;
    private final VectorStore vectorStore;

    // this test comment
    public List<Document> retrieveRelevantChunks(String diffText, Set<String> excludeFilenames) {
        SearchRequest request = SearchRequest.builder()
                .query(diffText)
                .topK(TOP_K + excludeFilenames.size()) // to remove current PR's file
                .build();

        List<Document> results = vectorStore.similaritySearch(request).stream()
                .filter(doc -> !excludeFilenames.contains(doc.getMetadata().get("filename")))
                .limit(TOP_K)
                .collect(Collectors.toList());

        log.info("Retrieved {} relevant chunks for difference (topK={}, excluded {} in-PR files)",
                results.size(), TOP_K, excludeFilenames.size());
        results.forEach(doc -> log.info("  - {} (score={}): {}",
                doc.getMetadata().get("filename"), doc.getScore(),
                doc.getText().substring(0, Math.min(80, doc.getText().length())).replace("\n", " ")));

        return results;
    }
}
