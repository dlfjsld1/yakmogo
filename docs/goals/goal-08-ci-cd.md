# Goal 8: CI/CD와 8081 자동 배포·롤백

## 현재 상태

**승인 지점 2 대기.** 서버에 접속하지 않는 CI 검사와 통합 JAR 조립·검증 도구를 구현하고 로컬에서 검증했다. 라즈베리파이 배포 credential 연결, 8081 자동 재시작, 자동 롤백 활성화는 아직 구현하거나 실행하지 않았다.

Goal 7은 backend `enhancement`에 merge commit `7dc6ea1`로 병합했고 [통합 CI run 29314937366](https://github.com/dlfjsld1/yakmogo/actions/runs/29314937366)이 성공한 뒤 Goal 8을 시작했다.

## 한 문장 요약

backend와 web을 재현 가능하게 함께 빌드하고 SPA 누락·오래된 asset·8081 안전 profile을 검사해 SHA-256이 포함된 release candidate를 만드는 단계까지 자동화했으며, 외부 상태를 바꾸는 배포 단계는 사용자 승인 전 비활성 상태다.

## 작업 배경

Goal 1~7의 8081 배포는 다음 절차를 수동으로 반복했다.

1. web `npm run build`
2. backend `bootJar`
3. web `dist`를 JAR의 `BOOT-INF/classes/static`에 overlay
4. 후보 checksum 계산
5. Raspberry Pi에 전송
6. 기존 JAR 백업
7. 8081만 재시작
8. 최대 60초 HTTP polling
9. 실패 시 기존 JAR 복원

이 절차는 실제로 작동했지만 사람이 오래된 web bundle을 넣거나, overlay 과정에서 과거 hash asset을 JAR에 남기거나, systemd `active`만 보고 너무 일찍 성공 처리할 수 있다. Goal 8은 먼저 빌드 산출물의 계약을 자동 검사하고, 승인 뒤에만 원격 변경 단계를 연결한다.

## 승인 전 구현 범위

- 기존 backend test·bootJar CI 유지
- feature CI에서 release tooling shell 문법과 정상·실패 fixture 실행
- `-PwebDistDir` Gradle 입력으로 web `dist`를 정식 resource source로 사용
- 기존 SPA index·asset을 제외해 오래된 hash asset이 남지 않도록 처리
- 통합 JAR의 index·전체 web 파일·asset 집합·8081 profile 검증
- SHA-256과 release candidate 생성
- backend `enhancement` push에서만 실행될 release-candidate workflow 작성
- workflow 동시 실행 직렬화
- artifact 7일 보관
- CI/CD 설계와 배포 런북 문서화

## 승인 전 제외 범위

- GitHub Actions secret 등록
- SSH private key 생성·배포
- 라즈베리파이 CI 전용 사용자 생성
- sudoers 변경
- 원격 staging·backup 디렉터리 생성
- `systemctl stop/start/restart` 자동 실행
- 실패 artifact를 이용한 실제 8081 rollback 시험
- 자동 배포 workflow의 SSH/deploy job 활성화
- 운영 8080, 운영 DB, main 변경

## 통합 JAR 생성 방식

### 변경 전 overlay 방식

기존 수동 방식은 완성된 backend JAR에 `jar uf`로 web 파일을 덮었다. 새 index는 들어가지만 기존 `assets/index-OLD.js`가 남는다. 브라우저가 새 index만 사용하면 당장 보이지 않지만 JAR이 실제 web 산출물과 일치하지 않고 크기와 조사 대상이 계속 늘어난다.

### 변경 후 Gradle resource 입력

```text
web npm build
  -> yakmogo-web/dist
  -> ./gradlew clean test bootJar -PwebDistDir=<dist>
     -> 기존 static/index.html, static/assets/**, static/vite.svg 제외
     -> 새 dist를 static/에 포함
     -> backend images는 유지
```

`webDistDir`를 주지 않은 일반 backend CI는 기존 resource를 사용하므로 feature backend 검증 방식은 바뀌지 않는다. release candidate 생성 때만 외부 web 산출물을 입력한다.

## 산출물 검증 계약

`verify-integrated-jar.sh`는 다음 조건을 모두 확인한다.

- web `dist/index.html`과 JAR의 index가 byte 단위로 동일
- web `dist`의 모든 파일이 JAR에 존재
- JAR의 `static/assets` 파일 집합이 web `dist/assets`와 정확히 동일
- index가 JavaScript bundle을 참조
- `application-enhancement.yml`의 port가 8081
- datasource가 `yakmogo_enhancement`
- Telegram bot과 scheduler 비활성 설정 존재

fixture 테스트는 세 경우를 실행한다.

| fixture | 기대 결과 |
|---|---|
| index와 asset이 모두 일치 | 성공 |
| JAR에 과거 `old.js`가 남음 | 실패 |
| index가 참조하는 `app.js` 누락 | 실패 |

## 현재 workflow

`.github/workflows/release-candidate.yml`은 `enhancement` push만 수신한다. 현재 feature 브랜치에서는 실행되지 않으며, 아직 원격 배포 job이 없다.

```text
backend enhancement push
  -> backend checkout (해당 merge SHA)
  -> web enhancement checkout
  -> Java 21 / Node 22
  -> web lint + test + build
  -> backend test + MariaDB migration test + bootJar
  -> integrated JAR contract 검증
  -> SHA-256 생성
  -> GitHub artifact 7일 보관
```

`concurrency.group=enhancement-release`, `cancel-in-progress=false`로 두 enhancement release가 동시에 실행되지 않게 했다. 앞 release를 취소하면 배포 중 상태가 불명확해질 수 있으므로 나중 실행이 기다리는 정책이다.

## 로컬 검증 결과

실제 Goal 6 web `dist`와 Goal 7 backend를 사용했다.

- `clean test bootJar`: 성공
- web lint: 성공
- web Vitest: 2 files, 7 tests 성공
- web production build: 성공
- 통합 JAR 검증: 성공
- 오래된 asset fixture: 의도한 실패
- 누락 asset fixture: 의도한 실패
- 최종 로컬 통합 JAR SHA-256: `622e8719188238475cc619aef88197f57c8f22c30dafa566b6ab93dbb7aac22a`
- 서버 전송: 하지 않음
- 서비스 재시작: 하지 않음

checksum은 CI에서 만든 한 artifact와 원격으로 전송된 바로 그 파일이 동일한지 확인하는 용도다. 별도 시점에 다시 빌드한 JAR이 같은 hash일 것이라고 가정하지 않는다. 승인 후 배포 판단에는 CI artifact의 checksum을 사용한다.

## 승인 후 구현할 전체 흐름

상세 명령과 파일 책임은 [8081 CI/CD 배포 런북](../runbooks/enhancement-cicd.md)에 기록했다.

```text
검증·통합 JAR·checksum
  -> CI 전용 SSH key로 staging 업로드
  -> 원격 SHA-256 비교
  -> root-owned deploy helper 호출
  -> JAR·unit·start script·env 백업
  -> 후보 profile과 고정 경로 재검사
  -> yakmogo-enhancement.service만 중지/시작
  -> 최대 60초 readiness
  -> / 200 + 예상 JS 200 + 보호 API 401
  -> 성공 기록 보존
  -> 실패하면 기존 파일 복원 후 8081 health 재검증
```

## 사용할 CI secret 이름

승인 후 다음 이름으로 등록할 계획이다. 실제 값은 문서·workflow·로그에 쓰지 않는다.

| secret | 용도 |
|---|---|
| `ENHANCEMENT_SSH_HOST` | Raspberry Pi 주소 |
| `ENHANCEMENT_SSH_PORT` | SSH port |
| `ENHANCEMENT_SSH_USER` | CI 전용 제한 사용자 |
| `ENHANCEMENT_SSH_PRIVATE_KEY` | CI 전용 private key |
| `ENHANCEMENT_SSH_KNOWN_HOSTS` | 고정 host public key |

`SUDO_PASSWORD`, DB 비밀번호, Telegram token, 관리자 비밀번호를 GitHub secret으로 추가하지 않는다. 원격 root helper는 허용된 고정 명령만 password 없이 실행하도록 좁은 sudoers rule을 사용한다. 애플리케이션 비밀값은 기존 `/etc/yakmogo/yakmogo-enhancement.env`에만 남는다.

## 배포 대상과 경로

| 구분 | 계획 경로 |
|---|---|
| CI staging | `/var/lib/yakmogo-deploy/incoming/<backend-sha>/` |
| 실행 JAR | `/home/pi/myprojects/yakmogo-enhancement/yakmogo-0.0.7-SNAPSHOT.jar` |
| systemd unit | `/etc/systemd/system/yakmogo-enhancement.service` |
| start script | `/home/pi/myprojects/yakmogo-enhancement/start.sh` |
| 비밀 환경파일 | `/etc/yakmogo/yakmogo-enhancement.env` |
| 백업 | `/var/backups/yakmogo/enhancement/<timestamp>-<backend-sha>/` |
| deploy helper | `/usr/local/sbin/yakmogo-enhancement-deploy` |

후보 artifact에는 backend SHA, web SHA, JAR basename, SHA-256, 예상 JS bundle 경로를 manifest로 기록할 계획이다.

## health check 조건

다음 조건을 모두 만족해야 성공이다.

1. 원격 후보 SHA-256이 CI checksum과 동일
2. 후보 JAR profile이 8081·`yakmogo_enhancement`·bot false·scheduler false
3. `yakmogo-enhancement.service`가 active
4. 최대 60초 안에 `GET /`가 200
5. 새 index가 참조하는 예상 JS bundle이 200
6. `GET /api/v1/users`가 인증 없이 401
7. 운영 `yakmogo.service`의 PID와 JAR checksum이 배포 전후 동일
8. 8080 `/`가 계속 200

## rollback 조건

다음 중 하나면 자동 rollback 대상으로 설계한다.

- 업로드 checksum 불일치
- 후보 profile 경계 불일치
- 8081 service start 실패
- 60초 readiness timeout
- index 또는 예상 JS bundle 불일치·비정상 HTTP
- 보호 API가 401이 아님
- 운영 8080 PID·checksum·HTTP 불변 조건 위반

후보 교체 전 실패는 실행 파일을 건드리지 않고 종료한다. 교체 후 실패는 백업 JAR·unit·start script·environment file을 원래 권한으로 복원하고 `daemon-reload`, 8081 시작, 이전 health 확인 순서로 처리한다.

Flyway migration은 JAR rollback으로 되돌리지 않는다. migration은 별도 DB 변경이며 자동 역방향 SQL은 데이터 손실 위험이 있다. 따라서 새 migration이 포함된 release는 적용 전 backup과 forward/backward compatibility를 별도로 검증해야 한다.

## 8080과 main이 영향을 받지 않는 근거

- workflow trigger는 backend `enhancement`뿐이다.
- workflow 권한은 `contents: read`다.
- target service 이름을 `yakmogo-enhancement.service`로 고정한다.
- target JAR과 backup 경로에 `yakmogo-enhancement` 또는 `/var/backups/yakmogo/enhancement`만 허용한다.
- deploy helper는 전달받은 임의 service/path를 실행하지 않는다.
- 배포 전후 운영 PID·JAR checksum·HTTP 200을 비교한다.
- main push·tag·release trigger를 추가하지 않는다.
- 운영 DB URL을 workflow나 deploy helper에 전달하지 않는다.

## 알려진 위험과 승인 시 결정할 내용

- GitHub-hosted runner에서 가정용 Raspberry Pi로 inbound SSH가 가능한 네트워크 경로가 필요하다. 현재 서버가 LAN 주소만 사용하므로 외부 공개 SSH 대신 self-hosted runner, VPN tunnel, outbound pull 방식도 비교해야 한다.
- CI 전용 사용자와 narrow sudoers 설치는 Raspberry Pi 운영 파일 변경이다.
- 자동 rollback이 environment file을 복원할 때 현재 비밀값을 로그로 읽지 않도록 root 내부 copy만 사용해야 한다.
- JAR rollback은 DB migration rollback이 아니다.
- 실제 실패 rollback 검증은 8081만 사용하지만 짧은 서비스 중단이 발생한다.

## 다음 승인 후 수행할 작업

1. 네트워크 접근 방식을 확정한다.
2. CI 전용 key·사용자·known_hosts를 구성한다.
3. root-owned deploy helper와 narrow sudoers를 설치한다.
4. workflow에 upload·remote deploy job을 추가한다.
5. 정상 8081 자동 배포를 검증한다.
6. 의도적으로 health가 실패하는 후보로 rollback을 검증한다.
7. 정상 artifact로 8081을 복구한다.
8. secret masking, backup, 배포 이력, 8080 불변을 확인한다.
