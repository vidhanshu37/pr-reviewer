package com.pr_reviewer.prreviewer.review;

import com.pr_reviewer.prreviewer.dto.FileDiff;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiffCompressionService {

    private static final int DIFF_TOKEN_BUDGET = 4500;
    private final TokenEstimatorService tokenEstimatorService;

    @Getter
    public static class SplitResult {
        private final List<String> deletedFiles = new ArrayList<>();
        private final List<FileDiff> remainingFiles = new ArrayList<>();
    }

    @Getter
    public static class FitResult {
        private final List<FileDiff> includedFiles = new ArrayList<>();
        private final List<String> overflowFilenames = new ArrayList<>();
        private int totalTokensUsed = 0;
    }

    public String buildCompressedDiff(List<FileDiff> files) {
        SplitResult split = splitAndStripDeletions(files);
        List<FileDiff> sorted = sortBySizeDescending(split.getRemainingFiles());
        FitResult fit = fitToBudget(sorted);

        StringBuilder finalDiff = new StringBuilder();
        for(FileDiff file : fit.getIncludedFiles()) {
            finalDiff.append("### File: ").append(file.getFilename())
                    .append(" (").append(file.getStatus()).append(")\n");
            finalDiff.append(file.getPatch()).append("\n\n");
        }

        finalDiff.append(buildOverflowFilesSection(fit.getOverflowFilenames()));
        finalDiff.append(buildDeletedFilesSection(split.getDeletedFiles()));

        log.info("Built compressed diff: {} files included in full, {} overflowed, {} deleted, ~{} tokens total",
                fit.getIncludedFiles().size(), fit.getOverflowFilenames().size(),
                split.getDeletedFiles().size(), fit.getTotalTokensUsed());

        return finalDiff.toString();
    }


    public FitResult fitToBudget(List<FileDiff> sortedFiles) {
        FitResult res = new FitResult();
        int runningTotal = 0;

        for(FileDiff file : sortedFiles) {
            int fileTokens = tokenEstimatorService.estimateTokens(file.getPatch());

            if(runningTotal + fileTokens <= DIFF_TOKEN_BUDGET) {
                res.includedFiles.add(file);
                runningTotal += fileTokens;
            } else {
                res.overflowFilenames.add(file.getFilename());
            }
        }
        res.totalTokensUsed = runningTotal;

        log.info("Fit to budget ({} tokens): {} files included (~{} tokens used), {} files overflowed to filename-only list",
                DIFF_TOKEN_BUDGET, res.includedFiles.size(), runningTotal, res.overflowFilenames.size());

        if (!res.overflowFilenames.isEmpty()) {
            log.info("Overflow files (name only, not reviewed in detail): {}", res.overflowFilenames);
        }

        return res;
    }

    public SplitResult splitAndStripDeletions(List<FileDiff> files) {
        SplitResult result = new SplitResult();

        for (FileDiff file : files) {
            if ("removed".equals(file.getStatus())) {
                result.deletedFiles.add(file.getFilename());
                continue;
            }

            String strippedPatch = stripDeletionOnlyHunks(file.getPatch());
            file.setPatch(strippedPatch);
            result.remainingFiles.add(file);
        }

        log.info("Split diff: {} deleted files, {} remaining files (deletion-only hunks stripped)",
                result.deletedFiles.size(), result.remainingFiles.size());

        return result;
    }

    private String stripDeletionOnlyHunks(String patch) {
        if(patch == null || patch.isEmpty()) {
            return patch;
        }

        String[] lines = patch.split("\n");
        StringBuilder output = new StringBuilder();
        List<String> currentHunk = new ArrayList<>();

        boolean hunkHasAddition = false;
        for (String line : lines) {
            if (line.startsWith("@@")) {
                appendHunkIfKept(output, currentHunk, hunkHasAddition);
                currentHunk = new ArrayList<>();
                hunkHasAddition = false;
            }
            currentHunk.add(line);
            if (line.startsWith("+") && !line.startsWith("+++")) {
                hunkHasAddition = true;
            }
        }
        appendHunkIfKept(output, currentHunk, hunkHasAddition);

        return output.toString().trim();
    }

    private void appendHunkIfKept(StringBuilder output, List<String> hunkLines, boolean hasAddition) {
        if (hunkLines.isEmpty()) {
            return;
        }
        boolean isPureDeletionHunk = hunkLines.stream().anyMatch(l -> l.startsWith("-") && !l.startsWith("---"))
                && !hasAddition;
        if (isPureDeletionHunk) {
            return; // skip this hunk entirely
        }
        for (String l : hunkLines) {
            output.append(l).append("\n");
        }
    }

    public List<FileDiff> sortBySizeDescending(List<FileDiff> files) {
        List<FileDiff> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.comparingInt(
                (FileDiff f) -> tokenEstimatorService.estimateTokens(f.getPatch())
        ).reversed());

        log.info("Sorted {} files by token size (descending):", sorted.size());
        for (FileDiff f : sorted) {
            int tokens = tokenEstimatorService.estimateTokens(f.getPatch());
            log.info("  - {} (~{} tokens)", f.getFilename(), tokens);
        }

        return sorted;
    }

    public String buildDeletedFilesSection(List<String> deletedFiles) {
        if(deletedFiles.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n### DELETED FILES (").append(deletedFiles.size())
                .append(" total, content not shown for brevity — consider reviewing separately if these contained significant logic):\n");

        for (String filename : deletedFiles) {
            sb.append("- ").append(filename).append("\n");
        }
        return sb.toString();
    }

    public String buildOverflowFilesSection(List<String> overflowFilenames) {
        if (overflowFilenames.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n### OTHER MODIFIED FILES (").append(overflowFilenames.size())
                .append(" total, not shown in detail due to size — full patches omitted for brevity):\n");
        for (String filename : overflowFilenames) {
            sb.append("- ").append(filename).append("\n");
        }
        return sb.toString();
    }

}
