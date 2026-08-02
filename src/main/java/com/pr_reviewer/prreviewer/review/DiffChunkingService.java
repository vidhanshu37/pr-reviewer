package com.pr_reviewer.prreviewer.review;

import com.pr_reviewer.prreviewer.dto.FileDiff;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class DiffChunkingService {
    public String buildDiffText(List<FileDiff> files) {
        StringBuilder sb = new StringBuilder();

        for(FileDiff file : files) {
            sb.append("### File : ").append(file.getFilename())
                    .append(" (").append(file.getStatus()).append(")\n");

            sb.append(file.getPatch()).append("\n\n");
        }

        String res = sb.toString();
        log.info("Built diff text for {} files, total length: {}", files.size(), res);

        return res;
    }
}
