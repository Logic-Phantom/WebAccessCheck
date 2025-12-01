package com.tomatosystem.web;

import java.io.File;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomatosystem.access.ProjectRootResolver;

/**
 * 실무 친화적인 접근성 인사이트 리포트를 생성하는 컨트롤러
 */
@RestController
public class AccessibilityInsightsController {
    
    private enum InsightCategory {
        ALT_TEXT("ALT 텍스트"), 
        FIELD_LABEL("필드 레이블"), 
        TAB_ORDER("탭/포커스"), 
        OTHER("기타");
        
        private final String label;
        InsightCategory(String label) { this.label = label; }
        public String label() { return label; }
    }
    
    @GetMapping(value = "/api/accessibility/insights.do", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> generateInsights() {
        try {
            File jsonFile = ProjectRootResolver.getAuditorFile(AccessibilityInsightsController.class, "axe-results.json");
            if (!jsonFile.exists()) {
                return jsonResponse(HttpStatus.NOT_FOUND, "{\"success\":false,\"error\":\"검사 결과 파일을 찾을 수 없습니다.\"}");
            }
            
            Map<InsightCategory, List<InsightRecord>> categorized = analyzeViolations(jsonFile);
            String reportPath = writeInsightReport(categorized, jsonFile);
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.createObjectNode()
                    .put("success", true)
                    .put("reportPath", reportPath)
                    .put("lastScan", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(jsonFile.lastModified()))
                    .put("altIssues", categorized.get(InsightCategory.ALT_TEXT).size())
                    .put("labelIssues", categorized.get(InsightCategory.FIELD_LABEL).size())
                    .put("tabIssues", categorized.get(InsightCategory.TAB_ORDER).size())
                    .put("otherIssues", categorized.get(InsightCategory.OTHER).size());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new ResponseEntity<>(mapper.writeValueAsString(root), headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "{\"success\":false,\"error\":\"인사이트 리포트 생성 중 오류가 발생했습니다: " + escapeJson(e.getMessage()) + "\"}");
        }
    }
    
    private Map<InsightCategory, List<InsightRecord>> analyzeViolations(File jsonFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonFile);
        JsonNode violations = root.path("violations");
        Map<InsightCategory, List<InsightRecord>> map = new EnumMap<>(InsightCategory.class);
        for (InsightCategory cat : InsightCategory.values()) {
            map.put(cat, new ArrayList<>());
        }
        
        if (violations.isArray()) {
            for (JsonNode violation : violations) {
                InsightCategory category = categorizeViolation(violation);
                map.get(category).add(buildRecord(violation));
            }
        }
        return map;
    }
    
    private InsightCategory categorizeViolation(JsonNode violation) {
        String id = violation.path("id").asText("").toLowerCase();
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (violation.has("tags")) {
            violation.get("tags").forEach(tag -> tags.add(tag.asText().toLowerCase()));
        }
        
        if (isAltIssue(id, tags)) {
            return InsightCategory.ALT_TEXT;
        }
        if (isLabelIssue(id, tags)) {
            return InsightCategory.FIELD_LABEL;
        }
        if (isTabIssue(id, tags)) {
            return InsightCategory.TAB_ORDER;
        }
        return InsightCategory.OTHER;
    }
    
    private boolean isAltIssue(String id, LinkedHashSet<String> tags) {
        if (id.contains("image-alt") || id.contains("input-image-alt") || id.contains("role-img-alt")
                || id.contains("svg-img-alt") || id.contains("img-alt") || id.contains("object-alt")
                || id.contains("server-side-image-map")) {
            return true;
        }
        if (tags.contains("cat.text-alternatives")) {
            // 일부 규칙(document-title 등)을 제외
            return !(id.contains("document-title") || id.contains("html-has-lang"));
        }
        return false;
    }
    
    private boolean isLabelIssue(String id, LinkedHashSet<String> tags) {
        return id.contains("label") || id.contains("field")
                || id.contains("aria-command") || id.contains("aria-input")
                || id.contains("form-field") || id.contains("autocomplete")
                || tags.contains("cat.name-role-value") || tags.contains("cat.forms");
    }
    
    private boolean isTabIssue(String id, LinkedHashSet<String> tags) {
        return id.contains("tabindex") || id.contains("focus") || id.contains("keyboard")
                || id.contains("landmark") || tags.contains("cat.keyboard");
    }
    
    private InsightRecord buildRecord(JsonNode violation) {
        InsightRecord record = new InsightRecord();
        record.id = violation.path("id").asText("-");
        record.description = violation.path("description").asText("-");
        record.help = violation.path("help").asText("-");
        record.impact = violation.path("impact").asText("-");
        record.helpUrl = violation.path("helpUrl").asText(null);
        
        JsonNode nodes = violation.path("nodes");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                NodeDetail detail = new NodeDetail();
                detail.snippet = cleanSnippet(node.path("html").asText(""));
                JsonNode target = node.path("target");
                if (target.isArray() && target.size() > 0) {
                    detail.selector = target.get(0).asText();
                }
                collectReasons(detail, node);
                record.nodeDetails.add(detail);
                record.nodeCount++;
                if (record.nodeDetails.size() >= 5) {
                    break;
                }
            }
        }
        return record;
    }
    
    private String writeInsightReport(Map<InsightCategory, List<InsightRecord>> data, File jsonFile) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        File auditorDir = ProjectRootResolver.getAuditorDir(AccessibilityInsightsController.class);
        String reportPath = new File(auditorDir, "axe-insights.txt").getAbsolutePath();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(reportPath), StandardCharsets.UTF_8)) {
            writer.write(repeat("=", 92) + "\n");
            writer.write("실무 접근성 인사이트 리포트\n");
            writer.write(repeat("=", 92) + "\n\n");
            writer.write(String.format("마지막 검사 시각 : %s\n\n", sdf.format(jsonFile.lastModified())));
            
            for (InsightCategory category : InsightCategory.values()) {
                List<InsightRecord> records = data.get(category);
                writer.write(repeat("-", 92) + "\n");
                writer.write(String.format("%s 이슈 (%d건)\n", category.label(), records.size()));
                writer.write(repeat("-", 92) + "\n\n");
                if (records.isEmpty()) {
                    writer.write("해당 유형의 문제가 발견되지 않았습니다.\n\n");
                    continue;
                }
                int idx = 1;
                for (InsightRecord record : records) {
                    writer.write(String.format("[%d] %s (%s)\n", idx++, record.id, record.impact.toUpperCase()));
                    writer.write("ㆍ설명      : " + record.description + "\n");
                    writer.write("ㆍ권장 조치 : " + record.help + "\n");
                    if (record.helpUrl != null) {
                        writer.write("ㆍ참고 문서 : " + record.helpUrl + "\n");
                    }
                    writer.write("ㆍ영향 요소 수 : " + record.nodeCount + "\n");
                    appendNodeDetails(writer, record);
                    writer.write("\n");
                }
            }
        }
        return reportPath;
    }
    
    private ResponseEntity<String> jsonResponse(HttpStatus status, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(body, headers, status);
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    
    private void collectReasons(NodeDetail detail, JsonNode node) {
        String failureSummary = node.path("failureSummary").asText(null);
        if (failureSummary != null && !failureSummary.isEmpty()) {
            detail.reasons.add(failureSummary);
        }
        gatherCheckMessages(detail.reasons, node.path("any"));
        gatherCheckMessages(detail.reasons, node.path("all"));
        gatherCheckMessages(detail.reasons, node.path("none"));
    }
    
    private void gatherCheckMessages(List<String> reasons, JsonNode checks) {
        if (checks == null || !checks.isArray()) {
            return;
        }
        for (JsonNode check : checks) {
            String message = check.path("message").asText(null);
            if (message != null && !message.isEmpty()) {
                reasons.add(message);
            }
        }
    }
    
    private void appendNodeDetails(Writer writer, InsightRecord record) throws IOException {
        if (record.nodeDetails.isEmpty()) {
            writer.write("ㆍ대표 문제 요소 정보를 가져올 수 없습니다.\n");
            return;
        }
        writer.write("ㆍ대표 문제 요소\n");
        for (NodeDetail detail : record.nodeDetails) {
            writer.write("    - " + detail.snippet + "\n");
            if (detail.selector != null) {
                writer.write("      selector: " + detail.selector + "\n");
            }
            if (!detail.reasons.isEmpty()) {
                writer.write("      근거: " + detail.reasons.get(0) + "\n");
                for (int i = 1; i < detail.reasons.size(); i++) {
                    writer.write("             " + detail.reasons.get(i) + "\n");
                }
            }
        }
    }
    
    private String cleanSnippet(String html) {
        return html == null ? "" : html.replaceAll("\\s+", " ").trim();
    }
    
    private String repeat(String text, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(text);
        return sb.toString();
    }
    
    private static class InsightRecord {
        String id;
        String description;
        String help;
        String impact;
        String helpUrl;
        int nodeCount;
        List<NodeDetail> nodeDetails = new ArrayList<>();
    }
    
    private static class NodeDetail {
        String snippet;
        String selector;
        List<String> reasons = new ArrayList<>();
    }
}

