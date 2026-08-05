package com.pr_reviewer.prreviewer.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private static final int TOP_K = 5;
    private final VectorStore vectorStore;

    public List<Document> retrieveRelevantChunks(String diffText) {
        SearchRequest request = SearchRequest.builder()
                .query(diffText)
                .topK(TOP_K)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        log.info("Retrieved {} relevant chunks for diff (topK={})", results.size(), TOP_K);

        results.forEach(doc -> log.info("  - {} (score={}): {}",
                doc.getMetadata().get("filename"),
                doc.getScore(),
                doc.getText().substring(0, Math.min(80, doc.getText().length())).replace("\n", " ")));

        return results;
    }
}
