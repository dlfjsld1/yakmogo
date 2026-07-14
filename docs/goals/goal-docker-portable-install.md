# Docker D: 단일 명령 설치와 수동 업데이트

## 한 문장 요약

Yakmogo 앱과 전용 MariaDB를 하나의 Compose 프로젝트로 묶고, 새 ARM64 Linux에서 `.env` 작성 후 `./install.sh` 한 번으로 설치하며 `./update.sh`로 백업·검증·실패 복귀까지 수행하도록 단순화했다.

## 작업 배경

기존 Goal 8과 첫 Docker 전환은 GitHub self-hosted runner가 Pi의 고정 root helper를 호출하는 자동 CD 구조였다. 안전한 구조였지만 새 시스템 복구 관점에서는 runner, sudoers, staging 경로, systemd shadow까지 알아야 했다. 사용자 한 명이 운영하는 홈서버에는 자동 배포 속도보다 설치와 복구의 이해 가능성이 더 중요하다고 판단해 기본 경로를 독립 설치 패키지와 수동 한 명령 업데이트로 바꿨다.

## 변경 전 문제

- 앱 container가 host network로 host MariaDB에 연결돼 한 묶음만으로 새 시스템에 설치할 수 없었다.
- 자동 CD에는 Pi runner와 제한 sudoers가 필요했다.
- image 이름이 commit SHA 중심이라 사람이 관리할 release version이 없었다.
- 백업·복원과 새 시스템 설치 절차가 하나의 실행 경로로 검증되지 않았다.

## 이번 Goal 범위

- `yakmogo-app`과 `yakmogo-mariadb`를 별도 container로 구성
- DB named volume과 전용 network 구성
- MariaDB 3306 host 비공개
- `.env` 기반 비밀정보 주입
- 빈 DB 설치, 기존 dump 복원, 논리 백업
- versioned ARM64 image와 설치 archive 생성
- 앱 업데이트 전 DB 백업과 readiness 실패 시 직전 image 복귀
- GitHub-hosted runner의 build·artifact·격리 통합 검증
- 한국어 설치·빌드·업데이트 설명서

## 범위에서 제외한 내용

- 현재 Pi 8081과 host `yakmogo_enhancement` DB 전환
- 운영 8080, 운영 DB `yakmogo`, `main` 변경
- registry와 자동 CD 재구축
- Pi 전체 복구 문서 변경
- Android 코드·API·테이블

## 도입한 기술과 개념

### image, container, volume의 차이

- image는 Java 21과 통합 Yakmogo JAR가 든 읽기 전용 실행 설계도다.
- container는 그 image를 실제로 실행한 프로세스다. 앱과 DB를 한 container에 넣지 않아 각각 독립적으로 재시작·교체할 수 있다.
- volume은 container를 지워도 유지되는 DB 데이터 저장소다. 이 Goal의 실제 데이터는 `yakmogo-mariadb-data`에 남는다.

### Compose의 역할

Compose는 두 container, network, volume과 환경값 연결을 한 파일에 선언한다. 앱은 고정 IP가 아니라 Docker DNS 이름 `yakmogo-mariadb`로 DB를 찾는다. DB에는 host port를 열지 않았으므로 외부에서는 직접 접속할 수 없고 앱만 같은 Docker network 안에서 3306에 접속한다.

### image와 비밀정보의 분리

image는 여러 시스템에 복사할 수 있는 공용 산출물이고 `.env`는 해당 설치만의 비밀번호와 token이다. 패키지 제작기는 허용 목록 파일만 복사해 개발자의 실제 `.env`가 산출물에 섞이지 않게 했다. `.env`는 Git에서 제외하며 Linux 권한 `600`이 아니면 실행을 거부한다.

## 선택한 방식과 대안

- 앱과 DB를 한 container에 넣는 방식은 process 수명과 데이터 수명이 섞이므로 제외했다.
- GHCR registry는 로그인 credential과 운영 절차가 늘어나 현재 규모에 필요하지 않아 image tar를 선택했다.
- self-hosted 자동 CD는 새 시스템 필수 요소에서 제거했다. GitHub Actions는 검증된 설치 archive를 만드는 데까지만 관여한다.
- DB dump는 MariaDB 전체 data directory 복사보다 버전과 저장 방식에 덜 결합된 `mariadb-dump` 논리 백업을 사용한다.

## 실제 동작 흐름

### 새 설치

```text
.env 검증
  -> image tar SHA-256 검증
  -> docker load
  -> yakmogo-mariadb 시작/health 확인
  -> 선택한 SQL dump를 빈 DB에만 복원
  -> yakmogo-app 시작
  -> / 200 + 예상 JS 200 + 보호 API 401 확인
  -> 현재 정상 release manifest 기록
```

### 업데이트와 실패 복귀

```text
현재 .env와 release manifest 임시 보관
  -> DB SQL dump + SHA-256 생성
  -> 새 image 검증/load
  -> 앱 container만 새 image로 재생성
  -> readiness 성공: 새 manifest 확정
  -> readiness 실패: 이전 .env/image 복원 후 다시 readiness 확인
```

MariaDB container와 volume은 일반 앱 업데이트 때 재생성하지 않는다. Flyway migration은 앱 시작 시 수행되므로 migration을 포함한 업데이트의 DB 역복원은 별도 승인과 복구 절차가 필요하다.

## 변경된 파일과 역할

- `deploy/portable/compose.yml`: 앱·전용 MariaDB·volume·network
- `deploy/portable/.env.example`: 비밀값 없는 설정 견본
- `deploy/portable/install.sh`: 신규 설치와 선택적 dump 복원
- `deploy/portable/update.sh`: 백업, 앱 교체, 실패 복귀
- `deploy/portable/backup.sh`, `restore.sh`: 논리 백업과 빈 DB 복원
- `deploy/portable/lib.sh`: 입력·checksum·readiness 공통 검증
- `deploy/portable/README.md`: 설치와 운영 한국어 설명서
- `scripts/release/build-image.ps1`: Windows 단일 빌드 진입점
- `scripts/release/build-image.sh`: 통합 JAR·ARM64 image·패키지 조립
- `scripts/release/package-portable.sh`: 허용 파일만 복사하고 권한 보존 archive 생성
- `scripts/ci/test-portable-install.sh`: GitHub 격리 통합 검증
- `.github/workflows/release-candidate.yml`: GitHub-hosted build·test·artifact 전용 workflow

## 데이터 및 설정 변경

필수 설치 설정은 project 이름, app image, HTTP port, frontend URL, DB 이름·계정·두 비밀번호, 관리자 비밀번호, 인증 secret이다. Telegram과 scheduler는 기본 `false`다. DB 데이터는 `${COMPOSE_PROJECT_NAME}-mariadb-data` volume에 저장된다.

현재 Pi의 데이터와 설정은 변경하지 않았다. 현재 8081, 운영 8080, 두 host DB와 runner도 건드리지 않았다.

## 시행착오와 문제 해결 과정

1. 첫 Compose 초안에서 network를 `internal: true`로 두었다. DB 격리는 되지만 앱도 Telegram API 등 외부 인터넷에 나갈 수 없으므로 제거했다. DB port 비공개만으로 host 직접 접근은 막는다.
2. 첫 update 초안은 이전 release manifest를 읽었지만 install이 그 파일을 기록하지 않았다. install 성공 시 현재 manifest를 저장하고 update가 임시 복사본으로 rollback하도록 고쳤다.
3. 패키지 디렉터리 전체 복사는 개발자의 실제 `.env`를 포함할 가능성이 있었다. 허용 목록의 8개 파일만 복사하도록 바꿨다.
4. Windows에서 폴더를 옮기면 shell 실행 권한이 사라질 수 있어 실행 권한을 보존한 `portable.tar.gz`와 checksum을 함께 만든다.
5. 로컬 Gradle 첫 실행은 sandbox가 wrapper 다운로드를 막아 실패했다. 네트워크 허용 후 동일 명령이 성공했으며 코드 실패는 아니었다.
6. 첫 GitHub 통합 검증은 테스트 준비 코드가 `.env`와 shell script 모두에 `chmod 600`을 적용해 `install.sh` 실행 권한을 제거하면서 exit 126으로 실패했다. `.env`는 600, script는 실행 권한으로 분리했다.
7. 두 번째 통합 검증은 readiness 최대 대기 시간이 지난 뒤 실패했지만 비로그인 공개 API가 내부 로그를 제공하지 않았다. 추측 수정 대신 설치·복원·rollback 단계명을 실패 annotation에 남기도록 진단 경계를 추가했다.
8. 단계 진단 결과 신규 앱·DB 설치는 성공했고 바로 다음 `create-probe` SQL에서 실패했다. MariaDB client의 실제 오류만 공개 annotation으로 확인하도록 해당 명령의 표준 오류를 제한적으로 기록했다.
9. 실제 오류는 `using password: NO`였다. MariaDB 초기화 환경변수가 나중의 `docker compose exec` 셸에서도 제공된다고 가정한 것이 원인이었다. 권한 600 `.env`에서 값을 읽고 client 명령 하나에 `MARIADB_PWD`로 주입하도록 백업·복원과 검증 명령을 모두 수정했다.

## 테스트와 보호 시나리오

- 전체 backend test: 성공
- shell 문법 검사: 성공
- PowerShell parser 검사: 성공
- 통합 JAR verifier 단위 테스트: 성공
- container/portable 정적 안전 계약: 성공
- GitHub ARM64 image build: 실행 중
- 빈 DB 설치·dump 복원·고장 image rollback: GitHub 격리 Docker에서 실행 중

## 배포 및 검증

feature branch만 GitHub에 push했다. release workflow에는 `self-hosted` job과 Pi deploy 명령이 없다. 산출물은 `build/portable`과 `yakmogo-<version>-portable.tar.gz`이며 실제 Pi 전환은 승인 전 수행하지 않는다.

## 운영 시 주의 사항

- `.env`는 반드시 권한 `600`으로 보관한다.
- `docker compose down -v`는 Yakmogo DB volume을 삭제하므로 운영에서 실행하지 않는다.
- update가 DB dump를 만들지만 별도 디스크 복사와 보관 정책은 Goal 9에서 정한다.
- Telegram과 scheduler는 데이터 복원·검증을 마친 뒤에만 활성화한다.

## 직접 확인하는 방법

```bash
docker compose --env-file .env -f compose.yml ps
docker volume inspect yakmogo-mariadb-data
curl -I http://127.0.0.1:8080/
./backup.sh
```

## 알려진 한계

- 최초 설치에는 MariaDB image를 받을 인터넷 또는 미리 받은 `mariadb:11.8` image가 필요하다.
- SQL migration 자체의 자동 역변환은 제공하지 않는다.
- 현재 enhancement DB를 container volume으로 옮기는 실기 검증은 승인 후 단계다.

## 후속 Goal에 넘길 내용

CI 성공 후 현재 enhancement DB dump·검증·8081 cutover 계획을 보고하고 승인을 기다린다. 승인 후 Docker D 실기 검증을 완료한 다음 Goal 9의 정기 백업과 관측성으로 진행한다.
