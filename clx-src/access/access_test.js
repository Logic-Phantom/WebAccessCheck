/************************************************
 * access_test.js
 * Created at 2025. 12. 1. 오후 2:12:57.
 *
 * @author LCM
 ************************************************/

/*
 * "Button" 버튼(btn1)에서 click 이벤트 발생 시 호출.
 * 사용자가 컨트롤을 클릭할 때 발생하는 이벤트.
 */
function onBtn1Click(e){
//// GET 요청
//fetch('/api/accessibility/audit?url=http://localhost:8080/exbuilder/view?clx=/access/test.clx')
//  .then(response => response.json())
//  .then(data => {
//    console.log('검사 결과:', data);
//    if (data.success) {
//      console.log('결과 JSON:', data.result);
//    }
//  });
//
//// POST 요청
//fetch('/api/accessibility/audit', {
//  method: 'POST',
//  headers: {
//    'Content-Type': 'application/x-www-form-urlencoded',
//  },
//  body: 'url=http://localhost:8080/exbuilder/view?clx=/sample/Main.clx'
//})
//  .then(response => response.json())
//  .then(data => console.log(data));
app.lookup("sms1").send()
}

/*
 * "Button" 버튼(btn2)에서 click 이벤트 발생 시 호출.
 * 사용자가 컨트롤을 클릭할 때 발생하는 이벤트.
 */
function onBtn2Click(e){
	var btn2 = e.control;
	app.lookup("sms2").send();
}
