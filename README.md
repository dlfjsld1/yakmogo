# 💊 약손 (Yakmogo) - 투약 관리 및 알림 시스템 (Backend)

가족들의 투약 관리를 위해 만들어진 토이 프로젝트입니다.  
사용자의 투약 스케줄을 관리하고, 텔레그램 봇을 통해 정해진 시간에 알림을 발송합니다.

## 📌 텔레그램 봇 설정 방법

1. 텔레그램 앱에서 **@yakson_bot**을 검색하여 대화방에 입장합니다.
2. **/start** 명령어를 입력합니다.
3. 봇이 자동으로 사용자의 **Chat ID**를 인식하여 메시지로 전송해줍니다.
4. **등록 절차:**
   - **관리자:** 봇이 알려준 ID를 서버 실행 시 환경변수(`TELEGRAM_CHAT_ID`)로 주입하여 시스템 알림을 수신합니다.
   - **일반 사용자:** 봇이 알려준 ID를 복사하여 관리자에게 전달하고, **관리자는 이를 '관리자 페이지'의 사용자 설정 메뉴에 직접 입력**하여 저장합니다.
   
## 📌 인프라 제약 사항 및 아키텍처 결정

* **컨테이너화 생략 (No Docker):** 라즈베리파이 Buster OS의 호환성 문제로 Docker 대신 Native 환경에서 직접 실행하도록 구성했습니다.
* **로컬망 중심 설계:** 관리자 페이지는 홈 네트워크(로컬) 내부에서 수행하며, 알림은 텔레그램 API(Outbound)를 통해 외부로 전송됩니다.
* **보안 주의:** 민감 정보(DB 비번, 토큰 등)는 환경변수 및 실행 파라미터(`-D`)로 주입합니다.

---

## 🚀 배포 및 설치 가이드 (라즈베리파이 기준)

> **사전 요구사항:** MariaDB가 별도로 설치되어 있어야 합니다.

### 1. 통합 빌드 (Build)

**[프론트엔드 빌드]**

```bash
npm install
npm run build
```

👉 `dist` 내부 파일을 백엔드 `src/main/resources/static/` 경로로 복사합니다.

**[백엔드 빌드]**

```bash
./gradlew clean build -x test
```

👉 `build/libs/`에 생성된 `.jar` 파일을 실행 환경으로 이동시킵니다.

### 2. 스냅샷 실행 (작동 테스트용)

```bash
java -DRASPI_IP=localhost \
     -DYAKMOGO_DB_PASSWORD=DB비번 \
     -DTELEGRAM_BOT_TOKEN=텔레그램봇토큰 \
     -DTELEGRAM_CHAT_ID=관리자텔레그램챗아이디 \
     -DADMIN_PASSWORD=관리자비밀번호 \
     -DFRONTEND_URL=http://<현재_로컬_IP>:8080 \
     -jar yakmogo-0.0.7-SNAPSHOT.jar
```

### 3. systemd 서비스 등록 (자동 시작)

#### 3-1. 실행 스크립트 (`start.sh`) 작성

```bash
#!/bin/bash
MY_IP=$(hostname -I | awk '{print $1}')
JAR_PATH="/home/pi/myprojects/yakmogo/yakmogo-0.0.7-SNAPSHOT.jar"

/usr/bin/java \
    -DRASPI_IP=localhost \
    -DYAKMOGO_DB_PASSWORD=DB비번 \
    -DTELEGRAM_BOT_TOKEN=텔레그램봇토큰 \
    -DTELEGRAM_CHAT_ID=관리자텔레그램챗아이디 \
    -DADMIN_PASSWORD=관리자비밀번호 \
    -DFRONTEND_URL=http://${MY_IP}:8080 \
    -jar $JAR_PATH
```

#### 3-2. 서비스 파일 (`yakmogo.service`) 등록

```ini
[Unit]
Description=Yakmogo Spring Boot Server
After=network.target

[Service]
User=pi
WorkingDirectory=/home/pi/myprojects/yakmogo
ExecStart=/home/pi/myprojects/yakmogo/start.sh
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

**✅ 운영 명령어**

```bash
sudo systemctl daemon-reload
sudo systemctl start yakmogo.service
sudo systemctl enable yakmogo.service
# 실시간 로그 확인
journalctl -u yakmogo.service -f
```
