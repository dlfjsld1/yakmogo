# Goal 9 실행 프롬프트: 관측성·백업·복원

이 작업을 Goal로 생성하고 완료할 때까지 진행한다. [공통 고도화 원칙](common-principles.md)을 모두 적용한다.

## 목표

운영과 고도화 서비스의 상태를 빠르게 파악하고 DB와 JAR을 실제로 복구할 수 있는 최소 관측·백업 체계를 구축한다.

## 브랜치

backend 최신 `enhancement`에서 `feature/operations`를 만든다. 저장소 외 Raspberry Pi 설정은 8081부터 검증하고 운영 변경이 필요하면 별도 승인 없이 적용하지 않는다.

## 작업

- Spring Boot Actuator를 최소 권한으로 도입한다.
- 필요한 health 정보만 노출하고 env, configprops, heapdump 등 민감 endpoint는 공개하지 않는다.
- MariaDB 연결, 디스크 여유 공간, systemd 서비스 상태와 재시작 횟수를 점검한다.
- 운영 DB와 고도화 DB의 백업 파일, 경로, 일정, 보관 정책을 분리한다.
- 백업 보관 기간과 자동 정리 기준을 정한다.
- 백업 파일의 권한과 민감정보 취급 방식을 정의한다.
- 복원 리허설은 `yakmogo_enhancement` 복제본 또는 별도 테스트 DB에서 수행한다.
- 운영 DB에 복원 시험을 하지 않는다.
- Uptime Kuma에서 8080과 8081을 별도 모니터링할 수 있게 구성한다.
- 장애 감지, 로그 확인, JAR 롤백, DB 복원의 런북을 작성한다.

## 완료 조건

- 8080과 8081 health 확인
- 민감 Actuator endpoint 외부 비공개
- 고도화 DB 백업 생성 및 복원 리허설 성공
- 복원 데이터의 핵심 테이블·row 수 검증
- 오래된 백업과 디스크 고갈 방지 정책 확인
- 운영 데이터 무변경 확인
- 관측·백업 troubleshooting과 운영 런북 갱신
