# Goal 4 실행 프롬프트: Flyway 기반 DB 마이그레이션

이 작업을 Goal로 생성하고 완료할 때까지 진행한다. [공통 고도화 원칙](common-principles.md)을 모두 적용한다.

## 목표

Hibernate `ddl-auto=update` 의존을 제거하고 Flyway 기반의 재현 가능한 DB 마이그레이션 체계를 구축한다.

## 브랜치

backend 최신 `enhancement`에서 `feature/database-migration`을 만든다. `main`은 변경하지 않는다.

## 마이그레이션 작성 전 기준선 조사

Flyway 파일을 작성하기 전에 다음 세 기준을 반드시 비교한다.

1. 현재 JPA 엔티티가 기대하는 스키마
2. `yakmogo_enhancement`의 실제 스키마
3. 운영 `yakmogo` DB의 읽기 전용 스키마

다음을 테이블별로 비교하고 보고서를 작성한다.

- 컬럼명, 타입, 길이, 정밀도
- NULL 허용과 기본값
- PK, 인덱스, UNIQUE 제약
- FK, 참조 대상, 삭제·갱신 규칙
- 엔티티와 실제 DB 간 차이

운영 DB에는 DDL, Flyway 적용, 데이터 수정, 복원 시험을 실행하지 않는다.

## 작업

- 세 기준의 비교 결과로 실제 기준선을 확정한다.
- 고도화 DB를 먼저 백업하고 복구 가능성을 확인한다.
- 기존 DB에 Flyway baseline이 필요한지 판단하고 근거를 문서화한다.
- 확정된 기준선에 맞는 초기 migration을 작성한다.
- 빈 테스트 DB에서 처음부터 전체 migration을 실행한다.
- 기존 `yakmogo_enhancement`에서 적용을 검증한다.
- migration 반복 실행의 안전성을 확인한다.
- JPA `ddl-auto`를 `validate`로 전환한다.
- seed가 필요하면 운영 데이터와 분리된 명시적 방식으로 둔다.
- 실패 시 DB 복구 절차를 작성한다.

## 보호 시나리오

- migration 전에 Flyway 파일을 먼저 만들지 않는다.
- 운영과 고도화의 차이를 임의로 한쪽에 맞추지 않는다.
- 스키마 비교 중 발견한 범위 밖 데이터 품질 문제는 수정하지 않고 보고한다.
- MariaDB와 H2 차이로 migration 검증이 왜곡되지 않게 한다.

## 완료 조건

- 세 스키마 비교 보고서 작성
- 실제 기준선과 baseline 전략 확정
- 빈 DB에서 전체 스키마 생성 성공
- 기존 고도화 DB에서 migration 성공
- 반복 실행 시 오류나 추가 변경 없음
- `ddl-auto=validate` 상태에서 backend 전체 테스트 성공
- 8081 정상 실행 및 DB 분리 확인
- 운영 DB 무변경 확인
- Goal 4 및 DB troubleshooting 문서 갱신
- feature CI 성공 후 `enhancement` 통합
