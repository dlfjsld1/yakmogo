# 약모고 (Yakmogo)

약모고는 가족의 약 복용 일정과 복약 상태를 관리하고, Telegram으로 복약 알림을 전달하는 개인용 서비스입니다. Spring Boot 백엔드와 React 관리자 웹, Yakmogo 전용 MariaDB로 구성되며 64비트 Linux에서 Docker Compose로 운영할 수 있습니다.

## 빠른 설치

새 서버에는 **Docker Engine과 Docker Compose plugin만** 설치되어 있으면 됩니다. Java, Node.js와 MariaDB를 별도로 설치할 필요는 없습니다.

배포용 `yakmogo-<version>-portable.tar.gz`와 같은 이름의 `.sha256` 파일을 서버로 옮긴 뒤 실행합니다.

```bash
sha256sum --check yakmogo-<version>-portable.tar.gz.sha256
tar -xzf yakmogo-<version>-portable.tar.gz
cd yakmogo-portable
./setup.sh
```

`setup.sh`는 관리자 비밀번호와 접속 URL만 확인하고 다음 작업을 한 번에 처리합니다.

- Yakmogo 앱과 전용 MariaDB image 설치
- DB 비밀번호와 인증 secret 자동 생성
- 앱·DB container 시작과 health 확인
- 매월 1일 03:35(한국 시간) 자동 DB 백업 등록
- 검증된 백업을 폴더별 최신 3개까지 보관

기존 약모고 DB를 이식하면서 설치할 때는 SQL dump를 함께 지정합니다.

```bash
./setup.sh /안전한/경로/yakmogo-db.sql.gz
```

Telegram token과 Chat ID는 자동으로 알 수 없기 때문에 신규 설치에서는 알림이 기본 비활성입니다. 설치 후 보호된 `.env`에 값을 입력하고 명시적으로 활성화해야 합니다. 자세한 설치·업데이트·복원 방법은 [휴대형 설치 안내](deploy/portable/README.md)와 [운영 런북](docs/runbooks/yakmogo-operations.md)을 참고하세요.

## 개발 배경과 방식

초기 버전은 개발자가 직접 설계하고 구현했습니다. 실제 가족이 사용하면서 Telegram 푸시만으로는 복약 알림을 명확하게 인지하기 어렵다는 한계를 확인했고, 향후 Kotlin Android 앱으로 알림 경험을 확장할 수 있도록 기존 구조 전반을 고도화했습니다. 이 과정에서 중복 발송 방지, 전달 결과 기록, 재시도와 복약 상태 전이의 일관성을 기술적 신뢰성 과제로 함께 개선했습니다. Android 앱 자체와 Device, Pairing, FCM 기능은 이번 버전에 포함하지 않았으며 별도의 2차 개발 단계로 분리했습니다.

고도화 버전은 AI 코딩 도구를 적극 활용하는 **바이브 코딩(vibe coding)** 방식으로 개발했습니다. AI가 생성한 결과를 그대로 배포한 것이 아니라, 개발자가 목표와 설계 방향을 결정하고 기능별 브랜치에서 코드 검토, 자동 테스트, 문서화와 실제 MariaDB·ARM64 Linux 환경 검증을 거쳐 통합했습니다. Raspberry Pi는 지원 대상을 제한하는 전제가 아니라 ARM64 운영 인수시험 환경으로 사용했습니다.

## 주요 고도화 내용

- 서명된 Telegram 로그인 proof와 만료되는 access token을 사용하도록 인증 구조 개선
- 인증 주체와 허용 사용자 범위를 서비스 계층에서 일관되게 처리하도록 인가 경계 정리
- REST와 Telegram callback의 복약 완료 처리를 공통 `IntakeCommandService`로 통합
- 알림 시간 정책, 전달 결과와 재시도를 분리하고 중복 발송 방지 기록 추가
- Flyway V1·V2와 JPA `ddl-auto=validate`를 이용한 재현 가능한 DB migration 도입
- 약품 일정 생성·수정·삭제 화면과 입력 검증, 사용자 흐름 개선
- 백엔드 통합 테스트와 프런트엔드 Vitest·React Testing Library 검사 확대
- GitHub Actions CI와 실제 배포 대상인 ARM64 container 후보 검증 추가
- 앱과 Yakmogo 전용 MariaDB를 하나의 Compose project로 묶은 간편 설치 제공
- 제한된 health endpoint, 월간 논리 백업, checksum 검증과 update rollback 절차 추가
- Android가 추가되어도 Telegram 전용 로직에 묶이지 않도록 상태 전이·인증·알림 경계 정리

후속 Android 계획은 [Android 2차 개발 로드맵](docs/roadmaps/android-phase-2.md)에 정리되어 있습니다.

## 구성

- `yakmogo-app`: Spring Boot API와 빌드된 React 관리자 웹을 함께 제공
- `yakmogo-mariadb`: 약모고 전용 MariaDB. 호스트에 3306 포트를 공개하지 않음
- `${COMPOSE_PROJECT_NAME}-mariadb-data`: DB 영속 volume
- `.env`: DB 비밀번호, 관리자 비밀번호, 인증 secret과 Telegram 설정. Git에 포함하지 않음
- `backups/scheduled`: 정기 백업
- `backups/update`: 업데이트 직전 백업

애플리케이션의 `/actuator/health`는 앱과 DB 연결을 합친 상태만 `UP` 또는 `DOWN`으로 반환하며 환경변수나 component 상세 정보는 공개하지 않습니다.

## Telegram 설정

1. Telegram에서 `@yakson_bot`을 검색하고 `/start`를 입력합니다.
2. 봇이 알려준 Chat ID를 확인합니다.
3. 일반 사용자의 Chat ID는 관리자 웹의 사용자 설정에서 등록합니다.
4. 서버 관리용 token과 Chat ID는 설치 폴더의 `.env`에만 기록합니다.
5. `TELEGRAM_BOT_ENABLED=true`, `SCHEDULING_ENABLED=true`로 설정한 뒤 앱 container를 다시 만듭니다.

비밀번호, bot token, Chat ID, SQL dump는 Git에 commit하지 않습니다.

## 개발과 검증

백엔드는 Java 21과 Gradle을 사용합니다.

```bash
./gradlew clean test build
```

웹을 포함한 ARM64 설치 image는 웹 `dist`를 준비한 뒤 빌드합니다.

```powershell
.\scripts\release\build-image.ps1 -Version 0.0.7 -WebDist ..\yakmogo-web\dist
```

생성 결과와 배포 형식은 [휴대형 설치 안내](deploy/portable/README.md)의 “개발 PC에서 설치 묶음 만들기”를 따릅니다.

## 문서

- [시행착오 및 트러블슈팅](docs/troubleshooting/README.md)
- [운영·백업·복원 런북](docs/runbooks/yakmogo-operations.md)
- [CI/CD 트러블슈팅](docs/troubleshooting/ci-cd.md)
- [알림 신뢰성 트러블슈팅](docs/troubleshooting/notification-reliability.md)
- [Android 2차 개발 로드맵](docs/roadmaps/android-phase-2.md)
