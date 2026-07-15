# Goal 8 실행 프롬프트: CI/CD와 8081 자동 배포·롤백

이 작업을 Goal로 생성하고 완료할 때까지 진행한다. [공통 고도화 원칙](common-principles.md)을 모두 적용한다.

## 목표

Goal 1에서 도입한 기본 CI를 확장해 `enhancement`의 8081 자동 배포와 실패 시 자동 롤백을 구현한다. `main`과 운영 8080은 자동 변경되지 않게 한다.

## 브랜치

필요한 저장소의 최신 `enhancement`에서 `feature/ci-cd`를 만든다.

## 작업

- backend test/bootJar와 web lint/test/build CI를 유지·정리한다.
- feature 브랜치는 검사만 수행한다.
- `enhancement` 병합 시에만 고도화 통합 JAR을 생성한다.
- React `dist`가 포함된 Spring Boot JAR 패키징을 자동화한다.
- 배포 전 기존 JAR과 필요한 unit·설정을 백업한다.
- 로컬 산출물과 원격 파일의 SHA-256을 비교한다.
- `yakmogo-enhancement.service`만 재시작한다.
- systemd 상태와 별개로 최대 60초 동안 8081 HTTP 준비 상태를 polling한다.
- `/`, 예상 JS bundle, 보호 API 응답을 확인한다.
- health check 실패 시 이전 JAR·unit·설정으로 자동 복구한다.
- `main`과 8080 배포는 수동 승인 없이 실행하지 않는다.
- SSH key와 비밀값은 저장소가 아닌 CI secret으로 관리한다.
- CI 로그에서 비밀값이 마스킹되는지 확인한다.
- 배포 이력, 백업 위치, 롤백 결과를 보존한다.

## 실패 검증

운영에 영향을 주지 않는 8081에서 의도적으로 실패하는 산출물 또는 health check 조건을 사용해 자동 롤백을 실제 검증한다. 테스트가 끝나면 정상 enhancement 산출물로 복구한다.

## 완료 조건

- feature 검사 파이프라인 성공
- enhancement 자동 배포 성공
- 체크섬 일치 확인
- 8081 readiness 확인
- 실패 시 자동 롤백 시나리오 성공
- 8080과 운영 DB 무변경 확인
- CI secret과 로그 민감정보 비노출
- CI/CD troubleshooting과 배포 런북 갱신
