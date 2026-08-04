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

    public void indexChunks(List<Document> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        vectorStore.add(chunks);
        log.info("Indexed {} chunks into vector store", chunks.size());
    }
}
