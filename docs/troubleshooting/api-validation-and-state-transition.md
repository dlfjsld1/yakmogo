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
