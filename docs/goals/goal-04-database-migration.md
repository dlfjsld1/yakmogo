# Goal 4: Flyway 기반 DB 마이그레이션

## 현재 상태

**승인 후 구현·검증 진행 중.** 2026-07-14 승인에 따라 고도화 DB 백업과 격리 복원 리허설, Flyway dependency·V1·설정·테스트 구현, 실제 MariaDB 빈 DB 검증까지 완료했다. `yakmogo_enhancement`의 8081 baseline 적용은 feature CI 성공 후 수행한다. 운영 `yakmogo`, 운영 8080, `main`은 변경하지 않았다.

## 한 문장 요약

JPA가 생성하는 스키마와 고도화·운영 DB의 물리 스키마는 현재 일치하므로, 신규 DB용 `V1__initial_schema.sql`과 기존 DB용 1회성 baseline version 1 전략을 사용하되 승인 전에는 적용하지 않는다.

## 작업 배경

현재 `application.yml`은 `spring.jpa.hibernate.ddl-auto=update`를 사용한다. 애플리케이션 시작이 스키마 변경 권한을 갖기 때문에 변경 이력과 적용 순서를 재현하기 어렵고, 잘못된 datasource를 사용하면 의도하지 않은 DB가 변경될 수 있다.

Goal 4는 Flyway를 도입해 스키마 변경을 명시적 SQL과 version으로 관리하고 JPA는 `validate`만 수행하게 만드는 작업이다. 단, 기존 DB에 baseline을 잘못 지정하면 실제 SQL 실행 없이 불일치를 정상으로 간주할 수 있으므로 migration 파일보다 현재 기준선 확인이 먼저다.

## 승인 전 수행 범위와 결과

- JPA 엔티티와 Hibernate 생성 DDL 확인
- `yakmogo_enhancement`의 `information_schema` 읽기
- 운영 `yakmogo`의 `information_schema` 읽기
- 테이블 row 내용 없이 건수와 물리 크기 확인
- DB 도구·디스크·기존 백업 유무 확인
- baseline, migration, 백업·복원 계획 작성
- 로컬 테스트 1건으로 Hibernate 생성 DDL 확인

## 승인 시점까지 제외한 범위

- Flyway dependency 추가
- `V1__initial_schema.sql` 작성
- `ddl-auto=validate` 전환
- `flyway_schema_history` 생성
- 고도화 DB dump 생성과 복원 DB 생성
- 고도화·운영 DB의 DDL 또는 데이터 변경
- 8081 JAR 교체와 서비스 재시작
- 운영 8080, 운영 DB, `main` 변경

## 조사 환경

| 항목 | 확인값 |
|---|---|
| MariaDB | `11.8.6-MariaDB-0+deb13u1` |
| DB engine | InnoDB |
| 두 schema 기본 문자셋 | `utf8mb4` |
| 두 schema 기본 collation | `utf8mb4_unicode_ci` |
| server collation | `utf8mb4_uca1400_ai_ci` |
| `lower_case_table_names` | `0` |
| DB time zone | `SYSTEM`, system `KST` |
| SQL mode | `STRICT_TRANS_TABLES, ERROR_FOR_DIVISION_BY_ZERO, NO_AUTO_CREATE_USER, NO_ENGINE_SUBSTITUTION` |
| dump 도구 | `/usr/bin/mariadb-dump`, server와 같은 11.8.6 계열 |
| 디스크 여유 | 약 104GB, 사용률 14% |
| 기존 `.sql`·`.sql.gz`·`.dump` | 발견되지 않음 |

server 기본 collation과 기존 schema collation이 다르므로 V1에는 `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`를 테이블마다 명시해야 한다.

## JPA 엔티티 스키마

### 엔티티와 테이블

| 엔티티 | 테이블 | ID 전략 |
|---|---|---|
| `User` | `users` | `BIGINT AUTO_INCREMENT` |
| `Guardian` | `guardian` | `BIGINT AUTO_INCREMENT` |
| `MedicineGroup` | `medicine_group` | `BIGINT AUTO_INCREMENT` |
| `IntakeLog` | `intake_log` | `BIGINT AUTO_INCREMENT` |

Hibernate 6.6.36과 MariaDB dialect가 빈 H2 MariaDB-mode DB에 생성한 DDL을 별도로 추출했다. 엔티티에서 명시하지 않은 문자열 길이는 255, 연관관계는 nullable, `LocalTime`은 `TIME(6)`, `LocalDateTime`은 `DATETIME(6)`로 생성됐다.

### 컬럼 비교

`일치`는 JPA 생성 DDL, 고도화 DB, 운영 DB의 타입·nullable이 동일하다는 뜻이다.

| 테이블 | 컬럼 | JPA 기대 | 고도화 | 운영 | 결과 |
|---|---|---|---|---|---|
| `users` | `id` | `bigint`, PK, auto increment, not null | 동일 | 동일 | 일치 |
| `users` | `name` | `varchar(255)`, nullable | 동일 | 동일 | 일치 |
| `guardian` | `id` | `bigint`, PK, auto increment, not null | 동일 | 동일 | 일치 |
| `guardian` | `chat_id` | `varchar(255)`, nullable | 동일 | 동일 | 일치 |
| `guardian` | `name` | `varchar(255)`, nullable | 동일 | 동일 | 일치 |
| `guardian` | `user_id` | `bigint`, nullable | 동일 | 동일 | 일치 |
| `medicine_group` | `id` | `bigint`, PK, auto increment, not null | 동일 | 동일 | 일치 |
| `medicine_group` | `intake_time` | `time(6)`, nullable | 동일 | 동일 | 일치 |
| `medicine_group` | `is_active` | `bit(1)`, not null, DB default 없음 | 동일 | 동일 | 일치 |
| `medicine_group` | `name` | `varchar(255)`, nullable | 동일 | 동일 | 일치 |
| `medicine_group` | `schedule_type` | `enum('DAILY','INTERVAL','WEEKLY')`, nullable | 동일 | 동일 | 일치 |
| `medicine_group` | `schedule_value` | `varchar(255)`, nullable | 동일 | 동일 | 일치 |
| `medicine_group` | `start_date` | `date`, nullable | 동일 | 동일 | 일치 |
| `medicine_group` | `user_id` | `bigint`, nullable | 동일 | 동일 | 일치 |
| `intake_log` | `id` | `bigint`, PK, auto increment, not null | 동일 | 동일 | 일치 |
| `intake_log` | `actual_taken_time` | `datetime(6)`, nullable | 동일 | 동일 | 일치 |
| `intake_log` | `intake_date` | `date`, nullable | 동일 | 동일 | 일치 |
| `intake_log` | `intake_time` | `time(6)`, nullable | 동일 | 동일 | 일치 |
| `intake_log` | `notified_count` | `int`, not null, DB default 없음 | 동일 | 동일 | 일치 |
| `intake_log` | `status` | `enum('CANCELLED','MISSED','PENDING','TAKEN')`, nullable | 동일 | 동일 | 일치 |
| `intake_log` | `medicine_group_id` | `bigint`, nullable | 동일 | 동일 | 일치 |
| `intake_log` | `user_id` | `bigint`, nullable | 동일 | 동일 | 일치 |

Java enum 선언 순서와 DB enum 문자열 순서가 다르다. Hibernate가 문자열을 알파벳 순으로 생성한 현재 물리 순서를 V1에 그대로 사용한다. `EnumType.STRING`이므로 enum ordinal 저장 문제는 없다.

### 인덱스와 FK 비교

| 테이블 | 컬럼 | 대상 | UPDATE/DELETE | JPA·고도화·운영 |
|---|---|---|---|---|
| `guardian` | `user_id` | `users.id` | RESTRICT/RESTRICT | 일치 |
| `medicine_group` | `user_id` | `users.id` | RESTRICT/RESTRICT | 일치 |
| `intake_log` | `user_id` | `users.id` | RESTRICT/RESTRICT | 일치 |
| `intake_log` | `medicine_group_id` | `medicine_group.id` | RESTRICT/RESTRICT | 일치 |

각 FK 컬럼에는 MariaDB가 사용하는 비고유 BTREE 인덱스가 있고 두 DB의 index 이름, FK 이름, 순서까지 동일하다. 별도의 UNIQUE 제약이나 trigger는 없으며 `flyway_schema_history`도 없다.

## 고도화와 운영 실제 상태

데이터 값은 조회하지 않고 `COUNT(*)`와 `information_schema` 통계만 확인했다.

| DB | users | guardian | medicine_group | intake_log | data+index |
|---|---:|---:|---:|---:|---:|
| `yakmogo_enhancement` | 0 | 0 | 0 | 0 | 약 128KiB |
| `yakmogo` | 1 | 1 | 2 | 65 | 약 128KiB |

스키마는 동일하지만 데이터 상태는 동일하지 않다. 운영 데이터는 baseline 조사에 사용하지 않고 이후에도 변경하지 않는다.

## 세 스키마의 차이

### 스키마 차이

현재 확인된 테이블, 컬럼 타입, null 허용, 기본값, PK, index, FK, engine, table collation 차이는 없다.

### 스키마 밖의 차이

- 운영 DB에는 실제 데이터가 있고 고도화 DB는 비어 있다.
- JPA 엔티티에는 DB 수준 UNIQUE 제약이 없다. 중복 방지는 repository의 존재 확인에 의존하므로 동시 요청 중복 가능성은 Goal 7 후속 후보로 기록한다.
- JPA cascade와 orphan removal은 애플리케이션 동작이며 FK는 DB 수준 `RESTRICT`다. 직접 DB 삭제 시 동작이 다르지만 현재 엔티티·물리 스키마 불일치는 아니다.
- `is_active`와 `notified_count`는 Java 생성자에서 값을 넣지만 DB default는 없다. V1에서 임의 default를 추가하면 현재 기준선과 달라지므로 추가하지 않는다.

## Flyway baseline 전략

### 결정

1. 신규 빈 DB는 `V1__initial_schema.sql`을 실행해 4개 테이블과 FK를 만든다.
2. 이미 동일 스키마가 있는 `yakmogo_enhancement`는 **baseline version 1**을 1회 기록하고 V1을 실행하지 않는다.
3. baseline 기록 직후 JPA `validate`로 실제 스키마가 엔티티와 일치하는지 확인한다.
4. 성공 후 `baseline-on-migrate`를 다시 false로 고정한다.
5. 운영 DB는 Goal 10 이후 별도 승인 전까지 baseline도 수행하지 않는다.

### 이유

기존 스키마에 `CREATE TABLE` V1을 실행하면 즉시 실패한다. 반대로 version 1 baseline은 현재 스키마를 V1 상태로 선언하므로 V1을 건너뛴다. 이 방식은 기존 스키마가 정말 V1과 같다는 신뢰를 요구하며, 이번 세 스키마 비교가 그 근거다.

### 안전 장치

- `spring.flyway.baseline-on-migrate` 기본값은 false로 둔다.
- 8081 최초 전환에서만 명시적으로 true를 주입한다.
- baseline version은 `1`로 고정한다.
- 첫 성공 후 환경변수에서 baseline 허용 값을 제거하고 재시작 검증한다.
- production 기본 datasource가 `yakmogo`이므로 profile과 `SELECT DATABASE()`를 배포 전 확인한다.
- 비어 있지 않은 미확인 DB를 자동 baseline하지 않는다.

## 작성할 migration 및 코드 목록

승인 후 다음과 같이 구현했다.

### 1. dependency

- `org.flywaydb:flyway-core`
- MariaDB/MySQL 지원 모듈인 `org.flywaydb:flyway-mysql`

실제 version은 Spring Boot 3.5.8 dependency management 결과인 Flyway 11.7.2이며 dependency report로 확인했다.

### 2. `V1__initial_schema.sql`

- `users`
- `guardian`
- `medicine_group`
- `intake_log`
- 현재와 같은 PK와 네 FK
- InnoDB
- `utf8mb4` / `utf8mb4_unicode_ci`
- 현재 enum 문자열 순서
- 현재 nullable과 DB default 상태

현재 불일치가 없으므로 V2 보정 migration은 작성하지 않았다. `flyway_schema_history`는 Flyway가 관리하며 수동 SQL에 넣지 않았다. 기존 물리 스키마와 동일하게 만들기 위해 컬럼 순서, enum 문자열 순서, FK 이름까지 맞췄다.

### 3. 설정

- JPA `ddl-auto=validate`
- Flyway enabled
- baseline version 1
- baseline-on-migrate 기본 false
- test 환경의 migration 실행 전략 명시

`baseline-on-migrate`의 저장소 기본값은 false다. 기존 8081 DB를 처음 편입할 때만 환경변수로 true를 주입하고 성공 직후 제거한다.

### 4. 테스트

- 빈 DB에서 V1 전체 적용
- Flyway validate 성공
- JPA validate 성공
- migration 재실행 시 추가 작업 없음
- 기존 고도화 DB에서 baseline 후 V1 미실행 확인
- 두 번째 시작에서 baseline 허용 없이 정상 시작

H2 MariaDB mode만으로 최종 호환성을 확정하지 않고 실제 MariaDB 격리 DB 검증을 포함했다. 로컬에서는 MariaDB 환경변수가 없을 때 실제 DB 테스트 1건만 조건부 skip되고, GitHub Actions에서는 MariaDB 11.8 service를 띄워 반드시 실행한다. CI는 JUnit XML의 `tests=1`, `skipped=0`도 별도 확인한다.

## 승인 후 백업과 복원 리허설 결과

적용 전 `yakmogo_enhancement`만 root 전용으로 백업했다. 운영 `yakmogo`는 백업하거나 변경하지 않았다.

| 항목 | 결과 |
|---|---|
| 백업 파일 | `/var/backups/yakmogo/yakmogo_enhancement-before-flyway-20260714-133557.sql.gz` |
| 소유자·권한 | `root:root`, `600` |
| 크기 | 1,331 bytes |
| SHA-256 | `0b1f7fcf75321c66077f81b2b338cddd9d6712fcd1ad55c73adf8a3fd825be78` |
| 압축 검사 | `gzip -t` 성공 |
| 복원 DB | 임시 `yakmogo_enhancement_restore_goal4` |
| 복원 결과 | 4개 도메인 테이블과 FK·인덱스 복원, row count 모두 0 |
| 정규화 스키마 SHA-256 | `b14ced37c0dac7e2a270004223602e2feb199d897eabc5733ad01d77178f63fb` |
| 정리 | 검증 후 임시 복원 DB 삭제 |

백업 파일에는 DB 내용이 들어 있으므로 저장소로 복사하거나 내용을 출력하지 않았다. checksum은 복원 대상 파일 식별과 전송 손상 확인에 사용한다.

## 구현 결과

- `flyway-core`, `flyway-mysql`을 추가했다.
- `V1__initial_schema.sql`에 기존 4개 테이블과 네 FK를 정확히 기술했다.
- Hibernate schema 관리를 `update`에서 `validate`로 전환했다.
- Flyway validate, out-of-order 차단, clean 차단을 기본 설정으로 고정했다.
- H2 MariaDB mode에서 V1 최초 적용·validate·재실행 무변경을 검사한다.
- 실제 MariaDB 11.8에서 V1 최초 적용·validate·재실행 무변경을 검사한다.
- 애플리케이션 context 테스트도 Flyway가 만든 스키마를 JPA가 validate하도록 전환했다.

## 실제 MariaDB 격리 검증 결과

라즈베리파이의 임시 DB `yakmogo_flyway_goal4_test`에 V1을 적용해 애플리케이션을 두 번 시작했다. bot과 scheduler는 비활성화하고 non-web mode로 실행했으며, 임시 DB·JAR·스크립트는 종료 시 삭제했다.

| 검증 | 결과 |
|---|---|
| 첫 시작 V1 적용 | 성공, history 1건 |
| Flyway validate | 성공 |
| JPA `ddl-auto=validate` | 성공 |
| 두 번째 시작 신규 migration | 0건 |
| 도메인 테이블 | 4개 |
| 기존 스키마와 정규화 비교 | 일치 |
| 정규화 SHA-256 | `4e358452cf1092fd502aa8e5b42f5d6d1812f280af8e099f77157186e139cf7f` |

서로 다른 비교 단계의 checksum은 dump 표현과 정규화 방식이 달라 직접 같은 값이어야 하는 지표가 아니다. 각 단계 안에서 원본과 복원본 또는 기대 스키마와 생성 스키마의 동일성을 판정하는 데 사용했다.

## 로컬 검증 결과

`./gradlew clean test bootJar --no-daemon`을 실행했다.

| 항목 | 결과 |
|---|---|
| test | 12건, 실패 0, 오류 0 |
| 로컬 skip | 1건: 실제 MariaDB 환경변수가 없을 때만 실행하지 않는 통합 테스트 |
| H2 Flyway V1 테스트 | 성공 |
| Spring context·JPA validate | 성공 |
| bootJar | 성공 |

로컬 skip은 실제 DB 검증 누락으로 간주하지 않는다. 같은 V1을 라즈베리파이의 격리 MariaDB에서 별도로 성공시켰고, feature CI에서는 MariaDB service와 환경변수를 제공해 해당 테스트가 실행됐음을 JUnit XML로 강제 확인한다.

## 백업 방법

승인 후 migration 또는 baseline 전에 다음 순서로 수행했으며 위 결과를 확인했다.

1. `/var/backups/yakmogo`를 `root:root 700`으로 준비한다.
2. `mariadb-dump --single-transaction --quick --routines --triggers --events --hex-blob yakmogo_enhancement`를 실행한다.
3. 결과를 timestamp가 포함된 `.sql.gz`로 저장하고 `root:root 600`을 적용한다.
4. `gzip -t`로 압축 무결성을 확인한다.
5. SHA-256 파일을 함께 생성한다.
6. 파일 크기, checksum, 생성 시간을 기록한다.

현재 schema와 데이터가 작아 dump 자체는 1MiB 미만일 가능성이 높지만 실제 생성 크기로 다시 보고한다. DB 값이 포함되므로 dump 내용을 출력하거나 저장소에 복사하지 않는다.

## 복원 방법

운영 DB가 아니라 격리 DB에서만 리허설했다.

1. `yakmogo_enhancement_restore_goal4` 같은 임시 DB를 생성한다.
2. dump를 임시 DB에 복원한다.
3. 4개 테이블의 구조, FK, index, row count를 원본 고도화 DB와 비교한다.
4. 일치 증거를 남긴 뒤 임시 DB를 삭제한다.
5. 실제 rollback이 필요하면 8081을 중지하고 승인된 dump를 `yakmogo_enhancement`에 복원한 후 이전 JAR과 설정으로 되돌린다.

격리 DB 생성·복원·삭제는 승인 후에만 실행했다.

## 예상 위험

| 위험 | 영향 | 대응 |
|---|---|---|
| datasource 오지정 | 운영 DB에 Flyway 접근 | enhancement profile, DB 이름 사전 확인, 운영 미배포 |
| baseline이 실제 불일치를 숨김 | 이후 validate 또는 런타임 실패 | 세 스키마 비교 근거 보존, baseline 후 JPA validate |
| baseline-on-migrate 상시 활성 | 알 수 없는 DB를 자동 채택 | 1회만 true, 성공 후 제거 |
| H2와 MariaDB DDL 차이 | CI는 성공하고 실 DB 실패 | 실제 MariaDB 격리 DB 테스트 |
| Flyway MariaDB 모듈 누락 | startup migration 실패 | dependency tree와 빈 DB test |
| enum·collation 차이 | validate 또는 정렬·비교 차이 | V1에 현재 enum과 table collation 명시 |
| DB 사용자 DDL 권한이 양 DB에 존재 | 잘못된 URL의 피해 확대 | 8081 URL 고정, 운영 미배포; 권한 분리는 후속 운영 보안 후보 |
| 현재 백업 부재 | 실패 시 복구 불가 | 적용 전에 root 전용 dump와 복원 리허설 필수 |
| 운영 데이터 존재 | 보정 DDL 위험 증가 | 운영에는 baseline·migration 미적용 |

## 시행착오와 학습

Hibernate 생성 DDL 추출 시 H2를 MariaDB mode로 실행하면서 MariaDB dialect의 `DROP FOREIGN KEY IF EXISTS`가 존재하지 않는 초기 테이블에 적용돼 경고가 발생했다. 테스트는 성공했지만 H2가 MariaDB의 완전한 대체가 아님을 보여준다.

또한 권한 확인에 `SHOW GRANTS`를 사용하면 인증 hash가 출력될 수 있다. 실제 값은 문서에 기록하지 않았으며 이후 점검은 필요한 권한 사실만 기록해야 한다. 자세한 내용은 [DB 마이그레이션 트러블슈팅](../troubleshooting/database-migration.md)에 남긴다.

## 남은 실행 순서

1. feature 브랜치 커밋·CI 성공 확인
2. 8081 JAR과 enhancement 환경 파일 백업
3. 고도화 DB에 1회 baseline version 1
4. JPA validate와 HTTP·DB·bot/scheduler 검증
5. baseline-on-migrate 제거 후 두 번째 재시작 검증
6. 운영 8080과 운영 DB가 변하지 않았음을 읽기 확인
7. Goal 문서·troubleshooting 최종 갱신
8. feature 커밋·CI 후 `enhancement` 통합

## 현재 상태

승인 지점 1의 승인을 받아 구현 중이다. 다음 별도 승인 지점은 Goal 8 자동 배포 활성화 전이며, Goal 4의 8081 수동 검증은 이미 승인된 범위에 포함된다.
