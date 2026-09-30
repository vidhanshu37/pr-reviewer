package com.pr_reviewer.prreviewer.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private static final int TOP_K = 5;
    private final VectorStore vectorStore;

    public List<Document> retrieveRelevantChunks(String diffText, Set<String> excludeFilenames, String repoFullName) {
        StringBuilder filter = new StringBuilder("repo =='" + repoFullName + "'");

        for(String filename : excludeFilenames) {
            filter.append(" && filename != '").append(filename).append("'");
        }

        SearchRequest request = SearchRequest.builder()
                .query(diffText)
                .topK(TOP_K + excludeFilenames.size()) // to remove current PR's file
                .filterExpression(filter.toString())
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        log.info("Retrieved {} relevant chunks for diff (topK={}, repo={}, excluded {} in-PR files)",
                results.size(), TOP_K, repoFullName, excludeFilenames.size());

        results.forEach(doc -> log.info("  - [{}] {} (score={}): {}",
                doc.getMetadata().get("repo"),
                doc.getMetadata().get("filename"),
                doc.getScore(),
                doc.getText().substring(0, Math.min(80, doc.getText().length())).replace("\n", " ")));

        return results;
    }
}
