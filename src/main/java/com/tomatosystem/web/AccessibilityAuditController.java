package com.tomatosystem.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tomatosystem.access.AuditRunner;
import com.tomatosystem.access.AuditReportGenerator;

/**
 * 접근성 검사 컨트롤러
 * SPA 페이지의 접근성을 검사하고 결과를 반환합니다.
 */
@RestController
public class AccessibilityAuditController {
    
    /**
     * 프로젝트 루트 경로를 찾습니다.
     */
    private String getProjectRoot() {
        try {
            // 클래스 파일의 위치를 기준으로 찾기
            String classPath = AccessibilityAuditController.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
            
            // Windows 경로 디코딩
            if (classPath.startsWith("/")) {
                classPath = classPath.substring(1);
            }
            classPath = java.net.URLDecoder.decode(classPath, "UTF-8");
            
            File classFile = new File(classPath);
            
            // target/classes에서 프로젝트 루트로 이동
            File currentDir = classFile.getAbsoluteFile();
            while (currentDir != null) {
                // clx-src 폴더가 있는지 확인
                File clxSrcDir = new File(currentDir, "clx-src");
                if (clxSrcDir.exists() && clxSrcDir.isDirectory()) {
                    return currentDir.getAbsolutePath();
                }
                currentDir = currentDir.getParentFile();
            }
            
            // 찾지 못한 경우 user.dir 사용
            return System.getProperty("user.dir");
            
        } catch (Exception e) {
            // 오류 발생 시 user.dir 사용
            return System.getProperty("user.dir");
        }
    }
    
    /**
     * 접근성 검사 실행 (GET)
     * 
     * @param url 검사할 페이지 URL
     * @return 검사 결과 JSON
     */
    @GetMapping(value = "/api/accessibility/audit.do", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> auditGet(
            @RequestParam(value = "url", required = true) String url) {
        return executeAudit(url);
    }
    
    /**
     * 접근성 검사 실행 (POST)
     * 
     * @param url 검사할 페이지 URL
     * @return 검사 결과 JSON
     */
    @PostMapping(value = "/api/accessibility/audit.do", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> auditPost(
            @RequestParam(value = "url", required = true) String url) {
        return executeAudit(url);
    }
    
    /**
     * 접근성 검사 실행 및 결과 반환
     */
    private ResponseEntity<String> executeAudit(String url) {
        try {
            // URL 유효성 검사
            if (url == null || url.trim().isEmpty()) {
                String errorJson = "{\"success\":false,\"error\":\"URL이 필요합니다.\"}";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                return new ResponseEntity<>(errorJson, headers, HttpStatus.BAD_REQUEST);
            }
            
            // 접근성 검사 실행
            String resultPath = AuditRunner.runAudit(url);
            
            if (resultPath != null && new File(resultPath).exists()) {
                // 결과 파일 읽기
                File resultFile = new File(resultPath);
                String jsonContent = new String(
                    Files.readAllBytes(resultFile.toPath()), 
                    StandardCharsets.UTF_8
                );
                
                // 텍스트 리포트 생성
                String reportPath = AuditReportGenerator.generateTextReport(resultPath);
                
                // JSON 응답 생성 (문자열로 직접 구성)
                StringBuilder responseJson = new StringBuilder();
                responseJson.append("{");
                responseJson.append("\"success\":true,");
                responseJson.append("\"url\":\"").append(escapeJson(url)).append("\",");
                responseJson.append("\"resultPath\":\"").append(escapeJson(resultPath)).append("\",");
                if (reportPath != null) {
                    responseJson.append("\"reportPath\":\"").append(escapeJson(reportPath)).append("\",");
                }
                responseJson.append("\"summary\":\"").append(escapeJson(AuditReportGenerator.getSummary(resultPath))).append("\",");
                responseJson.append("\"result\":").append(jsonContent).append(",");
                responseJson.append("\"message\":\"접근성 검사가 완료되었습니다.\"");
                responseJson.append("}");
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                return new ResponseEntity<>(responseJson.toString(), headers, HttpStatus.OK);
            } else {
                String errorJson = "{\"success\":false,\"error\":\"검사 결과 파일을 생성할 수 없습니다.\"}";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                return new ResponseEntity<>(errorJson, headers, HttpStatus.INTERNAL_SERVER_ERROR);
            }
            
        } catch (Exception e) {
            String errorJson = "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
            e.printStackTrace();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new ResponseEntity<>(errorJson, headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * JSON 문자열 이스케이프
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * 검사 결과 리포트 다운로드 (텍스트)
     * 
     * @param response HTTP 응답 객체
     * @return 리포트 파일
     */
    @GetMapping("/api/accessibility/report.do")
    public void downloadReport(HttpServletResponse response) {
        try {
            String projectRoot = getProjectRoot();
            String jsonPath = projectRoot + File.separator + "clx-src" + File.separator 
                    + "auditor" + File.separator + "axe-results.json";
            
            File jsonFile = new File(jsonPath);
            if (!jsonFile.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("검사 결과 파일을 찾을 수 없습니다.");
                return;
            }
            
            // 리포트 생성
            String reportPath = AuditReportGenerator.generateTextReport(jsonPath);
            if (reportPath == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("리포트 생성에 실패했습니다.");
                return;
            }
            
            File reportFile = new File(reportPath);
            if (!reportFile.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("리포트 파일을 찾을 수 없습니다.");
                return;
            }
            
            response.setContentType("text/plain; charset=UTF-8");
            response.setHeader("Content-Disposition", 
                    "attachment; filename=\"accessibility-report.txt\"");
            response.setContentLengthLong(reportFile.length());
            
            try (FileInputStream fis = new FileInputStream(reportFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, bytesRead);
                }
            }
            
        } catch (IOException e) {
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("리포트 다운로드 중 오류가 발생했습니다: " + e.getMessage());
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }
    
    /**
     * 검사 결과 파일 다운로드 (JSON)
     * 
     * @param response HTTP 응답 객체
     * @return 결과 파일
     */
    @GetMapping("/api/accessibility/download.do")
    public void downloadResult(HttpServletResponse response) {
        try {
            // AuditRunner의 getProjectRoot 메서드를 사용하거나 직접 경로 찾기
            String projectRoot = getProjectRoot();
            String resultPath = projectRoot + File.separator + "clx-src" + File.separator 
                    + "auditor" + File.separator + "axe-results.json";
            
            File resultFile = new File(resultPath);
            
            if (!resultFile.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("결과 파일을 찾을 수 없습니다.");
                return;
            }
            
            response.setContentType("application/json");
            response.setHeader("Content-Disposition", 
                    "attachment; filename=\"axe-results.json\"");
            response.setContentLengthLong(resultFile.length());
            
            try (FileInputStream fis = new FileInputStream(resultFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, bytesRead);
                }
            }
            
        } catch (IOException e) {
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("파일 다운로드 중 오류가 발생했습니다: " + e.getMessage());
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }
}

