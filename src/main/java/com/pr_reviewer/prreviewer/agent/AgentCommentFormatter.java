package com.pr_reviewer.prreviewer.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AgentCommentFormatter {

    static final int MAX_FINDINGS = 25;
    static final int MAX_BODY_CHARS = 60_000; // GitHub's hard limit is 65,536

    private record Check(String node, String category, String icon, String title, String noun) {
    }

    private static final List<Check> CHECKS = List.of(
            new Check("security", "security", "🔒", "Security", "security issues"),
            new Check("bugs", "bug", "🐛", "Bugs", "bugs"),
            new Check("conventions", "convention", "📐", "Conventions", "convention problems"));

    private enum State { RAN, FAILED, SKIPPED, NOT_RUN }

    private AgentCommentFormatter() {
    }

    public static boolean isPostable(AgentReviewResponse r) {
        return r != null && (r.isDone() || r.isPartial());
    }

    public static String format(AgentReviewResponse r) {
        if (!isPostable(r)) {
            throw new IllegalArgumentException("only done/partial results can be formatted");
        }
        List<AgentReviewResponse.Finding> all = r.findings() == null ? List.of() : r.findings();
        Map<String, Object> stats = r.stats() == null ? Map.of() : r.stats();
        Map<String, Map<?, ?>> nodes = nodesByName(stats);
        boolean nodesKnown = !nodes.isEmpty();

        StringBuilder sb = new StringBuilder("## 🤖 AI Code Review\n\n");

        if (r.isPartial()) {
            sb.append("> ⚠️ **Incomplete review.** Some checks failed, so issues may be missing.\n");
            for (String w : r.warnings() == null ? List.<String>of() : r.warnings()) {
                sb.append("> - ").append(clean(w)).append('\n');
            }
            sb.append('\n');
        }

        long confident = all.stream().filter(f -> !f.isPossible()).count();
        long possible = all.size() - confident;
        appendHeadline(sb, r, all, confident, possible);

        if (nodesKnown) {
            appendTable(sb, all, nodes);
        }

        int[] shown = {0};
        for (Check check : CHECKS) {
            List<AgentReviewResponse.Finding> mine = sorted(all.stream()
                    .filter(f -> check.category().equals(f.category())).toList());
            State state = stateOf(check, nodes, nodesKnown, !mine.isEmpty());
            if (state == State.NOT_RUN || (state == State.SKIPPED && mine.isEmpty())) {
                continue;
            }
            sb.append("\n### ").append(check.icon()).append(' ').append(check.title()).append("\n\n");
            if (state == State.FAILED) {
                sb.append("⚠️ This check failed, so these changes were **not reviewed** for ")
                        .append(check.noun()).append(".\n");
            }
            List<AgentReviewResponse.Finding> sure = mine.stream().filter(f -> !f.isPossible()).toList();
            List<AgentReviewResponse.Finding> maybe = mine.stream().filter(AgentReviewResponse.Finding::isPossible).toList();
            if (mine.isEmpty() && state == State.RAN) {
                sb.append("✅ No ").append(check.noun()).append(" found ")
                        .append(coverageSentence(nodes.get(check.node()))).append('\n');
            }
            for (AgentReviewResponse.Finding f : sure) {
                appendFinding(sb, f, shown);
            }
            if (!maybe.isEmpty()) {
                sb.append("<details>\n<summary>Possible issues (lower confidence): ").append(maybe.size())
                        .append("</summary>\n\n");
                for (AgentReviewResponse.Finding f : maybe) {
                    appendFinding(sb, f, shown);
                }
                sb.append("</details>\n");
            }
        }

        appendFooter(sb, stats);
        return truncate(sb.toString());
    }

    private static void appendHeadline(StringBuilder sb, AgentReviewResponse r, List<AgentReviewResponse.Finding> all,
                                       long confident, long possible) {
        if (all.isEmpty()) {
            sb.append(r.isDone()
                    ? "No issues found in the reviewed changes. ✅\n"
                    : "No issues found in the parts that could be reviewed.\n");
            return;
        }
        List<String> parts = new ArrayList<>();
        parts.add("**" + confident + "** issue(s)");
        if (possible > 0) parts.add(possible + " possible");
        long verified = all.stream().filter(f -> "confirmed".equals(f.verdict())).count();
        long unverified = all.stream().filter(f -> "uncertain".equals(f.verdict())).count();
        if (verified > 0) parts.add(verified + " verified");
        if (unverified > 0) parts.add(unverified + " unverified");
        sb.append(String.join(" · ", parts)).append('\n');
    }

    private static void appendTable(StringBuilder sb, List<AgentReviewResponse.Finding> all, Map<String, Map<?, ?>> nodes) {
        sb.append("\n| Check | Result | Coverage |\n|---|---|---|\n");
        for (Check check : CHECKS) {
            List<AgentReviewResponse.Finding> mine = all.stream().filter(f -> check.category().equals(f.category())).toList();
            State state = stateOf(check, nodes, true, !mine.isEmpty());
            sb.append("| ").append(check.icon()).append(' ').append(check.title()).append(" | ");
            switch (state) {
                case NOT_RUN -> sb.append("— not applicable | -");
                case SKIPPED -> sb.append("— nothing to review | -");
                case FAILED -> sb.append("⚠️ failed (not reviewed) | -");
                case RAN -> {
                    long sure = mine.stream().filter(f -> !f.isPossible()).count();
                    long maybe = mine.size() - sure;
                    List<String> parts = new ArrayList<>();
                    if (sure > 0) parts.add(sure + " issue(s)");
                    if (maybe > 0) parts.add(maybe + " possible");
                    sb.append(parts.isEmpty() ? "✅ No issues" : String.join(" · ", parts));
                    sb.append(" | ").append(coverageCell(nodes.get(check.node())));
                }
            }
            sb.append(" |\n");
        }
    }

    private static State stateOf(Check check, Map<String, Map<?, ?>> nodes, boolean nodesKnown, boolean hasFindings) {
        if (!nodesKnown) {
            return hasFindings ? State.RAN : State.NOT_RUN;
        }
        Map<?, ?> node = nodes.get(check.node());
        if (node == null) return hasFindings ? State.RAN : State.NOT_RUN;
        if (node.get("failed") != null) return State.FAILED;
        if (node.get("skipped") != null) return State.SKIPPED;
        return State.RAN;
    }

    private static String coverageCell(Map<?, ?> node) {
        Integer reviewed = intOf(node, "reviewed");
        Integer eligible = intOf(node, "eligible");
        return reviewed != null && eligible != null ? reviewed + "/" + eligible + " files" : "-";
    }

    private static String coverageSentence(Map<?, ?> node) {
        Integer reviewed = intOf(node, "reviewed");
        Integer eligible = intOf(node, "eligible");
        if (reviewed == null || eligible == null) return "in the files checked.";
        return reviewed.equals(eligible)
                ? "in the " + reviewed + " file(s) checked."
                : "in the " + reviewed + " of " + eligible + " files checked.";
    }

    private static Integer intOf(Map<?, ?> node, String key) {
        return node != null && node.get(key) instanceof Number n ? n.intValue() : null;
    }

    private static Map<String, Map<?, ?>> nodesByName(Map<String, Object> stats) {
        Map<String, Map<?, ?>> byName = new HashMap<>();
        if (stats.get("nodes") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("node") instanceof String name) {
                    byName.put(name, m);
                }
            }
        }
        return byName;
    }

    private static List<AgentReviewResponse.Finding> sorted(List<AgentReviewResponse.Finding> list) {
        List<AgentReviewResponse.Finding> copy = new ArrayList<>(list);
        copy.sort(Comparator.comparingInt((AgentReviewResponse.Finding f) -> rank(f.severity())));
        return copy;
    }

    private static void appendFinding(StringBuilder sb, AgentReviewResponse.Finding f, int[] shown) {
        if (shown[0]++ == MAX_FINDINGS) {
            sb.append("\n_…more findings not shown._\n");
        }
        if (shown[0] > MAX_FINDINGS) {
            return;
        }
        sb.append("- ").append(severityLabel(f.severity())).append(" · `").append(clean(orDash(f.file())));
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

    private static void appendFooter(StringBuilder sb, Map<String, Object> stats) {
        StringBuilder parts = new StringBuilder();
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
        if ("critical".equalsIgnoreCase(severity)) return 0;
        if ("high".equalsIgnoreCase(severity)) return 1;
        if ("medium".equalsIgnoreCase(severity)) return 2;
        if ("low".equalsIgnoreCase(severity)) return 3;
        return 4;
    }

    private static String severityLabel(String severity) {
        return switch (rank(severity)) {
            case 0 -> "🔴 **Critical**";
            case 1 -> "🔴 **High**";
            case 2 -> "🟠 **Medium**";
            case 3 -> "🟡 **Low**";
            default -> "⚪ **Info**";
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