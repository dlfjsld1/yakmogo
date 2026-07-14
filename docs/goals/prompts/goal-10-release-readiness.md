# Goal 10 실행 프롬프트: 전체 검증과 main 병합 준비

이 작업을 Goal로 생성하고 완료할 때까지 진행한다. [공통 고도화 원칙](common-principles.md)을 모두 적용한다.

## 목표

backend와 web의 최신 `enhancement`를 릴리스 후보로 검증하고 `main` 병합 가능 상태까지 준비한다. 사용자 승인 전에는 `main` 병합과 운영 8080 배포를 실행하지 않는다.

## 전체 검증

- backend와 web의 최신 `enhancement`와 원격 정합성을 확인한다.
- 전체 lint, 단위 테스트, 통합 테스트, E2E, production build를 실행한다.
- Flyway migration과 JPA validation을 검증한다.
- DB 백업과 복원 절차를 다시 검증한다.
- 인증, 사용자, 보호자, 약, 복용 처리, 알림 흐름을 점검한다.
- 8081에서 최종 사용자 인수 테스트를 수행한다.
- 8080과 운영 DB가 변경되지 않았는지 확인한다.
- `main` 대비 커밋, 파일, DB, 환경변수, 배포 설정 변경을 정리한다.
- 알려진 이슈, 보안 위험, 운영 제한, 후속 과제를 작성한다.
- 릴리스 tag 후보와 코드·JAR·DB 롤백 지점을 제안한다.

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

- `main` merge, tag 생성, 운영 DB migration, 8080 배포 명령을 실행하지 않는다.
- 최종 보고서에 자동 검증 결과, 인수 테스트, 위험, 릴리스·롤백 계획을 제시한다.
- 사용자에게 명시적으로 `main` 병합과 운영 배포 승인을 요청한다.

## 완료 조건

- 모든 자동 검증 성공
- 8081 최종 인수 테스트 성공
- migration·백업·복원 검증 성공
- 알려진 이슈와 잔여 위험 목록 작성
- 릴리스 및 롤백 계획 작성
- Android 2차 로드맵 작성
- Goal 1~10 문서와 troubleshooting 인덱스 최종 정리
- 사용자에게 `main` 병합 승인 요청
