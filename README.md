# CAR IN (카인)

> **"차 안에서 시작된 노래, 오래도록 남을 추억."**

차량의 이동 정보와 음악을 연결해 **추억의 장소에서 그날의 노래를 다시 들려주고**, 함께한 음악을 **노래방과 여행 앨범**으로 즐기는 차량 특화 음악 서비스입니다.
현대자동차그룹 Pleos Connect 차량용 앱 개발 공모전 제출작 — 1차 GitHub 제출용 **실제 동작하는 최소 MVP**.

## 서비스 구조

| 서비스 | 내용 | 이번 MVP |
|---|---|---|
| **▶ 카인 플레이리스트** | 기존 음악 플레이어 + **Memory Route**(목적지 기반 추억 음악 추천) | ✅ 구현 |
| **🎙 카인 노래방** | 안전한 주차 상태에서 사진 배경 + 가사와 함께 음악 감상 | ✅ 최소 체험 (정적 데모 가사) |
| **✧ 카인 추억** | 여행 제목·장소·날짜·노래·사진으로 남기는 여행 기록 | ✅ 구현 |
| **CAR IN Together** | QR 기반 동승자 공동 선곡 | 🗓 2차 |

## 실제 구현 완료 기능

상세 구분은 [docs/feature-status.md](docs/feature-status.md), 변경 전 상태는 [docs/mvp-audit.md](docs/mvp-audit.md) 참고.

**✅ 에뮬레이터에서 동작 확인**
- 음악 재생·일시정지·이전/다음·플레이리스트 선택 (Media3 ExoPlayer)
- 추억 등록(장소·제목·날짜·곡) → 로컬 영속 저장 → 앱 재시작 후 유지
- 목적지 ↔ 추억 지역 매칭(토큰 정규화, substring 오매칭 방지) → 메인 추억 카드가 "지난번 이 길에서 들었던 노래가 있어요. 다시 들어보시겠어요?"로 제안(최근 여행 우선, 같은 목적지 1회) → **그날의 노래 실제 재생**
- 카인 추억: 사진 카드 목록·상세·삭제·듣기, 사진 연결, 상세 사진 표시, 추억 사진으로 노래방 진입
- 카인 노래방: 주차 확인 → 전체 화면, 사용자 사진/기본 배경, 정적 데모 가사(현재 줄 강조, 터치로 줄 이동), 진행 바, 기존 플레이어와 재생 상태 공유, 종료 후 복귀
- 디자인: 팀 목업·브랜드 팔레트 기준 다크 로즈 UI (탭 · 추억 카드 · 하단 미니 플레이어 · 공통 다이얼로그), 아이콘·로고는 코드로 그림
- 주행 안전: AAOS `CarUxRestrictions` 조회 결과가 **주차**일 때만 등록·삭제·노래방·사진 선택 허용 (확인 불가 시에도 제한, 음악 재생은 항상 허용)

**🔎 SDK 조회 검증** — NaviHelper SDK 연결 및 `getDestinationInfo()` 왕복 성공(에뮬레이터에 목적지가 없어 "설정된 목적지 없음"까지 확인). Vehicle SDK 좌석 조회(기존).

**🧪 데모로 시연** — 목적지 매칭은 에뮬레이터에 내비 목적지를 설정할 수단이 없어 **데모 목적지 선택**으로 시연 (UI에 `[DEMO]` 표기, SDK 목적지가 있으면 자동 우선). 노래방 가사는 **팀 자체 제작 정적 문구**이며 음악과 동기화되지 않음.

**⏳ 미검증** — 실제 주행 중(DO 필요) 상태의 기능 차단(에뮬레이터가 user build라 주행 상태 주입 불가), 실제 내비 목적지 데이터로의 매칭.

**🚫 범위 외(2차)** — Gleo AI, QR 공동 선곡, 서버/로그인, SNS 공유, Drive Story, GPS 경로 저장, 외부 스트리밍, 자동 영상 편집, 마이크·MR·점수.

## Memory Route 시연 방법

단계별 안내: [docs/demo-guide.md](docs/demo-guide.md)

1. 앱 실행 → 하단 미니 플레이어의 **▶** 로 음악 재생 확인
2. **+ 추억 등록**(장소 `강릉`, 노래 선택) 또는 **데모 데이터 만들기**
3. 내비 목적지를 강릉으로 설정하거나 **데모 목적지 선택 → 강릉역**
4. 메인 추억 카드가 "지난번 이 길에서 들었던 노래가 있어요"로 바뀌는지 확인
5. **▶ 재생하기** → 플레이어가 해당 곡 재생
6. **✧ 카인 추억** 탭에서 기록 카드 확인 · 자세히 · 사진 연결 · 노래방
7. (추가) **부산역** 선택 시 "연결된 추억이 없어요", 같은 목적지 재선택 시 제안 반복 없음, 재실행 후 기록 유지

## 실행 환경 및 설정

```bash
# 설치 / 실행
adb install -r apk/CARIN_CAR_IN_release.apk
adb shell am start -n com.carin.noraebang/.MainActivity
adb logcat -s CARIN_NAVI CARIN_VEHICLE CARIN_SAFETY CARIN_MEMORY CARIN_KARAOKE CARIN_PHOTO AndroidRuntime:E

# 소스 빌드
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # PlaceMatcher 단위 테스트
./gradlew assembleRelease          # keystore.properties 가 있으면 서명됨
```

| 항목 | 값 |
|---|---|
| 언어 / 패키지 | Java 11 / `com.carin.noraebang` (applicationId 유지) |
| Android SDK | compileSdk 37 · targetSdk 37 · minSdk 28 · Build-Tools 36.0.0 |
| JDK | OpenJDK 25.0.4 (Android Studio JBR) |
| Gradle / AGP | 9.6.0 / 9.5.1 |
| 라이브러리 | Media3 ExoPlayer·Session 1.11.1, `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5` |
| Pleos Connect Emulator | AVD `Pleos_Connect_v2.1.0` — `RELEASE.release_connect_v2.1.0`, Android 14 (API 34), automotive, user build |
| 확인한 화면 규격 | **2560 × 1440, 160 dpi** (앱은 우측 약 2/3 영역, 가로형) |
| 호스트 | Windows 11 |

release 서명: 루트에 `keystore.properties`(gitignore됨)를 두면 `app/build.gradle.kts`가 읽어 서명합니다. 형식은 `keystore.properties.example`. 키스토어는 저장소 밖에 보관하고 **절대 커밋하지 마세요**.

## APK

| 항목 | 값 |
|---|---|
| 경로 | `apk/CARIN_CAR_IN_release.apk` (팀명 `CARIN`) |
| 빌드 | `assembleRelease`, R8 최적화, 약 13.9 MB |
| 서명 | APK Signature Scheme v2, `CN=CAR IN NORAEBANG, OU=CARIN` (`apksigner verify` 통과) |
| 검증 | Pleos Connect Emulator v2.1.0에서 설치·실행·핵심 흐름 확인 |

## 프로젝트 구조

```
app/src/main/java/com/carin/noraebang/
├── MainActivity.java            # 헤더·탭·추억 카드·미니 플레이어 + Memory Route + 노래방 오버레이
├── memory/   SongCatalog, Memory, MemoryStore, PlaceMatcher,
│             MemoryListPage, MemoryDetailDialog, MemoryRegisterDialog
├── ui/       CarinTheme, CarinIcon, PillButton, CircleButton, LogoView,
│             RouteLineView, ProgressLine, CarinDialog, ImageLoader   # 디자인 시스템
├── karaoke/  KaraokePanel, DemoLyrics, PhotoPicker
├── navi/     NaviDestinationClient    # NaviHelper SDK 래퍼
└── safety/   DrivingSafetyGuard       # CarUxRestrictions 조회
app/src/debug/java/.../NaviProbeActivity.java   # SDK 진단용 (debug 전용)
apk/CARIN_CAR_IN_release.apk
app/src/main/assets/backgrounds/   # 기본 이미지 슬롯 (hero·karaoke·memory·default)
docs/ mvp-audit.md · feature-status.md · demo-guide.md · change-summary.md · implementation-summary.md
```

## 공개 전 확인 사항

- `keystore.properties`, `*.jks`, `local.properties`, IDE 개인 설정은 `.gitignore`에 포함되어 있습니다.
- 번들 음원 `res/raw/carin_song_0N.mp3`와 데모 가사는 팀 보유/자체 제작물만 사용합니다.
- 이 디렉터리는 아직 Git 저장소가 아닙니다. `git init` 후 첫 커밋 전에 `git status`로 위 파일이 제외되는지 확인하세요.
