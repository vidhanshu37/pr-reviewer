package com.pr_reviewer.prreviewer.rag;

import com.pr_reviewer.prreviewer.dto.FileDiff;
import com.pr_reviewer.prreviewer.github.GitHubClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepoIndexingService {
    private final GitHubClientService gitHubClientService;
    private final CodeChunkingService codeChunkingService;
    private final CodebaseIndexingService codebaseIndexingService;

    @Async("indexingTaskExecutor")
    public void indexFileAsync(String owner, String repo, String repoFullName, String headSha, long installationId, FileDiff file) {
        try {
            String content = gitHubClientService.fetchFileContent(owner, repo, file.getFilename(), headSha, installationId);
            List<Document> chunks = codeChunkingService.chunkFile(repoFullName, file.getFilename(), content);

            codebaseIndexingService.indexChunks(repoFullName, file.getFilename(), chunks);
        } catch (Exception e) {

        }
    }

}
