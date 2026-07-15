# 관측·백업·복원 시행착오

## Actuator가 아닌 SPA fallback의 HTTP 200

### 증상

민감 Actuator endpoint가 닫혔는지 검사하는 테스트에서 `/actuator`가 예상한 404 대신 200을 반환했다.

### 원인 확인

MockMvc의 handler를 확인하니 Actuator discovery handler가 아니라 기존 `SpaController#forward()`였다. Yakmogo는 확장자가 없는 알 수 없는 경로를 React `index.html`로 보내므로, endpoint가 노출되지 않아도 HTTP 200이 될 수 있다.

### 해결

- `/actuator/health`는 Actuator handler와 `{"status":"UP"}`을 검사한다.
- `/actuator/env`, `/actuator/configprops`, `/actuator/heapdump`는 HTTP status만 보지 않고 `SpaController`가 처리하는지 검사한다.
- health 상세 component는 응답에 존재하지 않는지 검사한다.

교훈은 HTTP status만으로 endpoint 노출 여부를 단정하지 않는 것이다. SPA fallback이 있는 애플리케이션에서는 실제 handler와 response content type을 함께 확인해야 한다.

## 보관 개수 정리 순서

가장 오래된 백업을 먼저 지우고 새 dump를 만들면 새 백업 실패 시 복원 지점만 줄어든다. 따라서 `backup.sh`는 다음 순서를 고정한다.

1. 임시 파일에 dump
2. gzip 무결성 검사
3. 최종 이름으로 이동
4. SHA-256 생성과 재검증
5. 검증된 dump가 3개를 초과한 경우에만 가장 오래된 dump와 checksum 삭제

CI는 오래된 가짜 백업 3개를 준비한 뒤 실제 dump를 추가해 정확히 3개가 남고 가장 오래된 한 쌍만 삭제되는지 검사한다.

## 정기 백업과 update 백업 분리

초기 portable 구현은 수동 백업과 update 직전 백업을 같은 기본 폴더에 저장했다. 보관 개수 3개를 적용하면 update가 잦을 때 월간 복원 지점이 밀려날 수 있다.

정기 백업은 `backups/scheduled`, update 직전 백업은 `backups/update`로 분리했다. 각 폴더가 독립적으로 최신 3개를 보관한다.

## 운영 환경 변경 경계

Timer 설치 스크립트는 저장소와 release package에 포함하되 사용자 승인 뒤에만 Pi에서 실행했다. Uptime Kuma 설명 입력과 실제 복원 리허설도 같은 승인 뒤에 수행했다. 운영 8080·운영 DB를 기준선과 사후 상태에서 대조해 변경되지 않았음을 확인했다.

## Pi 복원 리허설의 두 가지 점검 실수

첫 접속 시도는 로컬에서 SSH 비밀번호 문자를 잘못 조립해 인증 전에 실패했다. Pi 명령은 실행되지 않았다. 값을 로그에 직접 쓰지 않고 기존 승인된 연결 방식으로 다시 구성해 접속했다.

최종 DB 행 수 점검에서는 container 이름을 `yakmogo-db`로 가정해 명령 결과가 비었다. `docker ps`에서 실제 Compose container 이름을 확인한 뒤 재실행했다. 이때 `docker inspect`의 환경변수 전체를 출력하지 않고 변수 이름만 확인해 비밀값 노출을 피했다.

리허설은 현재 enhancement volume이 아니라 별도 project, 포트 18082와 임시 volume을 사용했다. 복원 후 health, Flyway V1·V2, 핵심 table row 수와 FK orphan 0건을 확인하고 container·volume·directory를 모두 제거했다.
