# Goal 1 실행 프롬프트: 품질 개선 통합 및 기본 CI

[공통 고도화 원칙](common-principles.md)을 모두 적용한다.

## 목표

기존 backend와 web의 `feature/quality-hardening` 변경을 검증한 뒤 `enhancement`에 통합하고, 고도화 서버 8081에 `enhancement` 기준 빌드를 배포한다. 기본 CI를 가능한 이른 단계부터 feature 브랜치에 적용한다.

## 브랜치 예외

Goal 1은 이미 존재하는 `feature/quality-hardening`을 사용하며 새 feature 브랜치를 만들지 않는다. `main`에는 commit, merge, push하지 않는다.

## 작업

- backend에서 test와 `bootJar`를 실행한다.
- web에서 lint, 존재하는 경우 test, production build를 실행한다.
- `feature/**`, `fix/**`, `enhancement`에 기본 CI를 적용한다.
- feature 브랜치는 검사만 수행하고 자동 배포하지 않는다.
- 두 저장소에서 `feature/quality-hardening`을 `enhancement`에 `--no-ff`로 병합한다.
- feature와 enhancement를 원격에 push한다.
- 8081은 `yakmogo_enhancement`를 사용한다.
- 8081의 Telegram bot과 scheduler 비활성 상태를 유지한다.
- 운영 8080과 운영 DB를 변경하지 않는다.

## 완료 조건

- 두 저장소의 `enhancement`에 품질 개선 반영
- backend test/bootJar 성공
- web lint/build와 존재하는 test 성공
- feature 및 enhancement CI 성공
- 8080과 8081 HTTP 200
- 8081 별도 DB, bot·scheduler 비활성 확인
- 커밋, 배포 해시, 백업, 검증 결과 문서화

## 상태

완료. 실제 기록은 [Goal 1 문서](../goal-01-quality-hardening.md)를 참고한다.
