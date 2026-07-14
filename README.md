# 💊 약손 (Yakmogo) - 투약 관리 및 알림 시스템 (Backend)

가족들의 투약 관리를 위해 만들어진 토이 프로젝트입니다.  
사용자의 투약 스케줄을 관리하고, 텔레그램 봇을 통해 정해진 시간에 알림을 발송합니다.

## 📚 개발 및 문제 해결 문서

- [Goal별 설계·변경·검증 기록](docs/goals/README.md)
- [Goal 3: 런타임 설정과 비밀정보 분리](docs/goals/goal-03-runtime-config.md)
- [시행착오 및 트러블슈팅 인덱스](docs/troubleshooting/README.md)
- [인증과 인가 트러블슈팅](docs/troubleshooting/authentication.md)
- [배포와 런타임 트러블슈팅](docs/troubleshooting/deployment-and-runtime.md)
- [CI/CD 트러블슈팅](docs/troubleshooting/ci-cd.md)
- [알림 전달과 재시도 트러블슈팅](docs/troubleshooting/notification-reliability.md)

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
* **보안 주의:** 민감 정보(DB 비번, 토큰 등)는 Git에 저장하거나 Java `-D` 인자로 전달하지 않습니다. systemd가 읽는 권한 `600`의 `EnvironmentFile`로 주입합니다.

## 📌 실행 환경 요구사항

- **Java 21 Runtime** (OpenJDK 21 권장)
  - 본 프로젝트는 **Build-Jdk-Spec 21** 기준으로 빌드되었습니다.
  - Java 17 이하에서는 실행되지 않을 수 있습니다.
- **MariaDB**
- **Node.js + npm** (프론트엔드 빌드 시)
---

## 📌 MariaDB 초기 설정

약모고는 MariaDB의 `yakmogo` 데이터베이스와 `yakmogo_user` 계정을 사용합니다.

```sql
CREATE DATABASE yakmogo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER 'yakmogo_user'@'localhost' IDENTIFIED BY 'DB비밀번호';

GRANT ALL PRIVILEGES ON yakmogo.* TO 'yakmogo_user'@'localhost';

FLUSH PRIVILEGES;
```

`DB비밀번호`는 해당 실행 환경의 보호된 `EnvironmentFile`에 기록하는
`YAKMOGO_DB_PASSWORD` 값과 동일해야 합니다.

JPA 설정은 `ddl-auto: update`이므로, 데이터베이스와 계정만 준비되어 있으면 필요한 테이블은 애플리케이션 실행 시 자동으로 생성/갱신됩니다.

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

### 2. 고도화(8081) systemd 설정

고도화 환경은 `SPRING_PROFILES_ACTIVE=enhancement`를 사용한다. 이 프로필은 포트
`8081`, DB `yakmogo_enhancement`, Telegram bot 비활성, scheduler 비활성을 명시한다.

- unit: `deploy/systemd/yakmogo-enhancement.service`
- 비밀값 없는 예제: `deploy/systemd/yakmogo-enhancement.env.example`
- 실행 스크립트: `deploy/systemd/start-enhancement.sh`

예제 파일을 `/etc/yakmogo/yakmogo-enhancement.env`로 복사한 뒤 실제 값을 서버에서만
입력하고 소유권 `root:root`, 권한 `600`을 적용한다. `start.sh`에는 비밀값을 넣지
않으며 Java 명령은 `java -jar ...`만 남긴다.

운영 8080은 고도화 완료 전 변경하지 않는다. 운영용 unit과 환경파일 전환은 별도
점검·롤백 계획 아래 수행한다. 자세한 설치·검증·복구 절차는
[Goal 3 문서](docs/goals/goal-03-runtime-config.md)를 따른다.

**✅ 운영 명령어**

```bash
sudo systemctl daemon-reload
sudo systemctl start yakmogo-enhancement.service
sudo systemctl enable yakmogo-enhancement.service
# 실시간 로그 확인
journalctl -u yakmogo-enhancement.service -f
```
