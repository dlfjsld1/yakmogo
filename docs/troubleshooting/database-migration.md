# DB 마이그레이션 트러블슈팅

## 사례 1: H2 MariaDB mode에서 초기 FK drop 경고

### 상황

JPA 엔티티가 기대하는 DDL을 확인하기 위해 `YakmogoApplicationTests`를 빈 H2 DB, MariaDB mode, MariaDB dialect, `create-drop`으로 재실행했다.

### 증상

Hibernate가 create 전에 FK를 drop하려 했고 아직 테이블이 없어 다음 경고가 발생했다.

```text
Error executing DDL "alter table ... drop foreign key if exists ..."
Table not found
```

이후 4개 테이블과 FK 생성은 완료됐고 테스트 결과는 성공이었다.

### 원인

MariaDB dialect가 생성한 FK drop 문법과 H2의 schema drop 동작이 완전히 같지 않다. H2의 MariaDB mode는 문법 호환을 높이지만 MariaDB server 자체가 아니다.

### 해결과 판단

생성된 DDL을 JPA 기대 스키마 추출 근거로만 사용했다. Flyway의 최종 성공 근거는 H2 단독 테스트가 아니라 실제 MariaDB 격리 DB migration과 JPA validate로 정한다.

### 재발 방지

- H2 통과를 MariaDB migration 통과로 표현하지 않는다.
- enum, `TIME(6)`, `DATETIME(6)`, engine, collation, FK를 실제 MariaDB에서 검증한다.
- CI에 실제 MariaDB migration 검증 경로를 둔다.

## 사례 2: 권한 조회가 인증 hash를 출력할 수 있음

### 상황

Flyway가 schema history와 테이블을 만들 권한이 있는지 읽기 전용으로 확인하려고 `SHOW GRANTS`를 사용했다.

### 증상

권한 문장에 계정의 인증 hash 표현이 함께 출력될 수 있었다. 실제 값은 문서와 코드에 남기지 않는다.

```text
GRANT ... IDENTIFIED BY PASSWORD '<redacted>'
```

### 원인

`SHOW GRANTS`는 권한 범위만 반환하는 명령이 아니라 server와 계정 상태에 따라 인증 관련 절도 포함할 수 있다.

### 해결과 판단

Goal 4 보고서에는 `yakmogo_user`가 두 schema에 DDL 가능한 권한을 가진다는 사실만 기록했다. 향후 자동 점검은 출력 전체를 보존하지 않고 필요한 privilege 이름만 선택하거나 boolean 결과로 제한한다.

### 재발 방지

- `SHOW GRANTS` 전체 출력을 CI 로그와 문서에 넣지 않는다.
- `information_schema`의 필요한 privilege 컬럼만 선택한다.
- credential 또는 hash가 출력됐는지 검사한 뒤 기록을 남긴다.
- credential 회전과 최소 권한 분리는 운영 환경 변경 승인이 필요한 후속 보안 작업으로 관리한다.

## 사례 3: 실제 스키마가 같아도 baseline은 검증이 아님

### 상황

JPA, 고도화 DB, 운영 DB의 현재 구조가 동일하므로 기존 DB에 Flyway baseline version 1을 사용할 계획을 세웠다.

### 위험

Flyway baseline은 V1 SQL을 실행하거나 기존 테이블이 V1과 같은지 확인하지 않는다. 관리 시작 version을 기록할 뿐이다.

### 대응

- baseline 전 세 스키마 비교 결과를 보존한다.
- baseline 직후 `ddl-auto=validate`로 JPA 검증을 수행한다.
- baseline 허용 설정을 1회 사용 후 제거한다.
- 이후 모든 변경은 V2 이상 migration으로만 수행한다.

### 배운 점

baseline은 schema 검증 기능이 아니라 기존 schema를 version 관리에 편입하는 선언이다. 비교와 backup 없이 편의 설정으로 활성화하면 안 된다.

## 사례 4: 스키마 dump hash가 구조 불일치처럼 보임

### 상황

실제 MariaDB 빈 DB에 V1을 적용한 뒤 기존 `yakmogo_enhancement`의 schema-only dump와 hash를 비교했다.

### 증상

테이블·컬럼 타입·제약은 같았지만 원시 dump hash가 달랐다.

### 원인

- 초안 V1의 일부 컬럼 순서가 기존 물리 스키마와 달랐다.
- 기존 빈 테이블에는 과거 insert/delete로 증가한 `AUTO_INCREMENT` 다음 값이 남아 있었고 신규 DB에는 없었다.
- `AUTO_INCREMENT` 카운터는 현재 행이나 JPA 구조가 아니라 데이터 이력에 따른 메타데이터다.

### 해결

V1의 컬럼 순서를 기존 스키마와 동일하게 수정했다. 비교 시에는 `AUTO_INCREMENT=<숫자>`만 정규화한 후 테이블, 컬럼, index, FK, engine, charset, collation을 비교했다. 정규화 후 hash가 일치했고 JPA validate도 성공했다.

### 재발 방지

- 원시 dump hash 하나만으로 구조 일치 여부를 판정하지 않는다.
- 컬럼 순서처럼 재현 가능한 구조는 migration에 맞춘다.
- 데이터 이력 메타데이터는 명시적으로 정규화하고 그 규칙을 기록한다.
- 최종 판단은 Flyway validate, JPA validate, information_schema 비교를 함께 사용한다.

## 사례 5: 원격 cleanup과 PowerShell 상태값이 실패를 가릴 수 있음

### 상황

임시 DB와 JAR을 반드시 지우기 위해 원격 검증 스크립트에 cleanup trap을 사용하고, Windows PowerShell에서 SSH 명령의 종료 상태를 전달했다.

### 증상

초기 스키마 비교가 실패했는데도 cleanup의 마지막 성공 명령 또는 PowerShell의 상태값 해석 때문에 전체 실행이 성공처럼 보일 수 있었다.

### 원인

- trap에서 원래 종료 코드를 보존하지 않으면 cleanup 명령의 결과가 최종 상태가 될 수 있다.
- 원격 shell용 상태 표현을 PowerShell 문자열 안에 그대로 넣으면 로컬 PowerShell이 먼저 해석할 수 있다.

### 해결

원격 스크립트가 본문 종료 코드를 먼저 저장하고 cleanup 후 같은 코드로 종료하도록 변경했다. PowerShell이 원격 상태 표현을 선해석하지 않도록 quoting을 분리했고, 최종 성공 조건을 종료 코드뿐 아니라 `FIRST_START_READY=true`, `SECOND_START_READY=true`, `SECOND_START_NEW_MIGRATIONS=0` 같은 명시적 증거로 확인했다.

### 재발 방지

- cleanup trap은 진입 즉시 원래 종료 코드를 저장한다.
- 로컬 shell과 원격 shell의 변수·상태 표현을 섞지 않는다.
- exit 0만 믿지 않고 핵심 검증 결과가 실제로 출력됐는지 확인한다.
- 실패 후 임시 DB, JAR, 스크립트가 남지 않았는지 별도로 검사한다.
