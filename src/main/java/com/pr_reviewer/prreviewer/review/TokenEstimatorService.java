package com.pr_reviewer.prreviewer.review;

import org.springframework.stereotype.Service;

@Service
public class TokenEstimatorService {
    private static final int CHARS_PER_TOKEN = 4;

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / (double) CHARS_PER_TOKEN);
    }
}
