package com.tomatosystem.access;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class AuditRunner {
    
    /**
     * 프로젝트 루트 경로를 찾습니다.
     * 클래스 파일 위치를 기준으로 프로젝트 루트를 찾습니다.
     */
    /**
     * SPA 페이지의 접근성 검사를 실행합니다.
     * 
     * @param targetUrl 검사할 페이지의 URL
     * @return 검사 결과 JSON 파일 경로, 실패 시 null
     */
    public static String runAudit(String targetUrl) {
        try {
            // 프로젝트 루트 경로 찾기
            String projectRoot = ProjectRootResolver.resolve(AuditRunner.class);
            File auditorDir = ProjectRootResolver.getAuditorDir(AuditRunner.class);
            String auditorPath = auditorDir.getAbsolutePath();
            
            System.out.println("=== 접근성 검사 시작 ===");
            System.out.println("대상 URL: " + targetUrl);
            System.out.println("프로젝트 루트: " + projectRoot);
            System.out.println("Auditor 경로: " + auditorPath);
            
            // 경로 검증
            File runAxeFile = new File(auditorDir, "runAxe.js");
            if (!runAxeFile.exists()) {
                throw new IOException("runAxe.js 파일이 존재하지 않습니다: " + runAxeFile.getAbsolutePath());
            }
            
            System.out.println("경로 검증 완료");
            System.out.println();
            
            // Node.js runAxe.js 실행 설정 (URL을 인자로 전달)
            ProcessBuilder pb = new ProcessBuilder(
                    "node",
                    "runAxe.js",
                    targetUrl
            );
            pb.directory(auditorDir);
            pb.redirectErrorStream(true);
            
            // 실행 시작
            Process process = pb.start();
            
            // Node 콘솔 메시지 출력
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8")
            );
            
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Node] " + line);
            }
            
            int exitCode = process.waitFor();
            System.out.println("\nNode 프로세스 종료 코드 = " + exitCode);
            
            // 결과 JSON 파일 로드
            File resultFile = new File(auditorPath + File.separator + "axe-results.json");
            if (resultFile.exists()) {
                System.out.println("\n=== 검사 결과 ===");
                System.out.println("결과 파일: " + resultFile.getPath());
                return resultFile.getPath();
            } else {
                System.out.println("결과 파일이 생성되지 않았습니다.");
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("검사 실행 중 오류 발생:");
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 메인 메서드 - 커맨드라인에서 직접 실행 시 사용
     */
    public static void main(String[] args) {
        String targetUrl;
        
        // 커맨드라인 인자에서 URL 받기
        if (args.length > 0) {
            targetUrl = args[0];
        } else {
            // 기본값: 테스트용 로컬 HTML 파일
            String projectRoot = System.getProperty("user.dir");
            String testPagePath = "file:///" + projectRoot.replace("\\", "/") 
                    + "/clx-src/auditor/test-page.html";
            targetUrl = testPagePath;
            System.out.println("URL이 지정되지 않아 기본 테스트 페이지를 사용합니다.");
        }
        
        String resultPath = runAudit(targetUrl);
        
        if (resultPath != null) {
            System.out.println("\n=== 결과 파일 내용 (일부) ===");
            ResultReader.readResult(resultPath);
        }
    }
}

