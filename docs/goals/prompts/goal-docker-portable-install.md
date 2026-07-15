# Docker 후속 Goal 실행 프롬프트: 단일 명령 설치와 수동 업데이트

이 작업을 Goal로 생성하고 완료할 때까지 진행한다. [공통 고도화 원칙](common-principles.md)을 모두 적용한다.

## 목표

Yakmogo의 기본 배포 단위를 자동 CD가 아니라 독립 설치 패키지로 단순화한다. 새 ARM64 시스템에서는 비밀값을 `.env`에 입력한 뒤 `./install.sh` 한 번으로 앱과 Yakmogo 전용 MariaDB를 시작할 수 있어야 한다. 새 코드는 한 명령으로 image tar를 만들고 `./update.sh <image.tar>`로 검증·교체·롤백할 수 있어야 한다.

## 브랜치와 현재 기준선

- 아직 병합하지 않은 기존 `feature/docker-enhancement`에서 계속 작업한다. 새 feature 브랜치를 만들지 않는다.
- 현재 8081 `yakmogo-enhancement` container와 host `yakmogo_enhancement` DB는 승인 전까지 변경하지 않는다.
- 운영 8080, 운영 DB `yakmogo`, `main`은 변경하지 않는다.
- Pi 전체 복구 문서는 사용자가 별도로 요청하지 않는 한 읽거나 수정하지 않는다.

## 확정한 구조

Spring Boot와 MariaDB를 한 container에 넣지 않는다. 하나의 Compose project 안에서 별도 container로 실행한다.

```text
Compose project: yakmogo 또는 yakmogo-enhancement
service: yakmogo-app
service: yakmogo-mariadb
volume: yakmogo-mariadb-data
network: yakmogo-internal
```

- 이름만 보고 Yakmogo 전용 DB·volume·network임을 알 수 있게 한다.
- 앱은 Docker DNS `yakmogo-mariadb:3306`으로 DB에 연결한다.
- MariaDB 3306은 host에 publish하지 않는다.
- 외부에는 앱 HTTP port만 publish한다.
- 앱 image에는 DB 데이터, 비밀번호, token과 `.env`를 넣지 않는다.
- MariaDB 데이터는 named volume에 저장하고 논리 SQL dump를 복구 기준으로 사용한다.
- 현재 V1·V2 Flyway migration은 빈 MariaDB에서 전체 현재 스키마를 만들 수 있음을 다시 자동 검증한다. `ddl-auto=update`에 의존하지 않는다.

## 설치 패키지

최소 구성만 둔다.

```text
compose.yml
.env.example
install.sh
update.sh
backup.sh
restore.sh
images/yakmogo-<version>-linux-arm64.tar
images/*.sha256
release-manifest.txt
README.md
```

`.env.example`은 비밀값 없는 설명과 안전한 기본값만 제공한다. 사용자는 `.env`를 만든 뒤 다음 한 명령을 실행한다.

```bash
./install.sh
```

install은 Docker/Compose와 필수 환경값 확인, image checksum 검증·load, Compose 기동, MariaDB health, Flyway, 앱 HTTP와 보호 API 확인까지 수행한다. 기존 DB dump가 지정된 경우에만 복원하고, 빈 신규 설치와 기존 데이터 복원을 구분해 출력한다.

## image 생성과 수동 업데이트

- Windows 개발 PC의 진입점은 `build-image.ps1 -Version <version>` 한 명령으로 둔다.
- 기존 shell 검증·빌드 로직을 내부에서 재사용하고 PowerShell에 중복 구현하지 않는다.
- web lint·test·build, backend test·통합 JAR, ARM64 image, SHA-256, manifest를 순서대로 만든다.
- version과 backend/web commit SHA를 image label과 manifest에 기록한다.
- GitHub-hosted CI도 같은 script를 사용해 artifact까지만 만든다.
- Pi self-hosted runner, passwordless deploy sudoers와 자동 CD는 기본 설치·업데이트 경로에서 제거한다.

Pi의 수동 업데이트 진입점은 다음 하나다.

```bash
./update.sh images/yakmogo-<version>-linux-arm64.tar
```

update는 checksum·manifest·architecture 확인, 현재 DB dump, 현재 release 기록, 새 image load, 앱 container만 교체, readiness 검증과 실패 시 직전 image 복원을 수행한다. MariaDB container와 volume은 앱 업데이트 때 재생성하지 않는다.

## 단계와 승인 경계

### 1단계: 저장소와 격리 환경

- Compose, script, CI artifact와 문서를 구현한다.
- 빈 임시 MariaDB container에서 Flyway bootstrap을 검증한다.
- 임시 volume과 가짜 데이터로 install·backup·restore·update rollback을 검증한다.
- Pi, host MariaDB와 현재 8081은 변경하지 않는다.

### 승인 지점: 현재 enhancement DB 이동 전

다음을 보고하고 사용자 승인을 기다린다.

- 최종 container·volume·network 이름
- `.env` 필수값과 보관 권한
- host `yakmogo_enhancement` dump 명령과 예상 크기
- 새 MariaDB container 복원·row 수·핵심 데이터 검증 방법
- 8081 cutover 순서와 예상 중단 시간
- 실패 시 현재 host DB 연결 container로 돌아가는 절차
- self-hosted runner와 자동 CD를 기본 경로에서 제거하는 변경
- 운영 8080과 운영 DB가 영향을 받지 않는 근거

승인 전에는 현재 DB dump 생성, DB container 기동, 8081 Compose 교체, runner 서비스·sudoers 변경을 실행하지 않는다.

### 2단계: 승인 후 8081 실기 검증

- host `yakmogo_enhancement`는 읽기 전용 dump만 생성하고 삭제·변경하지 않는다.
- 별도 volume의 `yakmogo-enhancement-mariadb`에 복원한다.
- 테이블, row 수, 핵심 관계와 Flyway version을 비교한다.
- 앱을 내부 network DB에 연결해 8081 인수 테스트한다.
- install과 update rollback을 실제 검증한다.
- 성공 후에도 기존 host DB와 이전 정상 image를 Goal 완료 전까지 보존한다.

## 제외 범위

- 운영 8080과 운영 DB container 전환
- registry·GHCR 도입
- Pi self-hosted 자동 CD 재구축
- Kubernetes, 범용 installer framework와 여러 DB 지원
- Android 코드·API·테이블
- Pi 홈서버 전체 복구 문서

## 완료 조건

- 깨끗한 ARM64 격리 환경에서 `.env` 작성 후 `./install.sh` 성공
- 앱·MariaDB가 하나의 Compose에서 별도 container로 정상 동작
- DB port host 비공개와 명시적 Yakmogo 이름 확인
- 빈 DB Flyway bootstrap 성공
- SQL dump backup·restore와 핵심 데이터 검증 성공
- `build-image.ps1` 한 명령으로 versioned ARM64 artifact 생성
- `update.sh` 정상 업데이트와 의도적 실패 rollback 성공
- GitHub CI는 test/build/artifact까지만 수행하고 Pi runner 없이 완료
- 운영 8080·운영 DB 무변경
- 설치·이미지 생성·업데이트·백업·복원 한국어 매뉴얼 완성
