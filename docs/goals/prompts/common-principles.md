# 공통 고도화 원칙

## 1차와 2차 고도화 경계

현재 Goal 1~10은 Spring Boot 백엔드와 React 관리자 웹의 1차 고도화다.

Kotlin Android 앱 개발은 1차 고도화가 완료된 뒤 별도의 2차 Goal로 진행한다. 현재 단계에서는 Android 프로젝트, Device, Pairing, FCM, Android 전용 동기화 API, 사용하지 않는 테이블을 구현하지 않는다.

다만 향후 Android 추가를 불필요하게 막는 결합은 만들지 않는다.

- 복용 완료와 상태 전이는 Telegram callback이나 특정 Controller 전용 로직이 아니라 공통 애플리케이션 서비스에서 처리한다.
- 알림 정책과 복용 판단 로직은 Telegram SDK 호출 코드와 분리한다.
- 인증·인가는 React, localStorage, 특정 HTTP 헤더 구현에 직접 종속되지 않게 한다.
- 클라이언트별 표현과 핵심 도메인·권한 규칙을 분리한다.
- 미래 기능을 이유로 현재 사용하지 않는 API, 테이블, 역할 계층, 추상화 프레임워크를 추가하지 않는다.

## 브랜치와 실행 환경

- Goal 9까지 `main`, 운영 8080, 운영 DB `yakmogo`는 변경하지 않는다.
- 최신 `enhancement`에서 해당 Goal의 feature 브랜치를 만든다.
- 단, Goal 1은 기존 `feature/quality-hardening`을 사용하며 새 feature 브랜치를 만들지 않는다.
- 단일 명령 Docker 설치 후속 Goal은 아직 병합하지 않은 기존 `feature/docker-enhancement`에서 계속하며 새 feature 브랜치를 만들지 않는다.
- 개발과 통합 검증은 8081과 `yakmogo_enhancement`에서만 수행한다.
- backend와 web은 별도 Git 저장소임을 유지한다.
- Goal과 직접 관련 없는 문제는 임의로 수정하지 않고 후속 Goal 또는 차단 이슈로 기록한다.
- 검증된 feature만 `enhancement`에 `--no-ff`로 통합한다.
- Goal 9까지는 `main`을 변경하지 않는다. Goal 10에서는 전체 검증 성공 후 backend와 web의 기존 `main`을 같은 버전의 원격 보존 브랜치로 먼저 보존·검증하고, 고도화된 `enhancement`를 `main`으로 승격한다.
- Goal 10의 `main` 승격은 운영 릴리스와 분리한다. 별도 사용자 승인 전에는 운영 8080 배포, 운영 DB migration, 운영 서비스 재시작과 release tag 생성을 실행하지 않는다.

## CI와 검증

- 기본 test, lint, build는 Goal 1부터 모든 feature 브랜치에 적용한다.
- feature 브랜치는 검사만 수행하고 자동 배포하지 않는다.
- Goal 8의 JAR 자동 배포는 역사적 검증 결과로 보존한다. 현재 기본 배포 경로는 GitHub-hosted CI의 test·build·image artifact와 사용자가 실행하는 수동 `update.sh`이며 Pi self-hosted runner와 자동 CD를 필수 구성으로 두지 않는다.
- 로컬 테스트, CI, 8081 실제 검증을 구분해 기록한다.
- systemd `active`, TCP listen, HTTP 준비 완료를 같은 상태로 취급하지 않는다.
- 실제 비밀번호, token, Chat ID, 서명 키를 Git·로그·문서·프로세스 출력에 남기지 않는다.

## 설치와 container 원칙

- 설치 단위는 단일 image가 아니라 Compose, `.env.example`, versioned image tar, checksum, install·update·backup·restore script와 매뉴얼로 구성된 Yakmogo 설치 패키지다.
- Spring Boot와 MariaDB는 하나의 Compose project 안의 별도 container로 실행한다. 한 container에 두 프로세스를 넣지 않는다.
- service, volume과 network 이름에 `yakmogo`를 포함하고 MariaDB port는 host에 publish하지 않는다.
- 비밀번호와 DB 데이터는 image에 포함하지 않는다. 비밀값은 Git에서 제외한 `.env`, 데이터는 MariaDB volume, 복구 기준은 SQL dump로 관리한다.
- 앱 image update는 MariaDB container와 volume을 교체하지 않는다.
- 한 명령 설치·업데이트는 내부 검증과 rollback을 숨기는 진입점이지 checksum, DB backup과 health check를 생략하는 수단이 아니다.

## DB 안전 원칙

- Flyway 작성 전 JPA 엔티티, `yakmogo_enhancement`, 운영 `yakmogo` 읽기 전용 스키마를 비교한다.
- 운영 DB에는 DDL, Flyway 적용, 데이터 수정, 복원 시험을 실행하지 않는다.
- 마이그레이션과 복원은 고도화 DB 복제본 또는 별도 테스트 DB에서 검증한다.

## 상태 전이와 알림 경계

상태 변경 명령은 단일 진입점으로 모은다.

```text
REST controller ─────┐
                     ├─> IntakeCommandService.complete()
Telegram callback ──┘
```

코드 규모상 새 클래스가 과하면 기존 `IntakeLogService` 안의 단일 메서드로 수렴해도 된다. 클래스 생성 자체가 목표는 아니다.

```text
Scheduler → 알림 정책 → Telegram 전달
```

현재는 Telegram 전달만 구현한다. FCM 어댑터나 사용하지 않는 범용 메시징 프레임워크를 미리 만들지 않는다.

## 인증 모델 제한

서비스 계층에서 인증 주체의 유형, 허용 userId 범위, 관리자 여부를 일관되게 조회할 수 있는 최소 표현만 둔다.

```text
AuthenticatedPrincipal
- principalType
- allowedUserIds
- admin
```

사용하지 않는 역할 계층, 범용 권한 프레임워크, Android 전용 주체 유형은 만들지 않는다.

## 문서 완료 조건

각 Goal은 코드만 끝났다고 완료하지 않는다.

- `docs/goals/goal-XX-*.md`를 작성하거나 갱신한다.
- 실제 시행착오는 관련 `docs/troubleshooting/*.md`에 누적한다.
- 실패 원인, 조사 과정, 해결, 검증, 재발 방지를 기록한다.
- 확인되지 않은 내용은 추정으로 표시하고 실제로 없던 장애를 만들지 않는다.
- 커밋, 테스트, CI, 배포 로그를 근거로 연결한다.
- 문서 링크와 민감정보 포함 여부를 검사한다.
- 실제 시행착오가 없으면 `기록할 만한 시행착오 없음`이라고 명시한다.

## Android 2차 로드맵

Goal 10에서는 Android를 구현하지 않는다. 1차 고도화 결과를 기준으로 Android용 백엔드 경계와 Kotlin 앱 개발을 위한 별도 2차 로드맵만 작성한다.
