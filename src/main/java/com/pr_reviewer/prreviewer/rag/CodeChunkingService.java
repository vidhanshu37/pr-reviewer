package com.pr_reviewer.prreviewer.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CodeChunkingService {

    private static final int MAX_CHUNK_LINES = 60;
    private static final int MIN_CHUNK_LINES = 5;

    public List<Document> chunkFile(String filename, String content) {
        String[] lines = content.split("\n");
        List<Document> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();

        for (String line : lines) {
            current.add(line);
            boolean looksLikeBoundary = line.isBlank() && current.size() >= MIN_CHUNK_LINES;
            boolean tooLarge = current.size() >= MAX_CHUNK_LINES;

            if (looksLikeBoundary || tooLarge) {
                chunks.add(toDocument(filename, current));
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            chunks.add(toDocument(filename, current));
        }

        log.info("Chunked {} into {} Document chunks", filename, chunks.size());
        return chunks;
    }

    private Document toDocument(String filename, List<String> lines) {
        String text = String.join("\n", lines);
        return new Document(text, Map.of("filename", filename));
    }
}