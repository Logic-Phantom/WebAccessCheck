# WebAccessCheck

웹/SPA 화면을 대상으로 Axe-Core + Playwright 조합으로 접근성 검사를 수행하고, Spring 기반 Java 백엔드가 검사 실행/리포트 수집/인사이트 생성까지 자동화합니다.

## 아키텍처 개요
`
+----------------+        runAxe.js        +---------------------+       axe-results.json       +--------------------------+
| Java Backend   |        | Node.js Auditor      |            | Java Report Generators    |
| (Spring MVC)   |   1. 검사 명령          | (Playwright + Axe)   |   2. JSON 리포트 작성        | - 텍스트 리포트            |
|                |                        |                      |                             | - 실무 인사이트 리포트    |
+----------------+                        +---------------------+                             +--------------------------+
                                                                                          
           REST API                                                                       다운로드/조회
                                                                                          
   /api/accessibility/audit.do                                                    /api/accessibility/insights.do
`
- **AuditRunner**: Java에서 Node 프로세스를 호출해 Playwright + Axe-Core 검사를 수행하고 xe-results.json을 생성합니다.
- **AccessibilityAuditController**: REST API(/api/accessibility/audit.do)로 외부에서 검사를 트리거하고 결과 JSON을 반환합니다.
- **AccessibilityInsightsController**: xe-results.json을 Jackson으로 파싱해 ALT/필드레이블/탭 순서/기타 카테고리로 분류하여 실무 친화적인 리포트를 작성합니다.
- **ProjectRootResolver**: 서버/로컬 어디서 실행하든 동일한 clx-src/auditor 디렉터리를 찾도록 보조합니다.

## 동작 프로세스
1. **검사 요청**  POST /api/accessibility/audit.do?url={대상URL}
   - Playwright가 해당 URL을 열고 Axe-Core로 DOM을 분석, xe-results.json 생성
   - 동시에 xe-results_report.txt를 만들어 엔지니어링용 요약 제공
2. **인사이트 조회**  GET /api/accessibility/insights.do
   - Jackson으로 JSON을 분석해 ALT 텍스트/필드 레이블/탭포커스/기타 카테고리별 이슈 집계
   - 각 위반 항목마다 대표 HTML, CSS selector, Axe failureSummary를 포함한 xe-insights.txt 생성
3. **다운로드/공유**  생성된 텍스트 리포트를 사용해 QA/디자인/개발팀에 전달하거나 추가 포맷으로 변환

## 설치 & 준비
### Node/Playwright 설치
`
cd clx-src/auditor
npm install
npx playwright install
`

### Java/Spring 실행
- 일반 Spring MVC WAR 구조(src/main/java/com/tomatosystem/...)
- AuditRunner, AccessibilityAuditController, AccessibilityInsightsController가 핵심 모듈입니다.

## 주요 API
| Method | Endpoint                               | 설명 |
|--------|----------------------------------------|------|
| POST   | /api/accessibility/audit.do?url=...  | 지정 URL을 대상으로 Playwright + Axe-Core 검사 실행 |
| GET    | /api/accessibility/insights.do       | 최근 검사 결과를 ALT/레이블/탭 순서 별로 분석한 JSON + 텍스트 리포트 |
| GET    | /api/accessibility/report.do         | 고전적인 텍스트 리포트(xe-results_report.txt) 다운로드 |

## 리포트 예시
- clx-src/auditor/axe-results_report.txt
  - 전체 위반 목록, 심각도 분포, 권장 조치 등 일반 요약
- clx-src/auditor/axe-insights.txt
  - ALT 텍스트/필드 레이블/탭 순서별 대표 HTML과 selector, 근거 메시지를 포함한 실무 인사이트

## 개발 팁
- Playwright 검사 파일: clx-src/auditor/runAxe.js
- 결과 파일: clx-src/auditor/axe-results.json
- ProjectRootResolver를 통해 어느 환경에서도 경로 문제 없이 동작
- 추가 포맷(PDF/HTML 등)은 AccessibilityInsightsController에서 생성된 데이터 구조를 재사용해 확장 가능
