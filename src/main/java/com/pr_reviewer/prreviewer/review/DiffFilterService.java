package com.pr_reviewer.prreviewer.review;

import com.pr_reviewer.prreviewer.dto.FileDiff;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DiffFilterService {

    private static final Set<String> IGNORED_FILENAME = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", ".gitignore"
    );

    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".lock", ".svg", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".min.js", ".min.css"
    );

    public List<FileDiff> filterRelevantFiles(List<FileDiff> files) {
        List<FileDiff> relevant = files.stream()
                .filter(this::isRelevant)
                .collect(Collectors.toList());

        List<String> excludedNames = files.stream()
                .filter(f -> !isRelevant(f))
                .map(FileDiff::getFilename)
                .collect(Collectors.toList());

        log.info("Diff filter: {} total files, {} relevant, {} excluded -> excluded files: {}",
                files.size(), relevant.size(), excludedNames.size(), excludedNames);

        return relevant;
    }

    private boolean isRelevant(FileDiff file) {
        String filename = file.getFilename().toLowerCase();

        if(IGNORED_FILENAME.contains(filename)) {
            return false;
        }

        if(IGNORED_EXTENSIONS.stream().anyMatch(filename::endsWith)) {
            return false;
        }

        if(file.getPatch() == null) {
            return false;
        }

        return true;
    }
}
