# eXBuilder6 SPA 접근성 자동 진단 (Java + Node.js + axe-core + Playwright)

## 프로젝트 개요

**목적**

* eXBuilder6 화면(CLX 기반 SPA)을 대상으로 **자동 접근성(A11y) 검증** 수행
* 실제 SPA 렌더링 후 axe-core를 이용하여 스크린리더 기준 접근성 위반 탐지 및 JSON 보고서 생성

**주요 기능**

* eXBuilder6 SPA 화면 렌더링 자동화
* Headless Browser(Playwright) 기반 DOM 생성
* axe-core를 통한 자동 접근성 검사
* Java에서 Node.js 프로세스 호출 및 결과 처리
* JSON 결과 저장 및 Java에서 조회

## 아키텍처

```
┌───────────────────────┐
│      Java Backend     │
│  (AuditRunner.java)   │
│ - 파일 업로드        │
│ - Node.js 실행 호출  │
│ - 결과 JSON 처리      │
└─────────┬─────────────┘
          │
          ▼
┌───────────────────────┐
│       Node.js Auditor │
│  (runAxe.js)          │
│ - Playwright 실행     │
│ - eXBuilder6 SPA 로딩 │
│ - axe-core 검사       │
│ - JSON 리포트 생성    │
└─────────┬─────────────┘
          │
          ▼
┌───────────────────────┐
│     Axe-core Library  │
│ - DOM 기반 접근성 검사│
│ - 스크린리더 기준     │
└───────────────────────┘
```

## 프로세스

1. Java에서 auditor 실행

   * `AuditRunner.java` → `runAxe.js` Node.js 실행
2. Node.js Auditor 실행

   * Playwright Chromium Headless 실행
   * SPA 화면 로딩 및 렌더링 완료 대기
   * axe-core 스크립트 삽입
3. axe-core 검사

   * `axe.run()` → JSON 결과 생성
   * `axe-results.json` 저장
4. Java에서 결과 처리

   * `ResultReader.java` → JSON 파일 읽기
   * 콘솔 출력 또는 리포트 생성

## 설치 및 실행 방법

1️⃣ Node.js 환경 준비

```bash
cd auditor
npm install
npx playwright install
```

2️⃣ Java 컴파일 & 실행

```bash
cd backend
javac *.java
java AuditRunner
```

3️⃣ 결과 확인

* `auditor/axe-results.json` 생성
* violations 항목 확인 가능

## 테스트 페이지

```html
<img src="no-alt.png">
<button>클릭</button>
```

* 나중에 eXBuilder6 CLX 화면 URL로 변경 가능
* SPA 렌더링 후 동적 DOM 기반 검사 가능

## 향후 확장 포인트

1. eXBuilder6 SPA URL 자동 로딩
2. 파일 업로드 → 검사 자동화 UI
3. PDF/HTML 리포트 자동 생성
4. 다중 화면(batch) 접근성 검사
5. 탭·팝업·그리드 등 동적 상태 검사

## 필요 조건

* Node.js ≥ 16
* Java ≥ 8
* Playwright 크롬 브라우저 설치
* axe-core NPM 설치

## 프로젝트 구조

```
eX-A11y-Audit/
├── auditor/
│   ├── runAxe.js
│   ├── package.json
│   └── test-page.html
└── backend/
    ├── AuditRunner.java
    └── ResultReader.java
```

## 요약

* Java : orchestration, 결과 처리
* Node.js + Playwright : SPA 렌더링 + axe-core 검사
* axe-core : 스크린리더 기준 접근성 검증
* POC 단계 완료 후 → eXBuilder6 CLX SPA 적용 가능
