# API 검증과 복용 상태 전이 트러블슈팅

## 사례 1: 보호자 삭제 200인데 행이 남음

### 상황

보호자 추가·삭제 성공 테스트에서 DELETE API가 200을 반환한 뒤 repository로 실제 행 부재를 확인했다.

### 증상

`guardianRepository.delete(guardian)`가 호출되고 성공 로그도 출력됐지만 `existsById()`는 true였다. Hibernate SQL에는 DELETE가 없었다.

### 원인 확인

테스트 전체가 하나의 transaction과 영속성 context를 사용했다. 삭제 대상 guardian은 여전히 `User.guardians` 컬렉션에 포함돼 있었다. 객체 그래프는 관계가 유지된다고 표현하면서 repository에는 삭제를 요청해 서로 충돌했다.

### 해결

`User.removeGuardian()`으로 부모 컬렉션에서 제거하고 관계에 `orphanRemoval=true`를 적용했다. flush 시 고아가 된 guardian에 DELETE가 발생했다.

### 배운 점

HTTP 200이나 service 메서드 종료만으로 DB 변경을 증명할 수 없다. 쓰기 테스트는 flush를 유발한 뒤 repository나 SQL로 최종 상태를 확인해야 한다. 양방향 연관관계는 객체 그래프의 양쪽 의미를 일치시켜야 한다.

## 사례 2: 문자열 contains 기반 WEEKLY 판정

### 상황

기존 WEEKLY 일정은 다음과 같이 판정했다.

```java
scheduleValue.toUpperCase().contains(today.getDayOfWeek().name())
```

### 위험

`MONDAYX`처럼 실제 요일이 아닌 문자열도 월요일로 판정될 수 있다. 공백·빈 token·잘못된 요일도 저장 단계에서 막지 않았다.

### 해결

- request validation에서 쉼표 단위 token을 `DayOfWeek.valueOf()`로 검증
- 계산에서는 쉼표로 분리하고 대소문자를 정규화한 뒤 정확히 같은 요일만 비교
- React의 `Monday,Wednesday` 형식은 그대로 허용

### 배운 점

표현이 목록이면 부분 문자열 검색이 아니라 tokenization과 정확한 값 검증이 필요하다. 입력 검증과 계산 방어를 둘 다 둬야 DB에 이미 잘못된 값이 있어도 조용히 오판하지 않는다.

## 사례 3: 순차 중복 차단과 동시 중복 차단은 다름

### 상황

기존 코드는 조회한 상태가 `TAKEN`이면 거부했다. 한 요청이 끝난 뒤 같은 요청을 보내는 순차 중복은 막는다.

### 경쟁 조건

```text
요청 A: PENDING 읽음
요청 B: PENDING 읽음
요청 A: TAKEN 저장
요청 B: TAKEN 저장
```

두 요청이 같은 값을 읽으면 둘 다 성공 메시지를 만들 수 있다.

### 해결

완료 조회에 `PESSIMISTIC_WRITE`를 적용했다. 두 번째 transaction은 첫 번째가 commit할 때까지 기다리고, 이후 TAKEN을 읽어 `InvalidIntakeTransitionException`으로 거부된다.

### 주의

- 잠금은 transaction 종료까지 유지되므로 잠금 뒤 외부 Telegram 발송 같은 느린 작업을 넣지 않는다.
- 현재 명령 서비스는 DB 상태 변경 결과만 반환하고 Telegram 발송은 transaction 호출 뒤 handler가 수행한다.
- 여러 행을 잠글 때는 항상 같은 순서를 사용해야 교착 가능성을 낮출 수 있다.

## 사례 4: 인증 오류 형식이 인터셉터에서만 다름

### 상황

controller 예외는 `GlobalExceptionHandler`를 거쳤지만 인증 없음은 interceptor가 `sendError(401)`로 직접 응답했다.

### 증상

같은 API 계열에서도 어떤 오류는 plain text, 어떤 오류는 `{ "error": ... }`, 어떤 오류는 container 기본 body가 됐다.

### 해결

interceptor도 `UnauthorizedException`을 던지게 하고 전역 handler가 `ApiError`로 변환하도록 했다. 인증 controller의 미등록 사용자 응답도 같은 구조를 사용한다.

### 호환성 판단

React는 실패 body 구조에 의존하지 않고 HTTP 실패로 처리하고 있었다. 기존 `error` key는 유지했으며 URL·성공 body·인증 header는 바꾸지 않았다.

## 사례 5: H2 성공이 MariaDB 성공을 보장하지 않음

### 차이

H2 MariaDB mode는 빠른 service/controller 테스트에는 유용하지만 다음을 MariaDB와 똑같이 보장하지 않는다.

- enum 허용값과 저장 표현
- `BIT(1)` boolean 변환
- `TIME(6)`, `DATETIME(6)` 처리
- 실제 FK 오류 동작
- 조건부 update 결과

### 대응

CI의 MariaDB 11.8 service에서 V1 적용 후 대표 사용자·약·복용 행을 JDBC로 삽입한다. enum·boolean·시간을 다시 읽고, PENDING일 때만 TAKEN으로 바뀌는 update가 첫 실행 1건·두 번째 0건인지 확인하며, 존재하지 않는 user FK insert가 실패하는지도 검사한다. 데이터는 test DB transaction rollback과 다음 run의 Flyway clean으로 격리한다.

## 빠른 진단 순서

1. HTTP status와 `ApiError.code`를 함께 확인한다.
2. validation이면 `fieldErrors`의 property path를 확인한다.
3. 쓰기 API는 응답만 보지 말고 transaction flush 뒤 DB 상태를 확인한다.
4. 상태 전이 중복이면 repository 조회에 lock이 적용됐는지 확인한다.
5. H2에서만 재현되지 않으면 CI MariaDB 통합 테스트를 실행한다.

## 사례 6: sudo stdin 인코딩과 `/tmp` 보호 정책

### 상황

8081 비파괴 인수 테스트에서 root 전용 환경 파일의 관리자 비밀번호를 내용 출력 없이 임시 curl header로 만들려고 했다.

### 첫 증상: sudo 비밀번호 거부

PowerShell 문자열을 pipeline으로 SSH stdin에 넘기자 sudo는 같은 비밀번호를 틀렸다고 판단했다. 원격에서 `printf`로 동일 값을 전달한 무변경 `sudo -v` 검사는 성공했다.

### 첫 원인과 해결

문자열 값이 아니라 Windows PowerShell에서 native process stdin으로 전달되는 인코딩·줄바꿈 경계가 문제였다. 원격 shell의 `printf`가 정확한 바이트와 LF를 만들게 했고, 스크립트는 한 줄을 읽어 `sudo -S -v`에만 사용한 뒤 변수를 즉시 unset했다.

### 두 번째 증상: curl exit 23

pi 사용자가 `mktemp`로 `/tmp` 파일을 만든 뒤 root shell 또는 root curl이 그 파일을 열려고 하자 `Permission denied`와 curl exit 23이 발생했다. 일반적인 root 권한 예상과 달랐다.

### 두 번째 원인

sticky bit가 있는 `/tmp`와 Linux `fs.protected_regular` 보호는 권한 상승 프로세스가 다른 사용자가 먼저 만든 파일을 덮어쓰는 공격을 막는다. mode만 봐서는 root가 쓸 수 있어 보여도 안전 정책이 거부할 수 있다.

### 해결

- secret header와 response body 경로를 `/run/yakmogo-goal5-*`로 이동
- root가 파일을 처음 생성
- header는 600, 민감정보가 없는 response body는 644
- command line에는 실제 header 값을 넣지 않고 curl의 `--header @파일` 사용
- 성공·실패 trap 모두 `/run` 파일 삭제

### rollback 검증

새 JAR 기동 뒤 발생한 두 실패에서는 자동 rollback이 이전 JAR을 복원했다. 매번 다음을 확인한 뒤 재시도했다.

- 8081과 8080 active·HTTP 200
- 이전 JAR SHA-256 복원
- 고도화 Flyway history 1건 유지
- 고도화·운영 row count 불변
- 임시 JAR과 header/body 제거

### 배운 점

root 권한을 얻었다고 `/tmp` 파일 소유권 경계를 무시할 수 있다고 가정하면 안 된다. secret을 command argument로 우회하지 말고, 처음부터 올바른 소유자가 안전한 디렉터리에 파일을 생성해야 한다. 자동 rollback은 애플리케이션 실패뿐 아니라 검증 도구 실패에도 작동해야 한다.
