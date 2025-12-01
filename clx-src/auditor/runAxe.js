const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

async function runAudit(targetUrl) {
    const browser = await chromium.launch({ headless: true });
    const page = await browser.newPage();
    
    try {
        console.log("페이지 접속 중:", targetUrl);
        
        // SPA 페이지 접속 (네트워크 유휴 상태까지 대기)
        await page.goto(targetUrl, { 
            waitUntil: 'networkidle',  // 네트워크 요청이 완료될 때까지 대기
            timeout: 60000  // 60초 타임아웃
        });
        
        console.log("페이지 로드 완료, 동적 콘텐츠 렌더링 대기 중...");
        
        // SPA 동적 렌더링을 위한 추가 대기 전략
        // 1. 네트워크 유휴 상태 추가 대기 (2초)
        await page.waitForTimeout(2000);
        
        // 2. body 요소가 완전히 로드되었는지 확인
        await page.waitForSelector('body', { state: 'attached' });
        
        // 3. JavaScript 실행 완료를 위한 추가 대기
        await page.waitForLoadState('domcontentloaded');
        await page.waitForLoadState('networkidle');
        
        // 4. 추가 안정화 대기 (SPA 라우팅/렌더링 완료)
        await page.waitForTimeout(1000);
        
        console.log("동적 콘텐츠 렌더링 완료, 접근성 검사 시작...");
        
        // axe-core 삽입
        await page.addScriptTag({ path: require.resolve("axe-core") });
        
        // 검사 실행
        const results = await page.evaluate(async () => {
            return await axe.run();
        });
        
        // 결과 저장
        const resultPath = path.resolve(__dirname, "axe-results.json");
        fs.writeFileSync(resultPath, JSON.stringify(results, null, 2));
        
        console.log("Axe 검사 완료! 결과 저장:", resultPath);
        console.log("발견된 오류 수:", results.violations ? results.violations.length : 0);
        
        await browser.close();
        
    } catch (error) {
        console.error("검사 중 오류 발생:", error.message);
        await browser.close();
        throw error;
    }
}

// 커맨드라인 인자에서 URL 받기
const targetUrl = process.argv[2];

if (!targetUrl) {
    console.error("사용법: node runAxe.js <URL>");
    console.error("예시: node runAxe.js http://localhost:8080/exbuilder/view?clx=/sample/Main.clx");
    process.exit(1);
}

runAudit(targetUrl).catch(e => {
    console.error("오류:", e);
    process.exit(1);
});

