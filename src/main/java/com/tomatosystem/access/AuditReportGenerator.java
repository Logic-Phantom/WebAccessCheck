package com.tomatosystem.access;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 접근성 검사 결과 리포트 생성기 (경량 JSON 파싱)
 */
public class AuditReportGenerator {
    
    /**
     * JSON 결과 파일을 읽어서 텍스트 리포트를 생성합니다.
     */
    public static String generateTextReport(String jsonFilePath) {
        try {
            String jsonContent = new String(
                Files.readAllBytes(new File(jsonFilePath).toPath()),
                StandardCharsets.UTF_8
            );
            
            List<ViolationInfo> violations = extractViolations(jsonContent);
            SummaryInfo summary = buildSummary(jsonContent, violations);
            
            StringBuilder report = new StringBuilder();
            appendHeader(report, summary);
            appendSummary(report, summary);
            appendViolationDetails(report, violations);
            appendRecommendations(report, summary);
            
            String reportPath = jsonFilePath.replace(".json", "_report.txt");
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(reportPath), StandardCharsets.UTF_8)) {
                writer.write(report.toString());
            }
            return reportPath;
        } catch (Exception e) {
            System.err.println("리포트 생성 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private static void appendHeader(StringBuilder report, SummaryInfo summary) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        report.append(repeat("=", 92)).append("\n");
        report.append("접근성 검사 리포트 (Accessibility Audit Report)\n");
        report.append(repeat("=", 92)).append("\n\n");
        report.append(String.format("%-18s: %s\n", "검사 일시", sdf.format(new Date())));
        report.append(String.format("%-18s: %s\n", "검사 대상 URL", summary.url != null ? summary.url : "-"));
        report.append(String.format("%-18s: %s\n\n", "검사 엔진", summary.engineInfo));
    }
    
    private static void appendSummary(StringBuilder report, SummaryInfo summary) {
        report.append(repeat("-", 92)).append("\n");
        report.append("요약 Overview\n");
        report.append(repeat("-", 92)).append("\n");
        report.append(String.format("• 총 오류               : %d건\n", summary.totalViolations));
        report.append(String.format("• 통과 / 미완료 / 해당없음 : %d / %d / %d\n",
                summary.passCount, summary.incompleteCount, summary.inapplicableCount));
        report.append("• 심각도 분포           : ");
        report.append(String.format("Critical %d · Serious %d · Moderate %d · Minor %d\n\n",
                summary.critical, summary.serious, summary.moderate, summary.minor));
        if (summary.totalViolations > 0) {
            report.append("우선순위: Critical → Serious → Moderate 순으로 조치 후 재검사를 진행하세요.\n\n");
        }
    }
    
    private static void appendViolationDetails(StringBuilder report, List<ViolationInfo> violations) {
        report.append(repeat("=", 92)).append("\n");
        report.append("상세 이슈 (Detailed Findings) - 총 ").append(violations.size()).append("건\n");
        report.append(repeat("=", 92)).append("\n\n");
        
        if (violations.isEmpty()) {
            report.append("발견된 접근성 오류가 없습니다. 👍\n\n");
            return;
        }
        
        for (int i = 0; i < violations.size(); i++) {
            ViolationInfo v = violations.get(i);
            report.append(String.format("[%d] %s (%s)\n", i + 1, v.id, v.impact.toUpperCase()));
            report.append(repeat("-", 92)).append("\n");
            report.append("• 이슈 설명      : ").append(v.description).append("\n");
            report.append("• 권장 조치      : ").append(v.help).append("\n");
            if (v.helpUrl != null) {
                report.append("• 참고 문서      : ").append(v.helpUrl).append("\n");
            }
            report.append("• 영향 요소 개수 : ").append(v.nodeCount).append("개\n");
            if (!v.htmlSnippets.isEmpty()) {
                report.append("• 대표 문제 요소\n");
                int limit = Math.min(v.htmlSnippets.size(), 5);
                for (int j = 0; j < limit; j++) {
                    report.append(String.format("    - %s\n", v.htmlSnippets.get(j)));
                    if (j < v.selectors.size()) {
                        report.append(String.format("      selector: %s\n", v.selectors.get(j)));
                    }
                }
                if (v.htmlSnippets.size() > limit) {
                    report.append(String.format("    ... 외 %d개\n", v.htmlSnippets.size() - limit));
                }
            }
            report.append("• 현업 조치 TIP  : 위 요소에 적절한 속성/구조를 부여해 WCAG 조건을 충족시키세요.\n\n");
        }
    }
    
    private static void appendRecommendations(StringBuilder report, SummaryInfo summary) {
        report.append(repeat("=", 92)).append("\n");
        report.append("개선 권장사항\n");
        report.append(repeat("=", 92)).append("\n");
        if (summary.totalViolations == 0) {
            report.append("✓ 접근성 오류가 없어 매우 좋은 상태입니다. 주기적인 재검사만 수행하면 좋습니다.\n\n");
        } else {
            report.append("1. Critical/Serious 항목부터 우선 수정 후 재검사를 수행하세요.\n");
            report.append("2. 각 이슈의 참고 문서(Help URL)를 열어 구체적인 수정 예시를 확인하세요.\n");
            report.append("3. UI 변경 후 다시 본 도구를 실행하여 개선 여부를 추적하세요.\n\n");
        }
        report.append(repeat("=", 92)).append("\n");
        report.append("리포트 생성 완료\n");
        report.append(repeat("=", 92)).append("\n");
    }
    
    private static List<ViolationInfo> extractViolations(String json) {
        List<ViolationInfo> violations = new ArrayList<>();
        Pattern violationPattern = Pattern.compile(
                "\"id\"\\s*:\\s*\"([^\"]+)\".*?\"impact\"\\s*:\\s*\"([^\"]+)\".*?\"description\"\\s*:\\s*\"([^\"]+)\".*?\"help\"\\s*:\\s*\"([^\"]+)\"",
                Pattern.DOTALL);
        Matcher matcher = violationPattern.matcher(json);
        while (matcher.find()) {
            ViolationInfo v = new ViolationInfo();
            v.id = matcher.group(1);
            v.impact = matcher.group(2);
            v.description = unescapeJson(matcher.group(3));
            v.help = unescapeJson(matcher.group(4));
            int sectionEnd = findMatchingBrace(json, matcher.start());
            String violationSection = sectionEnd > matcher.start()
                    ? json.substring(matcher.start(), sectionEnd)
                    : json.substring(matcher.start());
            v.helpUrl = extractString(violationSection, "\"helpUrl\"\\s*:\\s*\"([^\"]+)\"");
            parseNodes(violationSection, v);
            violations.add(v);
        }
        return violations;
    }
    
    private static void parseNodes(String violationSection, ViolationInfo v) {
        int nodesIdx = violationSection.indexOf("\"nodes\"");
        if (nodesIdx < 0) return;
        int arrayStart = violationSection.indexOf('[', nodesIdx);
        int arrayEnd = findMatchingBracket(violationSection, arrayStart);
        if (arrayStart < 0 || arrayEnd < 0) return;
        String nodesArray = violationSection.substring(arrayStart + 1, arrayEnd);
        Pattern nodePattern = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);
        Matcher nodeMatcher = nodePattern.matcher(nodesArray);
        LinkedHashSet<String> snippetSet = new LinkedHashSet<>();
        while (nodeMatcher.find() && snippetSet.size() < 10) {
            String block = nodeMatcher.group(1);
            String html = extractString(block, "\"html\"\\s*:\\s*\"([^\"]+)\"");
            String selector = extractString(block, "\"target\"\\s*:\\s*\\[\\s*\"([^\"]+)\"");
            if (html != null && snippetSet.add(cleanSnippet(unescapeJson(html)))) {
                v.htmlSnippets.add(cleanSnippet(unescapeJson(html)));
                v.selectors.add(selector != null ? selector : "selector 정보 없음");
            }
            v.nodeCount++;
        }
    }
    
    private static SummaryInfo buildSummary(String json, List<ViolationInfo> violations) {
        SummaryInfo summary = new SummaryInfo();
        summary.url = extractString(json, "\"url\"\\s*:\\s*\"([^\"]+)\"");
        String engineName = extractString(json, "\"name\"\\s*:\\s*\"([^\"]+)\"");
        String engineVersion = extractString(json, "\"version\"\\s*:\\s*\"([^\"]+)\"");
        summary.engineInfo = engineName != null && engineVersion != null
                ? engineName + " v" + engineVersion : "-";
        summary.totalViolations = violations.size();
        for (ViolationInfo v : violations) {
            switch (v.impact.toLowerCase()) {
                case "critical": summary.critical++; break;
                case "serious": summary.serious++; break;
                case "moderate": summary.moderate++; break;
                case "minor": summary.minor++; break;
            }
        }
        summary.passCount = countArrayOccurrences(json, "\"passes\"");
        summary.incompleteCount = countArrayOccurrences(json, "\"incomplete\"");
        summary.inapplicableCount = countArrayOccurrences(json, "\"inapplicable\"");
        return summary;
    }
    
    private static String extractString(String text, String regex) {
        if (text == null) return null;
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        return m.find() ? unescapeJson(m.group(1)) : null;
    }
    
    private static int countArrayOccurrences(String json, String key) {
        int total = 0;
        Matcher m = Pattern.compile(key + "\\s*:\\s*\\[", Pattern.DOTALL).matcher(json);
        while (m.find()) {
            int start = m.end() - 1;
            int end = findMatchingBracket(json, start);
            if (end > start) {
                String section = json.substring(start, end + 1);
                Matcher itemMatcher = Pattern.compile("\\{").matcher(section);
                while (itemMatcher.find()) total++;
            }
        }
        return total;
    }
    
    private static int findMatchingBracket(String text, int startIndex) {
        if (startIndex < 0 || startIndex >= text.length()) return -1;
        char open = text.charAt(startIndex);
        char close = open == '[' ? ']' : '}';
        int depth = 0;
        boolean inString = false;
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && text.charAt(Math.max(0, i - 1)) != '\\') {
                inString = !inString;
            } else if (!inString) {
                if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }
    
    private static int findMatchingBrace(String text, int startIndex) {
        return findMatchingBracket(text, startIndex);
    }
    
    private static String cleanSnippet(String html) {
        return html == null ? "" : html.replaceAll("\\s+", " ").trim();
    }
    
    private static String unescapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\\"", "\"")
                  .replace("\\\\", "\\")
                  .replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t");
    }
    
    private static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(str);
        return sb.toString();
    }
    
    /**
     * JSON 결과에서 요약 문자열만 필요할 때 호출.
     */
    public static String getSummary(String jsonFilePath) {
        try {
            String jsonContent = new String(
                Files.readAllBytes(new File(jsonFilePath).toPath()),
                StandardCharsets.UTF_8
            );
            List<ViolationInfo> violations = extractViolations(jsonContent);
            int passCount = countArrayOccurrences(jsonContent, "\"passes\"");
            return String.format("오류: %d건, 통과: %d건", violations.size(), passCount);
        } catch (Exception e) {
            return "요약 정보를 가져올 수 없습니다.";
        }
    }
    
    private static class ViolationInfo {
        String id;
        String impact;
        String description;
        String help;
        String helpUrl;
        int nodeCount;
        List<String> htmlSnippets = new ArrayList<>();
        List<String> selectors = new ArrayList<>();
    }
    
    private static class SummaryInfo {
        String url;
        String engineInfo = "-";
        int totalViolations;
        int passCount;
        int incompleteCount;
        int inapplicableCount;
        int critical;
        int serious;
        int moderate;
        int minor;
    }
}
