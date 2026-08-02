package com.pr_reviewer.prreviewer.dto;

import lombok.Data;

@Data
public class FileDiff {
    private String filename;
    private String status;   // added, modified, removed, renamed
    private String patch;    // the actual diff text for this file (can be null for binary/large files)
    private int additions;
    private int deletions;
}
