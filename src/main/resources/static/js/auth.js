const KAKAO_JS_KEY = 'aa597d0bd76340869092d4d3a160b9c4';
if (typeof Kakao !== 'undefined' && !Kakao.isInitialized()) {
    Kakao.init(KAKAO_JS_KEY);
}

let currentUser = {
    token: localStorage.getItem('accessToken'),
    nickname: localStorage.getItem('nickname'),
    role: localStorage.getItem('role')
};

window.addEventListener('DOMContentLoaded', () => {
    updateTopNavUI();
});

function kakaoLogin() {
    console.log("카카오 로그인 버튼 클릭됨!"); // 디버깅용 
    if (!Kakao.isInitialized()) {
        console.log("카카오 SDK가 초기화되지 않아 다시 초기화합니다.");
        Kakao.init(KAKAO_JS_KEY);
    }
    
    Kakao.Auth.login({
        success: function(authObj) {
            console.log("카카오 토큰 발급 완료:", authObj);
            reqBackendLogin(authObj.access_token);
        },
        fail: function(err) {
            console.error("카카오 로그인 팝업 오류:", err);
            alert("카카오 로그인 실패! 콘솔을 확인해주세요.");
        }
    });
}

async function reqBackendLogin(kakaoToken) {
    try {
        const res = await fetch('/api/auth/kakao/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ kakaoAccessToken: kakaoToken })
        });
        const data = await res.json();
        
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('nickname', data.nickname);
        localStorage.setItem('role', data.role);
        
        currentUser = { token: data.accessToken, nickname: data.nickname, role: data.role };
        updateTopNavUI();
        
        if (typeof loadBoards === 'function') {
            loadBoards();
        }
    } catch (error) {
        console.error("로그인 에러:", error);
        alert("서버 오류로 로그인에 실패했습니다.");
    }
}

function logout() {
    localStorage.clear();
    currentUser = { token: null, nickname: null, role: null };
    updateTopNavUI();
    
    if (typeof loadBoards === 'function') {
        loadBoards();
    }
}

function updateTopNavUI() {
    const authArea = document.getElementById('authArea');
    if (!authArea) return;

    if (currentUser.token) {
        const roleBadge = currentUser.role === 'ROLE_ADMIN' ? 
            '<span style="background:#fecaca; color:#991b1b; padding:2px 6px; border-radius:4px; font-size:11px; margin-left:5px; font-weight:bold;">admin</span>' : 
            '<span style="background:#fef08a; color:#854d0e; padding:2px 6px; border-radius:4px; font-size:11px; margin-left:5px; font-weight:bold;">user</span>';

        authArea.innerHTML = `
            <div style="display:flex; align-items:center; color: #eee; font-size: 14px;">
                ${roleBadge}
                <button onclick="logout()" style="background:#444; color:#fff; border:1px solid #666; padding:5px 10px; border-radius:4px; margin-left:15px; cursor:pointer; font-weight:bold;">로그아웃</button>
            </div>
        `;
    } else {
        authArea.innerHTML = `
            <button onclick="kakaoLogin()" style="background-color:#FEE500; color:#000; border:none; padding:7px 15px; border-radius:6px; font-weight:bold; cursor:pointer; font-size: 14px;">
                카카오 로그인
            </button>
        `;
    }
}

function getAuthHeaders() {
    return {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + currentUser.token
    };
}
