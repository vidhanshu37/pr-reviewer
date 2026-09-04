package com.pr_reviewer.prreviewer.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodebaseIndexingService {
    private final VectorStore vectorStore;

    public void indexChunks(String repoFullName, String filename, List<Document> chunks) {
        vectorStore.delete("repo == '" + repoFullName + "' && filename == '" + filename + "'");
        if (!chunks.isEmpty()) {
            vectorStore.add(chunks);
        }
        log.info("Re-indexed {} - removed old chunks, added {} new chunks", filename, chunks.size());
    }
}
