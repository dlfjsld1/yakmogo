# Goal 10 실행 프롬프트: 전체 검증과 main 승격

이 작업을 Goal로 생성하고 완료할 때까지 진행한다. [공통 고도화 원칙](common-principles.md)을 모두 적용한다.

## 목표

backend와 web의 최신 `enhancement`를 릴리스 후보로 검증한다. 검증이 모두 성공하면 두 저장소의 기존 `main`을 같은 버전의 이전판 보존 브랜치로 먼저 원격에 보존하고, 확인된 `enhancement`를 `main`으로 승격한다. `main` 승격은 운영 8080 배포나 운영 DB 변경을 의미하지 않는다.

## 전체 검증

- backend와 web의 최신 `enhancement`와 원격 정합성을 확인한다.
- 전체 lint, 단위 테스트, 통합 테스트, E2E, production build를 실행한다.
- Flyway migration과 JPA validation을 검증한다.
- DB 백업과 복원 절차를 다시 검증한다.
- 깨끗한 격리 Docker 환경에서 설치 패키지와 `.env`만으로 `install.sh` 신규 설치를 검증한다.
- versioned ARM64 image 생성, checksum, `update.sh` 정상 교체와 의도적 실패 rollback을 최종 검증한다.
- Pi self-hosted runner와 자동 CD가 없어도 기본 설치·업데이트·복구가 완결되는지 확인한다.
- 인증, 사용자, 보호자, 약, 복용 처리, 알림 흐름을 점검한다.
- 8081에서 최종 사용자 인수 테스트를 수행한다.
- 8080과 운영 DB가 변경되지 않았는지 확인한다.
- `main` 대비 커밋, 파일, DB, 환경변수, 배포 설정 변경을 정리한다.
- 알려진 이슈, 보안 위험, 운영 제한, 후속 과제를 작성한다.
- 릴리스 tag 후보와 코드·JAR·DB 롤백 지점을 제안한다.

## 기존 main 버전 보존과 고도화본 승격

- backend와 web의 `origin/main`을 다시 fetch하고 Goal 10 검증을 시작한 기준 commit과 같은지 확인한다. 검증 중 `main`이 바뀌었으면 병합하지 말고 변경분을 다시 검토한다.
- 현재 전체 시스템의 이전판 보존 브랜치 후보는 `archive/v0.0.7-pre-enhancement`다. backend `0.0.7-SNAPSHOT`을 기준으로 한 이름이며, Goal 10 실행 시 실제 버전과 원격 이름 충돌을 다시 확인한다.
- 두 저장소에서 보존 브랜치가 각각 승격 직전 `origin/main` commit을 정확히 가리키게 만든 뒤 원격에 push한다.
- 두 원격 보존 브랜치의 commit SHA를 기록하고 실제 이전 `main` SHA와 일치하는지 확인하기 전에는 어느 저장소의 `main`도 변경하지 않는다.
- 보존 확인 후 backend와 web의 검증된 `enhancement`를 각각 `main`에 `--no-ff`로 병합하고 원격에 push한다. 보호 브랜치 정책이 직접 push를 막으면 같은 변경으로 PR을 만들고 검증을 통과시킨 뒤 병합한다.
- `main` CI가 모두 성공하고 두 `main`이 의도한 enhancement commit을 포함하는지 확인한다.
- 실패 시 force push, reset 또는 기존 보존 브랜치 덮어쓰기를 하지 않는다. 일부 저장소만 승격된 경우 즉시 중단하고 현황을 보고한 뒤 보존 브랜치를 기준으로 비파괴적 복구 계획을 세운다.

## Android 2차 로드맵

1차 고도화 결과를 바탕으로 별도의 Android 2차 로드맵 문서를 작성한다.

- Android 앱 사용자 범위와 핵심 화면
- Android용 백엔드 인증 경계
- Device와 Pairing이 실제로 필요한지에 대한 판단
- FCM 전달 구조와 Telegram 공존 방식
- 서버 장애·오프라인·동기화·충돌 정책
- 필요한 API 변경 후보
- Kotlin 프로젝트 구조와 단계별 Goal
- 기존 React 관리자 웹과 Telegram 기능의 공존 전략

이번 Goal에서는 Android 프로젝트, 코드, 테이블, API, Device, Pairing, FCM 설정을 구현하지 않는다.

## 릴리스 승인 경계

- 이 프롬프트에 따라 Goal 10의 자동·인수 검증이 모두 성공하면 기존 `main` 보존 브랜치 생성과 고도화본의 `main` 승격까지 수행한다.
- 릴리스 tag 생성, 운영 DB migration, 운영 8080 배포와 운영 서비스 재시작은 수행하지 않는다.
- 최종 보고서에 자동 검증 결과, 인수 테스트, 보존 브랜치와 SHA, 새 `main` SHA, 위험, 운영 릴리스·롤백 계획을 제시한다.
- 운영 배포는 `main` 승격과 분리하고 별도로 명시적인 승인을 요청한다.

## 완료 조건

- 모든 자동 검증 성공
- 8081 최종 인수 테스트 성공
- migration·백업·복원 검증 성공
- 단일 명령 신규 설치와 수동 image 업데이트·rollback 검증 성공
- 알려진 이슈와 잔여 위험 목록 작성
- 릴리스 및 롤백 계획 작성
- Android 2차 로드맵 작성
- Goal 1~10 문서와 troubleshooting 인덱스 최종 정리
- backend와 web의 이전 `main`을 버전 보존 브랜치로 원격 보존
- 검증된 backend와 web `enhancement`를 `main`으로 승격하고 `main` CI 성공
- 운영 8080·운영 DB 무변경 확인과 별도 운영 배포 승인 요청
