# Goal 9 실행 프롬프트: 관측성·백업·복원

이 작업을 Goal로 생성하고 완료할 때까지 진행한다. [공통 고도화 원칙](common-principles.md)을 모두 적용한다.

## 목표

운영과 고도화 서비스의 상태를 빠르게 파악하고 host 운영 DB와 Docker 고도화 DB·image를 실제로 복구할 수 있는 최소 관측·백업 체계를 구축한다.

## 브랜치

backend 최신 `enhancement`에서 `feature/operations`를 만든다. 저장소 외 Raspberry Pi 설정은 8081부터 검증하고 운영 변경이 필요하면 별도 승인 없이 적용하지 않는다.

## 작업

- Spring Boot Actuator를 최소 권한으로 도입한다.
- 필요한 health 정보만 노출하고 env, configprops, heapdump 등 민감 endpoint는 공개하지 않는다.
- MariaDB 연결, Docker app·DB health, volume과 디스크 여유 공간, 운영 systemd 상태를 점검한다.
- 운영 DB와 고도화 DB의 백업 파일, 경로, 일정, 보관 정책을 분리한다.
- 백업 보관 기간과 자동 정리 기준을 정한다.
- 백업 파일의 권한과 민감정보 취급 방식을 정의한다.
- 고도화 MariaDB container의 논리 dump와 volume 역할을 구분한다. 복구 기준은 검증 가능한 SQL dump로 두고 volume 복사만으로 복구 완료라고 판단하지 않는다.
- 복원 리허설은 별도 Compose project·임시 volume의 테스트 DB에서 수행한다.
- 운영 DB에 복원 시험을 하지 않는다.
- Uptime Kuma에는 사용자가 실제 이용하는 Yakmogo 운영 endpoint 하나만 둔다. 8081 enhancement는 릴리스 전 임시 검증 환경이므로 영구 monitor를 추가하지 않고 배포·인수 테스트에서 직접 확인한다.
- 기본 배포 경로에서 제외한 GitHub Actions self-hosted runner monitor는 추가하지 않는다. runner 서비스를 유지할지 제거할지는 읽기 전용 상태를 보고하고 별도 승인을 받되 Yakmogo 실행·백업 monitor와 섞지 않는다.
- 모든 Uptime Kuma monitor는 이름만으로 대상·환경·검사 방식을 식별할 수 있게 하고, 설명 또는 비고에 `감시 대상`, `정상 조건`, `장애 영향`, `확인 명령`, `복구 문서`를 기록한다. 설치된 Kuma 버전에 설명란이 없으면 같은 내용을 태그와 이 저장소의 monitor 목록표에 기록한다.
- monitor를 추가·변경·삭제할 때 운영 런북의 monitor 목록과 실제 Kuma 설정을 함께 갱신한다. 이름만 다른 중복 monitor는 만들지 않는다.
- 장애 감지, 로그 확인, 직전 image rollback, DB dump 복원의 런북을 작성한다.
- Yakmogo Docker 운영 문서는 단순 명령 모음이 아니라 애플리케이션의 역할, 설치·설정 방법, 경로와 권한, 정상 확인, 장애 영향, 복구 순서, 민감정보 취급을 포함해 갱신한다. Transmission, Jellyfin, Samba 등 프로젝트 밖의 홈서버 구성은 이 저장소에 기록하지 않는다.

## 완료 조건

- 8080 운영 endpoint와 8081 release candidate health를 직접 확인하되, Kuma monitor는 운영 endpoint 하나만 유지
- 민감 Actuator endpoint 외부 비공개
- 고도화 DB 백업 생성 및 복원 리허설 성공
- 복원 데이터의 핵심 테이블·row 수 검증
- 오래된 백업과 디스크 고갈 방지 정책 확인
- 운영 데이터 무변경 확인
- 실제 Uptime Kuma의 단일 Yakmogo monitor와 문서의 이름·URL/포트·주기·설명·활성 상태 일치 확인
- 신규 운영 구성의 설치부터 정상 확인과 제거·복구까지 제3자가 문서만 보고 재현할 수 있는지 검토
- `install.sh`, `update.sh`, `backup.sh`, `restore.sh`와 Goal 9 정책의 주기·보관·검증 방식 일치 확인
- 관측·백업 troubleshooting과 운영 런북 갱신
