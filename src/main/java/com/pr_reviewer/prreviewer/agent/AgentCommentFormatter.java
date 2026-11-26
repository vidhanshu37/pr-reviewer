package com.pr_reviewer.prreviewer.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class AgentCommentFormatter {

    static final int MAX_FINDINGS = 25;
    static final int MAX_BODY_CHARS = 60_000;

    private AgentCommentFormatter() {
    }

    public static boolean isPostable(AgentReviewResponse r) {
        return r != null && (r.isDone() || r.isPartial());
    }

    public static String format(AgentReviewResponse r) {
        if (!isPostable(r)) {
            throw new IllegalArgumentException("only done/partial results can be formatted");
        }
        List<AgentReviewResponse.Finding> findings = new ArrayList<>(r.findings() == null ? List.of() : r.findings());
        findings.sort(Comparator.comparingInt((AgentReviewResponse.Finding f) -> rank(f.severity())));

        StringBuilder sb = new StringBuilder("## 🤖 AI Code Review\n\n");

        if (r.isPartial()) {
            sb.append("> ⚠️ **Incomplete review.** Some checks failed, so issues may be missing.\n");
            for (String w : r.warnings() == null ? List.<String>of() : r.warnings()) {
                sb.append("> - ").append(clean(w)).append('\n');
            }
            sb.append('\n');
        }

        if (findings.isEmpty()) {
            sb.append(r.isDone()
                    ? "No issues found in the reviewed changes. ✅\n"
                    : "No issues found in the parts that could be reviewed.\n");
        } else {
            sb.append("Found **").append(findings.size()).append("** issue(s).\n");
            String currentGroup = null;
            int shown = 0;
            for (AgentReviewResponse.Finding f : findings) {
                if (shown++ == MAX_FINDINGS) {
                    sb.append("\n_…and ").append(findings.size() - MAX_FINDINGS).append(" more not shown._\n");
                    break;
                }
                String group = groupTitle(f.severity());
                if (!group.equals(currentGroup)) {
                    sb.append("\n### ").append(group).append("\n\n");
                    currentGroup = group;
                }
                appendFinding(sb, f);
            }
        }

        appendFooter(sb, r);
        return truncate(sb.toString());
    }

    private static void appendFinding(StringBuilder sb, AgentReviewResponse.Finding f) {
        sb.append("- **").append(clean(orDash(f.category()))).append("** · `")
                .append(clean(orDash(f.file())));
        if (f.line() != null) {
            sb.append(':').append(f.line());
        }
        sb.append('`').append(badge(f.verdict())).append('\n');
        sb.append("  ").append(clean(orDash(f.message())).replace("\n", "\n  ")).append('\n');
        if (f.suggestion() != null && !f.suggestion().isBlank()) {
            sb.append("  > **Suggestion:** ").append(clean(f.suggestion()).replace("\n", "\n  > ")).append('\n');
        }
        sb.append('\n');
    }

    private static void appendFooter(StringBuilder sb, AgentReviewResponse r) {
        StringBuilder parts = new StringBuilder();
        Map<String, Object> stats = r.stats() == null ? Map.of() : r.stats();
        Object totals = stats.get("totals");
        if (totals instanceof Map<?, ?> t) {
            Object calls = t.get("calls");
            Object wall = t.get("wall_s");
            if (calls != null) {
                parts.append(calls).append(" LLM call(s)");
            }
            if (wall instanceof Number n) {
                parts.append(parts.length() > 0 ? ", " : "").append(Math.round(n.doubleValue())).append("s");
            }
        }
        Object critic = stats.get("critic");
        if (critic instanceof Map<?, ?> c && c.get("rejected") instanceof List<?> rej && !rej.isEmpty()) {
            parts.append(parts.length() > 0 ? ", " : "").append(rej.size())
                    .append(" finding(s) removed by the critic");
        }
        sb.append("\n---\n<sub>Multi-agent review (security, bugs, conventions + critic)");
        if (parts.length() > 0) {
            sb.append(" · ").append(parts);
        }
        sb.append("</sub>\n");
    }

    private static String badge(String verdict) {
        if ("confirmed".equals(verdict)) return " ✅ verified";
        if ("uncertain".equals(verdict)) return " ❓ unverified";
        return "";
    }

    private static int rank(String severity) {
        if ("high".equalsIgnoreCase(severity)) return 0;
        if ("medium".equalsIgnoreCase(severity)) return 1;
        if ("low".equalsIgnoreCase(severity)) return 2;
        return 3;
    }

    private static String groupTitle(String severity) {
        return switch (rank(severity)) {
            case 0 -> "🔴 High";
            case 1 -> "🟠 Medium";
            case 2 -> "🟡 Low";
            default -> "Other";
        };
    }

    /** LLM text goes to GitHub: stop "@user" from pinging real people. */
    private static String clean(String text) {
        return text == null ? "" : text.replace("@", "@\u200B");
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private static String truncate(String body) {
        if (body.length() <= MAX_BODY_CHARS) return body;
        return body.substring(0, MAX_BODY_CHARS) + "\n\n_…comment truncated._\n";
    }
}