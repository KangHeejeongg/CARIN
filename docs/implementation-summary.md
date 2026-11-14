# 구현 요약 (Implementation Summary) — CAR IN

기존 음악 플레이어 앱 → Memory Route MVP → CAR IN 서비스 구조 → 목업 기준 UI 리디자인까지의 누적 구현 내역입니다.
원칙: Java 유지, XML 레이아웃 없이 코드로 UI 생성, 기존 ExoPlayer 재사용, 새 외부 의존성 없음, 패키지명/applicationId 유지.

## 1. 파일 구성

| 경로 | 역할 | 단계 |
|---|---|---|
| `MainActivity.java` | 헤더·탭·페이지·미니 플레이어·히어로 카드·노래방 오버레이를 묶는 단일 화면. 플레이어·목적지·안전·사진 로직 | 기존 + 확장 + 리디자인 |
| `memory/SongCatalog.java` | 번들 음원 ↔ 고정 ID(`carin_song_01`…) ↔ 제목. 플레이리스트와 추억이 공유하는 단일 출처 | MVP |
| `memory/Memory.java` | 추억 모델 + JSON (`photoUri` 선택 필드, 하위 호환) | MVP + CAR IN |
| `memory/MemoryStore.java` | SharedPreferences(JSON) 저장소. 조회/추가/수정/삭제/데모/매칭 | MVP + CAR IN |
| `memory/PlaceMatcher.java` | 지역 단위 매칭(토큰 + 접미사 제거 + 완전 일치) | MVP |
| `memory/MemoryListPage.java` | "카인 추억" 탭: 사진 카드 그리드, 빈 상태, 등록·데모 버튼 | 리디자인 |
| `memory/MemoryDetailDialog.java` | 추억 상세: 사진 + 경로선, 메타, 그날의 노래·노래방·사진 연결·삭제 | 리디자인 |
| `memory/MemoryRegisterDialog.java` | 등록 다이얼로그 (곡은 알약 탭으로 선택) | MVP + 리디자인 |
| `karaoke/KaraokePanel.java` | 노래방 전체 화면 오버레이: 현재 줄 강조 가사, 진행 바, 원형 컨트롤 | CAR IN + 리디자인 |
| `karaoke/DemoLyrics.java` | 팀 자체 제작 정적 데모 가사 (`linesFor()`로 줄 단위 제공) | CAR IN |
| `karaoke/PhotoPicker.java` | 동작하는 사진 선택기 탐색 + 앱 내부 복사 | CAR IN |
| `ui/CarinTheme.java` | 브랜드 팔레트 토큰과 뷰 팩토리(텍스트·유리 카드·칩·글로우·노을 그라디언트). **색은 여기서만 정의** | 리디자인 |
| `ui/CarinIcon.java` | 코드로 그리는 라인 아이콘 17종 (재생·마이크·핀·달력 등) | 리디자인 |
| `ui/PillButton.java` / `ui/CircleButton.java` | 알약 버튼(주·보조·탭) / 원형 버튼(코랄 재생·유리·투명) | 리디자인 |
| `ui/LogoView.java` | CAR IN 워드마크 (지붕 라인 + 자간 + 이퀄라이저) | 리디자인 |
| `ui/RouteLineView.java` | 장식용 경로선 + 핀 (실제 GPS 경로 아님) | 리디자인 |
| `ui/ProgressLine.java` | 재생 진행 바 (표시 전용) | 리디자인 |
| `ui/CarinDialog.java` | 시스템 AlertDialog를 대체하는 다크 로즈 다이얼로그 | 리디자인 |
| `ui/ImageLoader.java` | 사진 비동기 축소 디코딩 + LRU 캐시 + assets 기본 이미지 슬롯 | 리디자인 |
| `assets/backgrounds/README.txt` | 기본 이미지 슬롯 안내 (`hero`·`karaoke`·`memory`·`default`) | 리디자인 |
| `navi/NaviDestinationClient.java` | NaviHelper 3.0.5 래퍼 (메인 스레드 콜백) | MVP |
| `safety/DrivingSafetyGuard.java` | `CarUxRestrictionsManager` 리플렉션 조회 | MVP |
| `res/values(-night)/themes.xml`, `colors.xml` | `NoActionBar` + 브랜드 색 (상태바·창 배경 Deep Vinyl) | 리디자인 |
| `app/src/test/.../PlaceMatcherTest.java` | 매칭 단위 테스트 | MVP |
| `AndroidManifest.xml` | `NAVI_ROUTE` 권한, `android.car` uses-library(선택), 사진 선택기 `<queries>` | MVP + CAR IN |
| `app/build.gradle.kts` | `keystore.properties` 기반 release 서명, R8 `packageScope` 제거 | MVP |

삭제: `memory/MemoryListDialog.java` — 목록은 `MemoryListPage`(탭 화면), 상세는 `MemoryDetailDialog`로 대체.

## 2. 화면 구조

```
FrameLayout (앱 배경: Deep Vinyl + 상단 코랄 빛)
 ├─ LinearLayout
 │   ├─ 헤더         safetyStateText(칩) · LogoView · destinationChip(칩)
 │   ├─ 탭           PillButton×3 (TAB 스타일, 노래방 오버레이가 열리면 노래방 탭 강조)
 │   ├─ 페이지       FrameLayout
 │   │   ├─ playlistPage (ScrollView)
 │   │   │   ├─ 히어로 카드   사진/노을 + 스크림 + RouteLineView + 문구·버튼·점
 │   │   │   └─ [카인 플레이리스트 패널 | MEMORY ROUTE 패널(목적지·등록·데모·개발 정보)]
 │   │   └─ memoryPageScroll (ScrollView → MemoryListPage)
 │   └─ 미니 플레이어  썸네일 · 제목/순번/ProgressLine · ⏮ ▶ ⏭ · 목록
 └─ KaraokePanel (GONE)
```

## 3. 기능별 연결 구조

```
[탭]  createTabs()
   ├─ 카인 플레이리스트 → (노래방 닫기) selectTab(PLAYLIST), 맨 위로
   ├─ 카인 노래방      → openKaraoke(null)
   └─ 카인 추억        → (노래방 닫기) selectTab(MEMORY) → memoryPage.setMemories()

[추억 등록]  openRegisterDialog()
   └─ DrivingSafetyGuard.check()==PARKED → MemoryRegisterDialog → MemoryStore.add() → refreshMemoryViews()

[목적지 인식]
   SDK : NaviDestinationClient.connect() ─onConnected─▶ requestDestination()
          onDestinationInfo(info) ─main thread─▶ handleSdkDestination()  (빈 값이면 "설정된 목적지 없음")
          onDestinationChanged() ───────────────▶ requestDestination()
   DEMO: showDemoDestinationPicker() (CarinDialog) → applyDestination(DEMO, name, "")

[매칭 → 제안 → 재생]  applyDestination(source, name, address)
   ├─ key=source|normalize(name)|normalize(address); 새 key면 heroDismissed=false, heroIndex=0
   ├─ refreshMemoryViews(): MemoryStore.findMatching() → renderHero() · 목적지 칩 · 추억 탭 갱신
   └─ 매칭 있음 && key != lastPromptedKey → showMemoryPrompt(): 플레이리스트 탭 + 맨 위 + 히어로 강조 애니메이션
        renderHero() 상태:
          NONE        → "그 길을 다시 달릴 때…" + 내비 목적지 조회 / 데모 목적지 (+ 추억 등록)
          매칭 없음   → "'X'과(와) 연결된 추억이 없어요." + 추억 등록 / 다른 목적지
          매칭        → "지난번 이 길에서 들었던 노래가 있어요." + 재생하기 / 나중에
          나중에 이후 → "X에서의 추억 N개" + 그날의 노래 / 추억 보기
        재생하기 → playMemorySong(): mediaId 일치 인덱스 → player.seekTo(i,0); play()

[카인 추억]  MemoryListPage.Listener → MainActivity
   ├─ 그날의 노래 → playMemorySong
   ├─ 자세히 → showMemoryDetail() → MemoryDetailDialog
   │     ├─ 사진 연결 → (주차 확인) startPhotoPicker(REQ_PICK_MEMORY_PHOTO)
   │     │     └─ onActivityResult → PhotoPicker.importToAppStorage → MemoryStore.update(withPhoto) → 상세 다시 열기
   │     ├─ 노래방 → playMemorySong + openKaraoke(memory.photoUri)
   │     └─ 삭제 → confirmDeleteMemory(): (주차 확인) CarinDialog 확인 → MemoryStore.delete → 상세 닫기 → refreshMemoryViews
   ├─ 추억 등록 → openRegisterDialog
   └─ 데모 데이터 → createDemoData

[카인 노래방]  openKaraoke(photoOverride)
   ├─ DrivingSafetyGuard.check()==PARKED 아니면 토스트 후 중단
   ├─ karaokePanel.setSong / setPlaying / setProgress → applyKaraokeBackground() → 표시 → 탭 강조 갱신
   │     배경: 상세에서 들어온 추억 사진 → 현재 곡 songId의 최근 추억 사진 → prefs 노래방 사진 → assets karaoke → 그라디언트
   ├─ 패널 버튼 → togglePlay() / skipPrevious() / skipNext()  (미니 플레이어와 같은 메서드)
   ├─ Player.Listener → 패널 제목·가사(첫 줄부터)·재생 아이콘 동기화, progressTicker(0.5초) → 진행 바
   ├─ 가사 영역 터치 → 왼쪽 1/3 이전 줄, 나머지 다음 줄 (동기화 없음)
   ├─ 사진 선택 → startPhotoPicker(REQ_PICK_KARAOKE_PHOTO) → 내부 복사 → prefs 저장 → 배경 적용
   ├─ onBackPressed / ✕ → closeKaraoke()
   └─ onResume: 열려 있으면 UXR 재확인, 주차 아니면 닫음
```

## 4. 기존 플레이어와의 연동 방식

- 플레이어는 기존처럼 `onStart()`에서 생성, `onStop()`에서 `release()`. Memory Route·노래방·미니 플레이어 모두 **이 인스턴스를 그대로 사용**하며 별도 플레이어를 만들지 않는다.
- 노래방을 **별도 Activity가 아닌 같은 Activity의 오버레이**로 둔 이유: Activity 전환 시 `onStop()`이 플레이어를 해제해 음악이 끊기기 때문.
- 재생 제어는 `togglePlay()` / `skipPrevious()` / `skipNext()` 세 메서드에만 있고, 미니 플레이어와 노래방 패널이 이를 함께 호출한다.
- 진행 바는 `onStart()`에서 시작하는 0.5초 주기 `progressTicker`가 갱신하고, `onStop()`에서 멈춘다.
- 곡 식별은 `SongCatalog.Song.id` ↔ `MediaItem.mediaId`. 추억의 `songId`가 플레이리스트에 없으면 토스트만 띄우고 크래시 없이 종료한다.

## 5. 디자인 시스템 (`ui/`)

- 기준: 팀 목업(`Downloads/CARIN/목업`)과 브랜드 컬러 팔레트 시안의 다크 모드.
- 팔레트: Deep Vinyl `#1A191E`(배경) · Charcoal `#2B2B31`(카드) · Glass White `#FAF8F9`(텍스트) · Light Rose `#FFAEB5`(보조 강조) · Coral `#FF7F88`(주 액션). 데모·경고 표기는 Amber `#FFC98A`.
- 이미지 리소스를 추가하지 않고 아이콘·로고·경로선을 `Canvas`로 그린다. 글로우는 `setElevation` + `setOutlineSpotShadowColor`(API 28+).
- 기본 이미지: `ImageLoader`가 사용자 사진 → `assets/backgrounds/<slot>.(jpg|jpeg|png|webp)` → `default.*` → 뷰 배경(노을 그라디언트) 순으로 표시한다. 현재 슬롯 이미지는 비어 있다.

## 6. 사진 처리

- 선택기: `PhotoPicker.buildIntent()`가 `PICK_IMAGES`(API 33+) → `GET_CONTENT` → `OPEN_DOCUMENT` → `PICK` 순으로 조회하고, AAOS의 `com.android.car.frameworkpackagestubs`("파일 관리 미지원" 스텁)는 제외한다. Pleos 에뮬레이터에서는 시스템 포토 피커(`com.android.providers.media.module`)가 선택된다.
- 저장: 포토 피커 URI는 영구 권한이 없으므로 `importToAppStorage()`가 최대 1600px로 축소해 `files/photos/<uuid>.jpg`로 복사하고 `file://` URI를 보관한다. 노래방 배경은 prefs `carin_karaoke.background_uri`, 추억 사진은 `Memory.photoUri`.
- 표시: `ImageLoader`가 백그라운드 스레드에서 축소 디코딩하고, 같은 뷰에 더 최근 요청이 있으면 이전 결과를 버린다(뷰 tag 비교).
- 실패 처리: 디코딩 실패 → 토스트 + 기본 배경. 상세 화면에서 파일이 없으면 "사진을 열 수 없어요" 안내.

## 7. SDK / 플랫폼 API (소스 jar에서 확인한 것만 사용)

| SDK | 사용 API |
|---|---|
| NaviHelper 3.0.5 | `NaviHelper(ctx)`, `initialize(onConnected,onFailed)`, `addListener/removeListener`, `getDestinationInfo()`, `release()`; 콜백 `onDestinationInfo`, `onDestinationChanged`, `onError`; `DestinationInfo.getName()/getAddress()` |
| Vehicle 2.3.2 | (기존) `Vehicle.initialize()`, `getSeat().getCurrentSeatOccupancy()` — `Gear`는 `@hide/@Deprecated`라 미사용 |
| AAOS `android.car` | `Car.createCar`, `getCarManager("uxrestriction")`, `getCurrentCarUxRestrictions()`, `isRequiresDistractionOptimization()`, `getActiveRestrictions()`, `disconnect()` — 리플렉션 |
| Android | `android.provider.action.PICK_IMAGES`(문자열 상수), `ACTION_GET_CONTENT/OPEN_DOCUMENT/PICK`, `PackageManager.queryIntentActivities` + `<queries>` |
