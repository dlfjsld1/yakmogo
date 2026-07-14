# Docker 전환 준비: 8081 shadow container

## 현재 상태

`feature/docker-enhancement`에서 Docker image artifact와 18081 shadow 배포 검증을 완료했다. 기존 systemd 8081 중지, 컨테이너 8081 전환, enhancement 자동 container 배포는 아직 수행하지 않았다.

## 한 문장 요약

현재 CI의 test·통합 JAR·manifest·checksum 경계는 유지하면서 배포 단위만 ARM64 Docker image tar로 확장하고, 기존 8081과 운영 8080을 그대로 둔 채 18081에서 같은 enhancement DB 연결과 HTTP 계약을 검증한다.

## 작업 배경

Goal 8은 JAR·systemd 기반 자동 배포와 rollback을 완성했다. 이후 Pi 운영이 Docker Compose 중심으로 정리될 예정이므로 Goal 9의 백업·모니터링을 호스트 JAR 방식에 더 얹기 전에 container 경계를 먼저 검증한다.

Pi 실환경 조사 결과:

- ARM64, Docker 29.4.3, Compose v5.1.3
- Docker data root `/mnt/docker/docker-data`, 별도 ext4 디스크 `/dev/sda1`
- 기존 Compose project: Home Assistant, Jellyfin, Syncthing, Transmission/Gluetun, Uptime Kuma
- MariaDB는 host `127.0.0.1:3306`에만 listen
- 8080과 8081은 systemd Java process가 사용 중
- 18081은 비어 있음
- `yakmogo-runner`는 Docker group에 속하지 않음

## 이번 범위

- Java 21 ARM64 runtime image
- 통합 JAR을 포함한 Docker image tar와 SHA-256 생성
- 기존 release manifest에 image metadata 추가
- 비밀값을 image에 넣지 않는 Compose 정의
- host network와 18081을 사용하는 shadow container
- non-root user, read-only root filesystem, capability 제거, no-new-privileges
- runner가 Docker socket에 접근하지 않는 고정 root helper
- 8080·systemd 8081·다른 container ID 불변 검사
- CI shell·Compose 안전 계약 검사
- 상세 한국어 문서와 시행착오 기록

## 제외 범위

- systemd `yakmogo-enhancement.service` 중지·비활성화
- host 8081을 container에 넘기는 cutover
- container 기반 enhancement 자동 배포 활성화
- MariaDB listen address 변경 또는 DB container화
- 운영 8080과 운영 DB `yakmogo` 변경
- 기존 Docker project 변경
- image 자동 삭제와 backup schedule
- Android 코드·API·테이블

## 왜 bridge가 아니라 host network인가

일반 bridge container에서 `localhost`는 host Pi가 아니라 그 container 자신이다. 현재 MariaDB는 `127.0.0.1:3306`만 listen하므로 bridge container는 접근할 수 없다.

검토한 대안:

1. MariaDB를 `172.17.0.1`에도 bind: DB 설정·firewall 변경이 필요하다.
2. MariaDB도 container화: 데이터 이전과 복구 검증 범위가 너무 커진다.
3. host network: 기존 datasource `localhost`를 유지하고 DB 설정을 바꾸지 않는다.

이번 shadow 단계에서는 3번을 선택한다. 네트워크 격리는 약해지므로 container의 capability를 모두 제거하고 애플리케이션 자체도 non-root로 실행한다.

## image 생성 흐름

```text
web enhancement build
  -> backend test + bootJar
  -> 통합 JAR 계약 검증
  -> ARM64 eclipse-temurin Java 21 image
  -> Docker image tar
  -> image tar SHA-256
  -> GitHub artifact
```

registry를 사용하지 않는 이유는 shadow 단계에서 GHCR package 권한·로그인 credential을 새로 만들 필요가 없기 때문이다. single-platform image tar는 GitHub artifact로 전달하고 Pi의 root helper가 고정 staging에서만 `docker load`한다.

## runtime 비밀값 흐름

```text
image: 애플리케이션 JAR만 포함
compose: 비밀값 이름과 안전 설정만 포함
Pi root Docker Compose:
  /etc/yakmogo/yakmogo-enhancement.env 읽기
  -> container environment 주입
```

runner는 기존과 마찬가지로 환경파일을 읽지 못한다. Docker group은 host root와 사실상 같은 권한이므로 runner를 해당 group에 추가하지 않는다.

## shadow HTTP 흐름

```text
기존 systemd enhancement: host 8081 -> yakmogo_enhancement
shadow container:          host 18081 -> yakmogo_enhancement
production systemd:       host 8080 -> yakmogo
```

shadow container는 다음 값을 Compose에서 강제로 덮어쓴다.

```text
SPRING_PROFILES_ACTIVE=enhancement
SERVER_PORT=18081
FRONTEND_URL=http://192.168.50.192:18081
TELEGRAM_BOT_ENABLED=false
SCHEDULING_ENABLED=false
```

따라서 같은 enhancement DB를 읽지만 Telegram polling과 scheduler는 실행하지 않는다. Flyway는 validate와 이미 적용된 migration 확인만 수행하며 새 migration이 있는 Goal에서는 별도 승인이 필요하다.

## container 방어 설정

- 실행 user `10001:10001`
- root filesystem read-only
- `/tmp`만 64 MiB tmpfs, `noexec,nosuid`
- Linux capability 전체 제거
- `no-new-privileges`
- host port publish 없이 host network
- JSON log 10 MiB × 3개 제한
- `restart: unless-stopped`

## shadow 성공 조건

1. image tar와 manifest SHA-256 일치
2. image architecture가 `arm64`
3. backend SHA, web SHA, JAR SHA label 일치
4. container user가 `10001:10001`
5. 18081 `/` 200
6. manifest의 JavaScript bundle 200
7. 18081 보호 API 401
8. 8080 PID·JAR SHA·HTTP 200 불변
9. systemd 8081 PID·JAR SHA·HTTP 200 불변
10. 다른 실행 container의 name/ID 집합 불변

## 실패 처리

첫 shadow가 실패하면 container와 `.release.env`를 제거한다. 이전 정상 shadow가 있으면 이전 image·bundle 설정을 복원하고 readiness를 다시 확인한다. 어떤 실패 경로에도 `systemctl stop/start/restart` 명령은 없다.

## 변경 파일

- `deploy/docker/Dockerfile`: Java 21 non-root runtime image
- `deploy/docker/compose.yml`: 18081 shadow runtime 보안 계약
- `scripts/ci/build-container-candidate.sh`: ARM64 image tar와 manifest 생성
- `scripts/ci/test-container-contract.sh`: Docker/Compose/helper 안전 계약
- `deploy/cicd/yakmogo-enhancement-container-shadow`: 18081 전용 root helper
- `deploy/cicd/install-enhancement-container-shadow.sh`: Compose·helper·sudoers 설치
- `.gitattributes`: Linux 배포 파일 LF 강제
- `.github/workflows/release-candidate.yml`: image artifact 생성

## 다음 승인 지점

shadow 검증 뒤 다음 내용을 보고하고 멈춘다.

- image SHA와 architecture
- container 보안 설정
- 18081 인수 테스트
- 8080·systemd 8081·다른 container 불변
- MariaDB와 Flyway 동작
- container log의 민감정보 비노출
- cutover·rollback 명령과 예상 중단 시간

승인 전에는 host 8081을 container에 넘기지 않는다.

## 실제 shadow 검증 결과

2026-07-14 `feature/docker-enhancement` 커밋 `9e1deeb` 기준으로 검증했다.

- feature CI: [run 29322547273](https://github.com/dlfjsld1/yakmogo/actions/runs/29322547273) 성공
- backend test·bootJar·통합 SPA JAR 검증: 성공
- image base: `eclipse-temurin:21.0.11_10-jre-noble`
- 실제 base manifest digest: `sha256:373787d1d45a87f084fda43e7de0e9acf5eedee049446efac738f13587ec4c64`
- image ID: `sha256:7a6b733c70cc6eecedfc529942914fab42990ea6a9bcf5eab0a6d4aad3b4cde4`
- image architecture/OS: `arm64` / `linux`
- image tar: 약 159 MiB
- image tar SHA-256: `5291a042d935e877757aadee3e9e514fa185d82fd82520bf6991be339cb2308e`
- image 내부 JAR SHA-256 label: `2b3cf8054bf4ef6990a61ef7516157c716aed8537ea8d29d29b335f6f3a36602`
- 실행 user: `10001:10001`
- network: `host`
- read-only root filesystem: true
- capability: `ALL` drop
- no-new-privileges: true
- `/tmp`: 64 MiB, noexec, nosuid

HTTP와 UI:

- 8080 `/`: 200
- systemd 8081 `/`: 200
- container 18081 `/`: 200
- container 18081 예상 JS: 200
- container 18081 보호 API: 401
- React 화면: `yakmogo-web`, 관리자 인증 입력과 버튼 렌더링 성공
- 브라우저 console error: 0

DB와 프로세스:

- Flyway 3개 migration validation 성공
- `yakmogo_enhancement` schema version 2 확인
- 운영 8080 PID `1794958` 불변
- systemd 8081 PID `1967090` 불변
- 운영 JAR SHA-256 `b7670b8089e887181f78592e88dbf7bb12f45427685dfa3deb3a6d73bcdc79ce` 불변
- enhancement JAR SHA-256 `7a4623f56ee544951c85c1c8b73e19276ba49ec0ca52e60251e4477b0b61c92c` 불변
- Syncthing, Home Assistant, Jellyfin, Uptime Kuma, Transmission, Gluetun container ID 불변

관찰된 잔여 위험:

- 현재 Flyway는 MariaDB 11.8이 공식 확인 범위 11.2보다 새 버전이라는 경고를 출력한다. migration validation은 성공했지만 Goal 9 이전 후속 검토 대상으로 남긴다.
- image tar는 첫 build 기준 159 MiB이므로 보관·삭제 정책을 Goal 9에서 정해야 한다.
- Goal 9에서 Uptime Kuma monitor를 추가할 때 이름뿐 아니라 감시 대상, 정상 조건, 장애 영향, 확인 명령과 Yakmogo 복구 문서를 설명 또는 비고에 기록한다.
- host network는 MariaDB loopback 연결을 유지하는 대신 network namespace 격리를 줄인다.
