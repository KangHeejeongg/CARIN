# 변경 요약 (Change Summary)

최신 변경이 위에 옵니다.

---

# 2차 변경: 목업 기준 UI 리디자인

작업일: 2026-11-15 · 기준: 팀 목업 2종(`노을빛 드라이브, CAR IN 추억`, `현대자동차 CAR IN 음악 드라이브 UI 포스터`)과 브랜드 컬러 팔레트 시안

## 1. 수정 이유

- 기존 화면(남색 + 민트 강조색, 세로로 쌓인 단일 스크롤, 시스템 다이얼로그·보라색 ActionBar)이 목업의 노을 로즈 다크 톤·화면 구조와 달랐다.
- 진행 예정 플랜(`feature-status.md`의 향후 개발)에는 "다이얼로그 다크 테마 통일" 외에 디자인 항목이 없어, 목업 기준으로 전체 화면을 다시 구성했다.
- 기능 로직(플레이어·목적지 매칭·주행 안전·사진·Vehicle SDK)은 바꾸지 않는다.

## 2. 수정 파일

### 신규

| 파일 | 내용 |
|---|---|
| `ui/CarinTheme.java` | 브랜드 팔레트 토큰 + 뷰 팩토리 (색 정의를 한 곳으로 모음) |
| `ui/CarinIcon.java` | 코드로 그리는 라인 아이콘 17종 (이모지 대체) |
| `ui/PillButton.java`, `ui/CircleButton.java` | 알약 버튼(주·보조·탭), 원형 버튼(코랄 재생·유리·투명) |
| `ui/LogoView.java` | CAR IN 워드마크 |
| `ui/RouteLineView.java` | 장식용 경로선 (실제 GPS 경로 아님) |
| `ui/ProgressLine.java` | 재생 진행 바 |
| `ui/CarinDialog.java` | 다크 로즈 공통 다이얼로그 |
| `ui/ImageLoader.java` | 사진 비동기 로딩 + assets 기본 이미지 슬롯 |
| `memory/MemoryListPage.java` | "카인 추억" 탭 사진 카드 그리드 |
| `memory/MemoryDetailDialog.java` | 추억 상세 (사진 + 경로선 + 메타 + 액션) |
| `assets/backgrounds/README.txt` | 기본 이미지 슬롯 안내 |

### 수정 · 삭제

| 파일 | 변경 | 이유 |
|---|---|---|
| `MainActivity.java` | 화면을 헤더(상태 칩·로고·목적지 칩) → 탭 → 페이지(플레이리스트/추억) → 하단 미니 플레이어로 재구성. 히어로 카드(`renderHero()`), 진행 바 타이머, `togglePlay/skipPrevious/skipNext` 분리. 추억 발견 AlertDialog 팝업 → 히어로 카드 제안. 조수석 조회는 접히는 "개발 정보"로 이동 | 목업 구조 반영. 재생 로직을 한 곳에 두고 미니 플레이어·노래방이 공유 |
| `karaoke/KaraokePanel.java` | 현재 줄 코랄 강조 + 다음 줄 가사, 탭으로 줄 이동, 진행 바·시간, 원형 컨트롤, 로즈 스크림 | 목업 노래방 화면 반영. 자동 진행을 넣지 않아 "동기화 없음" 표기와 일치 |
| `karaoke/DemoLyrics.java` | `linesFor()` 추가 | 줄 단위 표시 |
| `memory/MemoryRegisterDialog.java` | CarinDialog 기반, 곡 선택을 스피너 → 알약 탭으로 | 디자인 통일 |
| `memory/MemoryListDialog.java` | **삭제** | 목록은 탭 화면, 상세는 `MemoryDetailDialog`로 대체 |
| `res/values(-night)/themes.xml`, `colors.xml` | `NoActionBar` + 브랜드 색, 템플릿 보라/청록 색 제거 | ActionBar 제거, 상태바·창 배경 통일 |
| `CLAUDE.md`, `docs/*.md` | 새 구조 반영 | 문서 동기화 |
| `apk/CARIN_CAR_IN_release.apk` | 리디자인 빌드로 교체 (같은 release 키) | 제출물 갱신 |

변경하지 않은 것: `SongCatalog`, `Memory`, `MemoryStore`, `PlaceMatcher`, `PhotoPicker`, `NaviDestinationClient`, `DrivingSafetyGuard`, 좌석 조회 코드, Manifest, Gradle 버전/의존성, 서명 설정, 패키지명.

## 3. 변경 기능

| 기능 | 변경 전 | 변경 후 |
|---|---|---|
| 테마 | 남색 + 민트, 보라색 ActionBar | 다크 로즈 팔레트, ActionBar 없음 |
| 메뉴 | 사각 버튼 3개(이모지) | 아이콘 알약 탭, 선택 탭 코랄 강조 |
| 플레이어 | 큰 "♫" 커버 + 텍스트 버튼 | 하단 고정 미니 플레이어 + 진행 바 |
| 추억 발견 | 시스템 팝업 + 하단 카드 목록 | 히어로 카드 제안(재생하기/나중에), 여러 개면 점으로 전환 |
| 카인 추억 | 목록 다이얼로그 | 탭 화면 사진 카드 그리드 + 상세 다이얼로그 |
| 노래방 | 전체 가사 스크롤 | 현재 줄 강조, 진행 바, 원형 컨트롤 |
| 주행 상태 | 패널 안 긴 문구 | 헤더 칩 |
| 기본 배경 | 단색 그라데이션 | 노을 그라디언트 + assets 이미지 슬롯 |

## 4. 영향 범위

- **저장 데이터**: 변경 없음 (기존 추억·노래방 배경 그대로 표시).
- **서명/설치**: 같은 release 키 → 기존 설치 위에 업데이트, 데이터 유지 확인.
- **동작 규칙**: 같은 목적지 1회 제안, `(SDK)`/`[DEMO]` 표기, 주차 상태에서만 제한 기능 — 모두 유지.

## 5. 확인 결과 (Pleos Emulator v2.1.0, release 빌드)

| 확인 항목 | 결과 |
|---|---|
| `assembleDebug` / `testDebugUnitTest` / `assembleRelease` | ✅ / ✅ / ✅ |
| 메인 화면 (헤더·탭·히어로·패널·미니 플레이어) | ✅ |
| 데모 목적지 창 → 강릉역 → 히어로 제안 → 재생하기 | ✅ |
| 노래방 진입 · 가사 강조 · 진행 바 · 닫기 | ✅ |
| 카인 추억 탭 카드 · 상세 다이얼로그 | ✅ |
| 크래시 로그 (`AndroidRuntime:E`) | 없음 |
| 기본 이미지 슬롯 실제 이미지 표시 | ⏳ 이미지 미추가 (그라디언트 대체 표시만 확인) |
| 주행 중 차단 | ⏳ 에뮬레이터에서 재현 불가 (변경 없음) |

---

# 1차 변경: CAR IN NORAEBANG → CAR IN

작업일: 2026-11-14 · 대상: 기존 Memory Route MVP 위에 CAR IN 서비스 구조 적용

## 1. 수정 이유

- 메인 서비스 브랜드가 **CAR IN(카인)** 으로 바뀌어 앱 표시명·헤더·메뉴를 새 구조(카인 플레이리스트 / 카인 노래방 / 카인 추억)로 재편해야 했다.
- 기존 MVP(음악 재생, 추억 등록·목록·삭제, 목적지 매칭, 그날의 노래 재생)는 이미 동작하므로 **삭제하거나 다시 만들지 않고 재사용**한다.
- P1으로 카인 노래방 최소 체험(사진 배경 + 정적 데모 가사 + 기존 플레이어 공유)과 카인 추억 사진 연결을 추가한다.
- 패키지명 `com.carin.noraebang`과 `applicationId`는 유지한다 (저장 데이터·서명 호환).

## 2. 수정 파일

### 신규

| 파일 | 내용 |
|---|---|
| `app/src/main/java/com/carin/noraebang/karaoke/KaraokePanel.java` | 노래방 전체 화면 오버레이(FrameLayout). 배경 사진 ImageView + 어두운 스크림 + 곡 제목 + 가사 패널 + 이전/재생/다음 + 사진 선택/기본 배경. 재생 제어는 `Listener`로 MainActivity에 위임 |
| `app/src/main/java/com/carin/noraebang/karaoke/DemoLyrics.java` | 팀 자체 제작 정적 데모 가사 3곡. 화면에 "정적 데모 가사 · 음악과 동기화되지 않음" 표기 |
| `app/src/main/java/com/carin/noraebang/karaoke/PhotoPicker.java` | 기기에서 실제 동작하는 사진 선택 인텐트 탐색(`PICK_IMAGES` → `GET_CONTENT` → `OPEN_DOCUMENT` → `PICK`, AAOS 스텁 패키지 제외) + 선택 사진을 앱 내부 저장소에 축소 복사 |
| `docs/mvp-audit.md`, `docs/change-summary.md` | 감사 보고서, 이 문서 |

### 수정

| 파일 | 변경 | 이유 |
|---|---|---|
| `app/src/main/res/values/strings.xml` | `app_name` `CARIN` → `CAR IN` | 브랜드 변경 |
| `app/src/main/java/com/carin/noraebang/MainActivity.java` | 헤더 "CAR IN / 슬로건", 배지 `MVP`; 상단 서비스 메뉴 3개(`createServiceMenu()`); 루트를 `FrameLayout(ScrollView + KaraokePanel)`로; 플레이어 리스너가 노래방 패널의 곡/재생 상태도 갱신; `openKaraoke()/closeKaraoke()`(주차 확인, `onResume` 재확인, 뒤로가기 처리); `startPhotoPicker()/onActivityResult()`(노래방 배경·추억 사진); 문구 "나의 플레이리스트"→"카인 플레이리스트", "내 추억"→"카인 추억" | 기존 화면을 유지한 채 진입점과 노래방을 통합. 별도 Activity로 만들면 `onStop()`에서 플레이어가 해제되어 음악이 끊기므로 오버레이 방식 선택 |
| `app/src/main/java/com/carin/noraebang/memory/Memory.java` | `photoUri` 선택 필드, `withPhoto()`, JSON `optString` 읽기 | 사진 연결. 기존 저장 데이터(필드 없음)는 그대로 읽힘 |
| `app/src/main/java/com/carin/noraebang/memory/MemoryStore.java` | `update(Memory)`, `findById(String)` | 사진 연결 저장 |
| `app/src/main/java/com/carin/noraebang/memory/MemoryListDialog.java` | 제목 "카인 추억"; 카드 📷 배지; 상세에 사진 표시 + "사진 연결"/"🎤 노래방" 버튼; `Listener.onPickPhoto/onKaraoke` 추가; 상세 본문 어두운 배경 | 카인 추억 요구사항(상세·사진·노래방 연계) |
| `app/src/main/AndroidManifest.xml` | `<queries>`에 이미지 선택 인텐트 4종 | Android 11+ 패키지 가시성 — 선택기 존재 여부를 `queryIntentActivities`로 판별하기 위함 |
| `README.md`, `docs/feature-status.md`, `docs/demo-guide.md`, `docs/implementation-summary.md`, `CLAUDE.md` | CAR IN 구조 반영 | 문서 동기화 |
| `apk/` | `CARIN_CAR_IN_NORAEBANG_release.apk` → `CARIN_CAR_IN_release.apk` | 산출물 이름 규칙 (팀명 `CARIN`은 사용자 확인값) |

변경하지 않은 것: `SongCatalog`, `PlaceMatcher`, `MemoryRegisterDialog`, `NaviDestinationClient`, `DrivingSafetyGuard`, 좌석 조회 코드, Gradle 버전/의존성, 서명 설정, 패키지명.

## 3. 변경 기능

| 기능 | 변경 전 | 변경 후 |
|---|---|---|
| 앱 이름/헤더 | CARIN / "CAR IN NORAEBANG" / PARK DEMO | CAR IN / 슬로건 / MVP |
| 진입점 | 단일 화면 | 상단 메뉴 카인 플레이리스트(현재 화면) · 카인 노래방(오버레이) · 카인 추억(목록 다이얼로그) |
| 카인 플레이리스트 | 기존 플레이어 + Memory Route | 동일 (문구만 변경) |
| 카인 추억 | 목록·상세·삭제·듣기 | + 사진 연결, 상세 사진 표시, 노래방 진입 |
| 카인 노래방 | 없음 | 주차 상태에서만 진입, 사진/기본 배경, 정적 데모 가사, 기존 플레이어 공유, 뒤로가기/닫기로 복귀 |
| 주행 안전 | 등록/삭제 전 확인 | + 노래방 진입/사진 선택 전 확인, 노래방 열린 채 복귀 시 재확인 |

## 4. 영향 범위

- **음악 재생**: 플레이어 생성/해제/제어 코드 변경 없음. 리스너에 노래방 패널 갱신 2줄 추가.
- **저장 데이터**: `Memory` JSON에 `photoUri` 선택 필드만 추가 → 이전 기록 호환. 노래방 배경은 별도 prefs `carin_karaoke`.
- **파일 시스템**: 선택 사진 축소본이 `files/photos/*.jpg`에 쌓임 (삭제 시 정리는 미구현 → 후속).
- **권한**: 새 런타임 권한 없음. 사진은 시스템 선택기가 URI 권한을 부여하며 즉시 내부 복사.
- **Manifest**: `<queries>` 추가만. 컴포넌트/권한 변경 없음.

## 5. 변경 후 확인해야 할 사항

| 확인 항목 | 결과 (Pleos Emulator v2.1.0) |
|---|---|
| 앱 이름 `CAR IN` | ✅ `aapt dump badging` application-label 확인 |
| 상단 메뉴 3개 표시·동작 | ✅ |
| 기존 재생/이전/다음/플레이리스트 | ✅ |
| 노래방 진입 시 주차 확인 로그 | ✅ `UxRestrictions requiresDO=false` |
| 노래방 기본 배경 + 가사 + 데모 표기 | ✅ |
| 노래방 재생/다음 → 플레이어 상태 동기화 | ✅ 곡 제목·가사·버튼 라벨 갱신 |
| 사진 선택 → 배경 적용 → 재실행 후 유지 | ✅ 시스템 포토 피커(`PICK_IMAGES`) 사용, 내부 복사본 82KB |
| 추억 사진 연결 → 상세 표시 → 노래방 배경 | ✅ |
| 노래방 닫기 → 플레이어 복귀 | ✅ |
| Memory Route(데모 강릉역 → 팝업 → 재생) 회귀 | ✅ (아래 테스트 기록 참고) |
| 주행 중 노래방/사진/등록 차단 | ⏳ 미검증 — 에뮬레이터에서 주행 상태 재현 불가 (코드 경로: `RESTRICTED/UNKNOWN` → 차단) |
| Debug / Release 빌드 | ✅ / ✅ (release 설치·실행 확인은 README 참고) |

알려진 제약
- AAOS 포토 피커 URI는 영구 권한을 주지 않으므로 **내부 복사본**을 사용한다. 원본 삭제와 무관하게 앱 내 사진은 유지된다.
- 다이얼로그는 시스템 기본(밝은) 테마. 상세 본문만 어두운 패널로 감쌌다.
- 노래방 화면이 열린 상태에서 주행을 시작해도 실시간으로 닫히지는 않는다(UXR 리스너 미등록). 앱 복귀(`onResume`) 시 재확인한다.
