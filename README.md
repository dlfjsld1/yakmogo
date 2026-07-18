# 약모고 (Yakmogo)

약모고는 가족의 약 복용 일정과 복약 상태를 관리하고 Telegram으로 정해진 시간에 알림을 보내는 개인용 복약 관리 서비스입니다.

관리자는 웹에서 사용자와 약 일정을 관리하고, 복용자는 Telegram 알림에서 약 정보를 확인한 뒤 복약 완료를 기록할 수 있습니다. Spring Boot 백엔드, React 관리자 웹과 전용 MariaDB로 구성됩니다.

## 주요 기능

- 사용자와 가족 알림 수신자 관리
- 약 이름, 복약 시작일, 시간과 반복 주기 관리
- 매일, 요일별, 일정 간격 복약 일정 지원
- 정해진 시각의 Telegram 복약 알림
- 알림에서 약 상세 확인과 복약 완료 처리
- 완료·미복용 상태와 실제 복약 시각 기록
- 알림 전달 결과 기록, 중복 발송 방지와 단계별 재알림
- 사용자별 접근 권한과 관리자 인증
- 웹에서 사용자·약 일정 추가, 수정과 삭제

## 빠른 설치

현재 표준 설치 대상은 systemd를 사용하는 ARM64 GNU/Linux입니다. 약모고를 실행할 컴퓨터에서 소스를 내려받아 Docker image와 설치 묶음을 만든 뒤, 빌드 결과와 분리된 운영 폴더에 설치합니다.

### 1. 필수 프로그램

빌드와 실행에 다음 프로그램이 필요합니다.

- Git
- JDK 21 (`java`뿐 아니라 `javac`가 필요합니다)
- Node.js 22와 npm
- Docker Engine
- Docker Compose plugin
- Docker Buildx
- Bash, `sudo`, `curl`, GNU `tar`·`coreutils`
- `awk`, `sed`, `grep`, `util-linux`, `iproute2`

일반 사용자 계정에서 `docker compose version`과 `docker buildx version`이 `sudo` 없이 실행되어야 합니다. 이 계정에는 월간 backup timer를 등록할 `sudo` 권한도 있어야 합니다. 최초 빌드와 설치에는 Gradle·npm 의존성과 MariaDB image를 내려받을 인터넷 연결이 필요합니다.

`setup.sh`는 일반 사용자로 실행해야 하며 `sudo ./setup.sh`로 실행하면 안 됩니다. 스크립트가 필요한 systemd 설정에만 별도로 `sudo`를 사용합니다.

### 2. 소스 내려받기

backend와 관리자 web 저장소를 같은 폴더 아래에 clone합니다.

```bash
mkdir -p yakmogo-build
cd yakmogo-build
git clone https://github.com/dlfjsld1/yakmogo.git yakmogo-backend
git clone https://github.com/dlfjsld1/yakmogo-web.git yakmogo-web
```

### 3. 빌드

관리자 web을 먼저 빌드한 뒤 그 결과를 포함한 backend, ARM64 Docker image와 배포 패키지를 만듭니다. 빌드 스크립트는 clone한 backend 저장소의 `scripts/release/build-image.sh`에 있으며, Windows용 wrapper는 `scripts/release/build-image.ps1`에 있습니다. 아래의 `0.0.7`은 만들려는 버전으로 바꿀 수 있습니다.

```bash
cd yakmogo-web
npm ci
npm run build
cd ../yakmogo-backend
./scripts/release/build-image.sh 0.0.7 ../yakmogo-web/dist
```

### 4. 설치

빌드가 끝나면 `build/yakmogo-0.0.7-portable.tar.gz`와 checksum 파일이 생성됩니다. checksum을 확인하고, 다음 빌드 때 삭제되는 `build/portable`이 아니라 별도의 운영 폴더에 압축을 풉니다.

```bash
cd build
sha256sum --check yakmogo-0.0.7-portable.tar.gz.sha256
mkdir -p "$HOME/yakmogo"
tar -xzf yakmogo-0.0.7-portable.tar.gz -C "$HOME/yakmogo" --strip-components=1
cd "$HOME/yakmogo"
./setup.sh
```

설치 과정에서 접속 URL과 관리자 비밀번호를 입력하면 애플리케이션과 전용 MariaDB container가 시작됩니다. DB 비밀번호와 인증 secret은 자동으로 생성되고, health 확인과 월간 DB 백업도 함께 설정됩니다. 설치가 끝나면 입력한 URL로 관리자 web에 접속합니다.

기존 DB를 복원하며 설치할 경우에는 위의 `./setup.sh` 대신 압축된 SQL dump 경로를 함께 전달합니다.

```bash
./setup.sh /안전한/경로/yakmogo-db.sql.gz
```

설치 상태는 다음 명령으로 확인합니다.

```bash
curl http://127.0.0.1:8080/actuator/health
docker compose --env-file .env -f compose.yml ps
```

정상이면 health 응답은 `{"status":"UP"}`이고 app과 MariaDB container가 실행 중이어야 합니다.

Telegram 알림은 새 설치에서 기본적으로 꺼져 있습니다. 사용하려면 Telegram의 `@BotFather`에서 봇을 만들고 발급된 token과 `@`를 제외한 봇 사용자명을 설치 폴더의 `.env`에 입력합니다.

```dotenv
TELEGRAM_BOT_ENABLED=true
SCHEDULING_ENABLED=true
TELEGRAM_BOT_TOKEN=BotFather가_발급한_token
TELEGRAM_BOT_USERNAME=봇_사용자명
```

앱 container를 다시 만든 뒤 Telegram에서 봇에게 `/start`를 보내면 Chat ID를 확인할 수 있습니다. 이 Chat ID를 관리자 web에서 해당 사용자의 알림 수신자로 등록합니다.

```bash
docker compose --env-file .env -f compose.yml up -d --force-recreate yakmogo-app
```

비밀번호, bot token, Chat ID와 SQL dump는 Git에 commit하지 않습니다.

업데이트, 백업과 복원 방법은 [ARM64 Docker 배포 패키지 안내](deploy/portable/README.md)와 [운영 런북](docs/runbooks/yakmogo-operations.md)을 참고하세요.

## 고도화 배경과 Docker 전환

초기 약모고 v0.0.7은 개발자가 직접 설계하고 손코딩했으며, 고도화 직전 코드는 `archive/v0.0.7-pre-enhancement` 브랜치에 보존되어 있습니다. 이 버전을 기준으로 `enhancement` 브랜치에서 AI 코딩 도구를 적극 활용하는 바이브 코딩 방식으로 인증·인가, 복약 상태 전이, 알림 중복 방지와 재시도, DB migration과 자동 테스트를 전반적으로 고도화했습니다.

초기 운영 구조는 Spring Boot JAR를 systemd 서비스로 실행하고 호스트에 설치된 MariaDB를 사용하는 방식이었습니다. 실제 가족이 계속 사용하는 서비스를 더 안전하고 안정적으로 운영하는 것이 고도화의 목적이었습니다.

고도화 과정에서 배포 방식도 Docker Compose로 전환했습니다. Spring Boot 애플리케이션과 빌드된 React 웹을 하나의 app image로 만들고, 전용 MariaDB와 함께 하나의 Compose project로 묶었습니다. 그 결과 새 서버에서 Java와 MariaDB 버전을 따로 맞출 필요가 없어졌고, 동일한 release artifact로 설치와 업데이트를 반복할 수 있게 됐습니다.

Docker 전환은 기존 운영 환경을 바로 교체하지 않고 별도 환경과 ARM64 container에서 먼저 검증한 뒤 진행했습니다. 현재 배포 묶음은 다음 운영 기능을 함께 제공합니다.

- 앱과 전용 MariaDB의 일괄 설치
- Flyway를 이용한 재현 가능한 schema migration
- container와 DB 연결을 확인하는 health check
- 월간 백업과 업데이트 직전 백업 분리
- checksum 검증과 readiness 실패 시 이전 image rollback
- GitHub Actions 기반 테스트와 ARM64 release 후보 검증

고도화 작업은 기능별 브랜치에서 진행해 `enhancement`에 통합하고 검증한 뒤 `main`에 병합했습니다. AI가 생성한 결과를 그대로 배포한 것이 아니라 개발자가 목표와 설계 방향을 결정하고 코드 검토, 자동 테스트와 실제 운영 환경의 최종 검증을 수행했습니다.

## 개발

백엔드는 Java 21과 Gradle을 사용합니다.

```bash
./gradlew clean test build
```

관리자 웹은 별도 `yakmogo-web` 저장소에서 개발합니다. 웹 `dist`를 포함한 ARM64 Docker image와 배포 패키지는 release script로 생성합니다.

```powershell
.\scripts\release\build-image.ps1 -Version <VERSION> -WebDist ..\yakmogo-web\dist
```

CI는 백엔드 테스트, MariaDB integration test, 웹 build와 ARM64 Docker 설치·백업·복원·업데이트·rollback 시나리오를 검증합니다.

## 문서

- [ARM64 Docker 배포 패키지 안내](deploy/portable/README.md)
- [운영·백업·복원 런북](docs/runbooks/yakmogo-operations.md)
- [시행착오 및 트러블슈팅](docs/troubleshooting/README.md)
- [CI/CD 트러블슈팅](docs/troubleshooting/ci-cd.md)
- [알림 신뢰성 트러블슈팅](docs/troubleshooting/notification-reliability.md)
