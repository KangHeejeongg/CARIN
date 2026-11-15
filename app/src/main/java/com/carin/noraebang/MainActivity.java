package com.carin.noraebang;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.Seat;
import ai.pleos.playground.vehicle.api.model.area.SeatArea;
import ai.pleos.playground.vehicle.constant.area.SeatValue;

import com.carin.noraebang.karaoke.KaraokePanel;
import com.carin.noraebang.karaoke.PhotoPicker;
import com.carin.noraebang.memory.Memory;
import com.carin.noraebang.memory.MemoryDetailDialog;
import com.carin.noraebang.memory.MemoryListPage;
import com.carin.noraebang.memory.MemoryRegisterDialog;
import com.carin.noraebang.memory.MemoryStore;
import com.carin.noraebang.memory.PhotoRetention;
import com.carin.noraebang.memory.PlaceMatcher;
import com.carin.noraebang.memory.SongCatalog;
import com.carin.noraebang.navi.NaviDestinationClient;
import com.carin.noraebang.photo.PhotoImporter;
import com.carin.noraebang.playback.CarinPlaybackService;
import com.carin.noraebang.safety.DrivingSafetyGuard;
import com.carin.noraebang.ui.CarinDialog;
import com.carin.noraebang.ui.CarinIcon;
import com.carin.noraebang.ui.CarinTheme;
import com.carin.noraebang.ui.CircleButton;
import com.carin.noraebang.ui.ImageLoader;
import com.carin.noraebang.ui.LogoView;
import com.carin.noraebang.ui.OnboardingPanel;
import com.carin.noraebang.ui.PillButton;
import com.carin.noraebang.ui.ProgressLine;
import com.carin.noraebang.ui.RouteLineView;

import kotlin.Unit;
import android.util.Log;

public class MainActivity extends Activity {

    // CAR IN 디자인 색상 (브랜드 팔레트: ui/CarinTheme)
    private static final int WHITE = CarinTheme.GLASS_WHITE;
    private static final int ACCENT = CarinTheme.CORAL;
    private static final int MUTED = CarinTheme.MUTED;
    private static final int WARN = CarinTheme.AMBER;

    private static final int TAB_PLAYLIST = 0;
    private static final int TAB_KARAOKE = 1;
    private static final int TAB_MEMORY = 2;

    /** 재생 서비스의 플레이어를 제어하는 MediaController (연결 전·onStop 후에는 null) */
    private Player player;
    private ListenableFuture<MediaController> controllerFuture;

    // Vehicle SDK 연결 및 좌석 상태 조회에 사용하는 객체
    private Vehicle vehicle;
    private TextView seatStatusText;
    private PillButton refreshSeatButton;

    // 비동기 요청이 진행 중일 때 중복 요청과 조기 해제를 방지
    private boolean seatRequestInProgress = false;
    private boolean activityDestroyed = false;

    // 미니 플레이어 · 플레이리스트
    private TextView songTitle;
    private TextView songCount;
    private TextView statusText;
    private TextView playlistInfo;
    private ImageView miniCover;
    private ProgressLine miniProgress;

    private CircleButton playButton;
    private CircleButton previousButton;
    private CircleButton nextButton;
    private LinearLayout playlistContainer;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            uiHandler.postDelayed(this, 500);
        }
    };

    // 탭 · 화면 전환
    private final PillButton[] tabs = new PillButton[3];
    private int selectedTab = TAB_PLAYLIST;
    private ScrollView playlistPage;
    private ScrollView memoryPageScroll;
    private MemoryListPage memoryPage;
    private View playlistPanel;
    private boolean landscape;

    // ───────── Memory Route ─────────

    /** 현재 화면에 반영된 목적지가 어디서 왔는지 */
    private enum DestinationSource { NONE, SDK, DEMO }

    private static final String[] DEMO_DESTINATIONS = {
            // 앞의 세 곳은 기본 데모 추억(강릉·속초·평창)과 매칭된다
            "강릉역", "속초해수욕장", "평창역", "부산역", "제주시", "서울역"
    };

    private MemoryStore memoryStore;
    private NaviDestinationClient naviClient;
    private DrivingSafetyGuard safetyGuard;

    private TextView sdkDestinationText;
    private TextView demoDestinationText;
    private TextView safetyStateText;
    private TextView destinationChip;
    private PillButton queryDestinationButton;
    /** 사용자가 "내비에서 불러오기"를 눌렀는지 (목적지가 없을 때만 안내 토스트를 띄우기 위함) */
    private boolean userRequestedNaviQuery = false;

    private DestinationSource destinationSource = DestinationSource.NONE;
    private String destinationName = "";
    private String destinationAddress = "";
    /** 마지막으로 추억 제안을 띄운 목적지 키 (같은 목적지 반복 조회 시 중복 제안 방지) */
    private String lastPromptedKey = null;

    // 히어로 카드 (목업의 "지난번 이 길에서 들었던 노래가 있어요" 카드)
    private FrameLayout heroCard;
    private ImageView heroImage;
    private RouteLineView heroRoute;
    private TextView heroChip;
    private TextView heroTitle;
    private TextView heroQuestion;
    private TextView heroMeta;
    private TextView heroSong;
    private View heroSongRow;
    private LinearLayout heroActions;
    private LinearLayout heroDots;
    private List<Memory> heroMatches = new ArrayList<>();
    private int heroIndex = 0;
    /** "나중에"를 눌러 현재 목적지의 제안 문구를 접었는지 */
    private boolean heroDismissed = false;

    // ───────── CAR IN 노래방 / 사진 ─────────

    private static final int REQ_PICK_KARAOKE_PHOTO = 11;
    private static final int REQ_PICK_MEMORY_PHOTO = 12;
    private static final String PREFS_KARAOKE = "carin_karaoke";
    private static final String KEY_KARAOKE_PHOTO = "background_uri";

    private KaraokePanel karaokePanel;
    /** 첫 실행 안내 오버레이 (사용법 버튼으로 다시 볼 수 있다) */
    private OnboardingPanel onboardingPanel;
    private static final String PREFS_GUIDE = "carin_guide";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";
    /** 메인 화면(헤더·탭·페이지·미니 플레이어). 노래방이 열린 동안 포커스를 막는다 */
    private LinearLayout mainContent;
    /** 추억 상세에서 노래방에 들어왔을 때 이 세션 동안 고정할 추억 (null이면 곡에 따라 자동 선택) */
    private Memory karaokeSessionMemory;
    private CarinDialog detailDialog;
    /** 주차 중에만 허용되는 다이얼로그(추억 등록 입력, 삭제 확인). 주행으로 바뀌면 닫는다 */
    private CarinDialog restrictedDialog;
    /** 추억 상세에서 사진 선택을 시작했을 때 결과를 연결할 추억 ID */
    private String pendingPhotoMemoryId = null;
    /** debug 빌드 검증용: true면 사진 연결 저장을 실패로 처리한다 (release는 설정 경로 없음) */
    private static boolean debugFailPhotoSave = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        memoryStore = new MemoryStore(this);
        // 첫 실행이면 데모 추억(강릉·속초·평창)을 넣어 처음부터 무엇을 하는 앱인지 보이게 한다
        memoryStore.seedDemosOnce();
        safetyGuard = new DrivingSafetyGuard(this);
        readDebugFaults(getIntent());
        // Pleos 가이드: 서비스를 Started 상태로 두어 앱 작업 제거 시 onTaskRemoved에서 재생을 정리하게 한다.
        // 서비스 생성만으로는 재생하지 않는다.
        try {
            startService(new Intent(this, CarinPlaybackService.class));
        } catch (Exception e) {
            Log.w("CARIN_PLAYBACK", "재생 서비스 시작 실패 (연결 시 다시 생성됨)", e);
        }

        getWindow().setStatusBarColor(CarinTheme.DEEP_VINYL);
        getWindow().setNavigationBarColor(CarinTheme.DEEP_VINYL);

        createUI();
        setupButtons();
        initializeVehicle(); // 화면 생성 뒤에 SDK 연결 시도
        initializeNavi();
        refreshMemoryViews();
        maybeShowOnboarding();
    }

    /** dp를 실제 화면 픽셀로 변환 */
    private int dp(float value) {
        return CarinTheme.dp(this, value);
    }

    /** 브랜드 서체를 적용한 텍스트 */
    private TextView text(String value, float size, int color, int weight) {
        return CarinTheme.text(this, value, size, color, weight);
    }

    /** 세로 LayoutParams (위 여백 포함) */
    private LinearLayout.LayoutParams below(int width, int height, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = dp(topMarginDp);
        return params;
    }

    // ───────────────────────────────────────────
    // 화면 구성: 헤더 → 탭 → (플레이리스트 | 추억) → 미니 플레이어
    // ───────────────────────────────────────────

    /** 화면 전체를 코드로 구성한다 */
    private void createUI() {
        landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(CarinTheme.appBackground(this));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(36), dp(16), dp(36), dp(20));
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));
        mainContent = root;

        root.addView(createHeader(), new LinearLayout.LayoutParams(-1, dp(104)));
        root.addView(createTabs(), below(-1, dp(72), 8));

        FrameLayout pages = new FrameLayout(this);
        LinearLayout.LayoutParams pagesParams = new LinearLayout.LayoutParams(-1, 0, 1);
        pagesParams.topMargin = dp(20);
        root.addView(pages, pagesParams);

        playlistPage = createPlaylistPage();
        pages.addView(playlistPage, new FrameLayout.LayoutParams(-1, -1));

        memoryPage = new MemoryListPage(this, memoryPageListener, landscape ? 3 : 1);
        memoryPageScroll = new ScrollView(this);
        memoryPageScroll.setVerticalScrollBarEnabled(false);
        memoryPageScroll.addView(memoryPage);
        pages.addView(memoryPageScroll, new FrameLayout.LayoutParams(-1, -1));

        root.addView(createMiniPlayer(), below(-1, -2, 16));

        // 노래방 오버레이는 같은 Activity 안에 두어 플레이어를 공유한다
        karaokePanel = new KaraokePanel(this, karaokeListener);
        frame.addView(karaokePanel, new FrameLayout.LayoutParams(-1, -1));

        onboardingPanel = new OnboardingPanel(this, this::onOnboardingFinished);
        frame.addView(onboardingPanel, new FrameLayout.LayoutParams(-1, -1));

        setContentView(frame);
        CarinTheme.installFocusRings(frame);
        selectTab(TAB_PLAYLIST);
    }

    /** 상단: 사용법 · (주행 중일 때만) 주행 상태 칩 · CAR IN 로고 · 목적지 칩 */
    private View createHeader() {
        FrameLayout header = new FrameLayout(this);

        LinearLayout left = new LinearLayout(this);
        left.setGravity(Gravity.CENTER_VERTICAL);
        PillButton help = new PillButton(this, "사용법", CarinIcon.Type.SPARKLE, PillButton.Style.GHOST);
        help.setTextSize(17);
        help.setOnClickListener(v -> showOnboarding());
        left.addView(help, new LinearLayout.LayoutParams(-2, dp(56)));
        // 평소(주차)에는 숨기고, 주행 중이거나 확인할 수 없을 때만 보여 준다
        safetyStateText = CarinTheme.chip(this, "", MUTED);
        safetyStateText.setVisibility(View.GONE);
        LinearLayout.LayoutParams safetyParams = new LinearLayout.LayoutParams(-2, -2);
        safetyParams.leftMargin = dp(12);
        left.addView(safetyStateText, safetyParams);
        header.addView(left, new FrameLayout.LayoutParams(-2, -2,
                Gravity.START | Gravity.CENTER_VERTICAL));

        header.addView(new LogoView(this), new FrameLayout.LayoutParams(dp(290), dp(100),
                Gravity.CENTER));

        destinationChip = CarinTheme.chip(this, "목적지 고르기", MUTED);
        destinationChip.setMinHeight(dp(56));
        destinationChip.setClickable(true);
        destinationChip.setOnClickListener(v -> showDestinationPicker());
        header.addView(destinationChip, new FrameLayout.LayoutParams(-2, -2,
                Gravity.END | Gravity.CENTER_VERTICAL));
        return header;
    }

    /** CAR IN 서비스 탭 (카인 플레이리스트 · 카인 노래방 · 카인 추억) */
    private View createTabs() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        String[] labels = {"카인 플레이리스트", "카인 노래방", "카인 추억"};
        CarinIcon.Type[] icons = {CarinIcon.Type.PLAY, CarinIcon.Type.MIC, CarinIcon.Type.SPARKLE};
        for (int i = 0; i < tabs.length; i++) {
            PillButton tab = new PillButton(this, labels[i], icons[i], PillButton.Style.TAB);
            tab.setTextSize(21);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
            if (i > 0) params.leftMargin = dp(16);
            row.addView(tab, params);
            tabs[i] = tab;
        }

        tabs[TAB_PLAYLIST].setOnClickListener(v -> {
            if (isKaraokeOpen()) closeKaraoke();
            selectTab(TAB_PLAYLIST);
            playlistPage.smoothScrollTo(0, 0);
        });
        tabs[TAB_KARAOKE].setOnClickListener(v -> openKaraoke(null));
        tabs[TAB_MEMORY].setOnClickListener(v -> {
            if (isKaraokeOpen()) closeKaraoke();
            selectTab(TAB_MEMORY);
        });
        return row;
    }

    /** 플레이리스트/추억 화면을 전환한다 (노래방은 오버레이라 별도) */
    private void selectTab(int tab) {
        selectedTab = tab;
        playlistPage.setVisibility(tab == TAB_MEMORY ? View.GONE : View.VISIBLE);
        memoryPageScroll.setVisibility(tab == TAB_MEMORY ? View.VISIBLE : View.GONE);
        if (tab == TAB_MEMORY) refreshMemoryPage();
        updateTabHighlight();
    }

    private void updateTabHighlight() {
        int active = isKaraokeOpen() ? TAB_KARAOKE : selectedTab;
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] != null) tabs[i].setTabSelected(i == active);
        }
    }

    /** 카인 플레이리스트 탭: 히어로 카드 + (곡 목록 | Memory Route 패널) + 개발 정보 */
    private ScrollView createPlaylistPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column);

        column.addView(createHeroCard(), new LinearLayout.LayoutParams(-1, dp(landscape ? 500 : 560)));

        // 한 화면에 주 행동 하나: 히어로(목적지·추억) 아래에는 곡 목록만 둔다.
        // 목적지 선택은 헤더 칩·히어로 버튼의 "목적지 고르기" 창으로, 추억 남기기는 추억 탭으로 옮겼다.
        playlistPanel = createPlaylistPanel();
        column.addView(playlistPanel, below(-1, -2, 24));

        // SDK 연결 상태·조수석 조회 같은 개발 정보는 debug 빌드에서만 맨 아래에 보인다
        View routePanel = createRoutePanel();
        if (isDebuggableBuild()) column.addView(routePanel, below(-1, -2, 24));
        return scroll;
    }

    /** debug 빌드인지 (개발 정보·실패 주입은 debug에서만) */
    private boolean isDebuggableBuild() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    // ── 히어로 카드 ──

    private View createHeroCard() {
        heroCard = new FrameLayout(this);
        heroCard.setBackground(CarinTheme.sunset(this, 36));
        heroCard.setClipToOutline(true);

        heroImage = new ImageView(this);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroCard.addView(heroImage, new FrameLayout.LayoutParams(-1, -1));

        // 왼쪽 글자가 잘 읽히도록 왼쪽→오른쪽으로 옅어지는 스크림
        View scrim = new View(this);
        // 세로 창에서는 글이 카드 전체 너비에 걸치므로 오른쪽까지 충분히 어둡게 한다
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, landscape
                ? new int[] {0xF21A191E, 0xB31A191E, 0x331A191E, 0x001A191E}
                : new int[] {0xF21A191E, 0xD91A191E, 0xA61A191E, 0x731A191E}));
        heroCard.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        heroRoute = new RouteLineView(this);
        FrameLayout.LayoutParams routeParams = new FrameLayout.LayoutParams(dp(640), dp(300),
                Gravity.END | Gravity.BOTTOM);
        routeParams.setMargins(0, 0, dp(32), dp(40));
        heroCard.addView(heroRoute, routeParams);
        // 세로 창(약 840dp)에서는 글이 카드 전체 너비를 쓰므로 장식용 경로선이 버튼과 겹친다 → 숨김
        if (!landscape) heroRoute.setVisibility(View.GONE);

        LinearLayout split = new LinearLayout(this);
        split.setOrientation(LinearLayout.HORIZONTAL);
        heroCard.addView(split, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        if (landscape) {
            content.setPadding(dp(52), dp(36), dp(24), dp(36));
            split.addView(content, new LinearLayout.LayoutParams(0, -1, 1.15f));
            split.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1f));
        } else {
            // 세로 창에서 반쪽 너비만 쓰면 제목이 단어 중간에서 끊기고 버튼이 잘린다
            content.setPadding(dp(40), dp(32), dp(40), dp(32));
            split.addView(content, new LinearLayout.LayoutParams(-1, -1));
        }

        LinearLayout chipRow = new LinearLayout(this);
        heroChip = CarinTheme.chip(this, "MEMORY ROUTE", ACCENT);
        chipRow.addView(heroChip);
        content.addView(chipRow);

        heroTitle = text("", 34, WHITE, CarinTheme.LIGHT);
        content.addView(heroTitle, below(-1, -2, 22));
        heroQuestion = text("", 36, WHITE, CarinTheme.BOLD);
        content.addView(heroQuestion, below(-1, -2, 6));

        heroMeta = text("", 19, MUTED, CarinTheme.REGULAR);
        View metaRow = CarinTheme.iconRow(this, CarinIcon.Type.PIN, CarinTheme.LIGHT_ROSE, heroMeta, 24);
        content.addView(metaRow, below(-1, -2, 22));

        heroSong = text("", 19, CarinTheme.LIGHT_ROSE, CarinTheme.MEDIUM);
        heroSongRow = CarinTheme.iconRow(this, CarinIcon.Type.NOTE, CarinTheme.LIGHT_ROSE, heroSong, 24);
        content.addView(heroSongRow, below(-1, -2, 10));

        heroActions = new LinearLayout(this);
        heroActions.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(heroActions, below(-1, dp(68), 30));

        heroDots = new LinearLayout(this);
        heroDots.setOrientation(LinearLayout.HORIZONTAL);
        heroDots.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(heroDots, below(-2, dp(24), 22));
        return heroCard;
    }

    /** 목적지·매칭 상태에 맞춰 히어로 카드를 다시 그린다 */
    private void renderHero() {
        if (heroCard == null) return;
        heroActions.removeAllViews();
        heroDots.removeAllViews();

        Memory memory = heroMatches.isEmpty()
                ? null : heroMatches.get(Math.min(heroIndex, heroMatches.size() - 1));

        if (destinationSource == DestinationSource.NONE) {
            List<Memory> all = memoryStore.getAll();
            setHeroChip("MEMORY ROUTE", ACCENT);
            // 처음 보는 사람도 바로 할 일을 알 수 있게: 질문 하나 + 버튼 하나
            heroTitle.setText("같은 길에서, 그날의 노래를 다시.");
            heroQuestion.setText("어디로 떠나세요?");
            boolean protectedStore = memoryStore.isWriteProtected();
            heroMeta.setText(protectedStore
                    ? "저장된 추억을 읽을 수 없어 기존 기록을 보호하고 있어요"
                    : all.isEmpty()
                    ? "목적지를 고르면 그 길에서 들었던 노래를 찾아드려요"
                    : "추억이 있는 곳 · " + memoryPlaces(all, 3));
            heroSongRow.setVisibility(View.GONE);
            addHeroAction("목적지 고르기", CarinIcon.Type.PIN, PillButton.Style.PRIMARY,
                    this::showDestinationPicker);
            heroRoute.setLabels("", "");
            loadHeroImage(null);
            return;
        }

        // 목적지 출처((SDK)/[DEMO])는 헤더 칩에 항상 표시되므로 히어로에서는 반복하지 않는다
        setHeroChip("MEMORY ROUTE", ACCENT);

        if (memory == null) {
            heroTitle.setText("'" + destinationName + "'과(와) 연결된 추억이 없어요.");
            heroQuestion.setText("이번 여행의 노래를 남겨볼까요?");
            heroMeta.setText(destinationAddress.isEmpty()
                    ? destinationName : destinationName + " · " + destinationAddress);
            heroSongRow.setVisibility(View.GONE);
            addHeroAction("추억 남기기", CarinIcon.Type.PLUS, PillButton.Style.PRIMARY,
                    this::openRegisterDialog);
            addHeroAction("다른 목적지", CarinIcon.Type.PIN, PillButton.Style.GHOST,
                    this::showDestinationPicker);
            heroRoute.setLabels("", destinationName);
            loadHeroImage(null);
            return;
        }

        String meta = memory.title + " · " + CarinTheme.displayDate(memory.travelDate)
                + (memory.demo ? "  [데모 데이터]" : "");
        heroMeta.setText(meta);
        heroSong.setText("그날의 노래 · " + SongCatalog.titleOf(memory.songId));
        heroSongRow.setVisibility(View.VISIBLE);
        heroRoute.setLabels("", memory.place);
        loadHeroImage(memory.photoUri);

        if (!heroDismissed) {
            heroTitle.setText("지난번 이 길에서 들었던\n노래가 있어요.");
            heroQuestion.setText("다시 들어보시겠어요?");
            addHeroAction("재생하기", CarinIcon.Type.PLAY, PillButton.Style.PRIMARY,
                    () -> playMemorySong(memory));
            addHeroAction("나중에", null, PillButton.Style.GHOST, () -> {
                heroDismissed = true;
                renderHero();
            });
        } else {
            heroTitle.setText(destinationName + "에서의 추억 " + heroMatches.size() + "개");
            heroQuestion.setText(memory.title);
            addHeroAction("그날의 노래", CarinIcon.Type.PLAY, PillButton.Style.PRIMARY,
                    () -> playMemorySong(memory));
            addHeroAction("추억 보기", CarinIcon.Type.SPARKLE, PillButton.Style.GHOST,
                    () -> showMemoryDetail(memory));
        }

        // 같은 목적지의 추억이 여러 개면 점으로 넘겨 본다 (최근 여행 우선)
        if (heroMatches.size() > 1) {
            for (int i = 0; i < heroMatches.size(); i++) {
                final int index = i;
                boolean current = i == heroIndex;
                View dot = new View(this);
                dot.setBackground(CarinTheme.rounded(this,
                        current ? ACCENT : CarinTheme.withAlpha(WHITE, 0x55), 999));
                dot.setOnClickListener(v -> {
                    heroIndex = index;
                    renderHero();
                });
                LinearLayout.LayoutParams dotParams =
                        new LinearLayout.LayoutParams(dp(current ? 32 : 12), dp(12));
                dotParams.rightMargin = dp(10);
                heroDots.addView(dot, dotParams);
            }
        }
    }

    private void setHeroChip(String label, int color) {
        heroChip.setText(label);
        CarinTheme.styleChip(heroChip, color);
    }

    private void addHeroAction(String label, CarinIcon.Type icon, PillButton.Style style, Runnable action) {
        PillButton button = new PillButton(this, label, icon, style);
        button.setTextSize(20);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -1);
        if (heroActions.getChildCount() > 0) params.leftMargin = dp(14);
        heroActions.addView(button, params);
    }

    /**
     * 추억이 있는 장소를 중복 없이 최대 limit개 나열한다 (예: "강릉 · 속초 · 평창").
     *
     * @param memories 추억 목록 (최신순)
     * @param limit    최대 장소 수
     * @return 장소 요약 문자열
     */
    private static String memoryPlaces(List<Memory> memories, int limit) {
        List<String> places = new ArrayList<>();
        for (Memory memory : memories) {
            if (!places.contains(memory.place)) places.add(memory.place);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(limit, places.size()); i++) {
            if (i > 0) out.append(" · ");
            out.append(places.get(i));
        }
        if (places.size() > limit) out.append(" 외 ").append(places.size() - limit).append("곳");
        return out.toString();
    }

    private void loadHeroImage(String photoUri) {
        ImageLoader.load(heroImage, photoUri == null ? null : Uri.parse(photoUri),
                ImageLoader.SLOT_HERO, 1600, null);
    }

    /** 목적지 출처 표기: 실제 연동은 (SDK), 데모는 [DEMO] */
    private String sourceLabel() {
        return (destinationSource == DestinationSource.SDK ? "(SDK) " : "[DEMO] ") + destinationName;
    }

    // ── 카인 플레이리스트 패널 ──

    private View createPlaylistPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(32), dp(28), dp(32), dp(24));
        panel.setBackground(CarinTheme.glass(this, 32));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        playlistInfo = text("카인 플레이리스트", 24, WHITE, CarinTheme.BOLD);
        head.addView(playlistInfo, new LinearLayout.LayoutParams(0, -2, 1));
        statusText = CarinTheme.chip(this, "재생 준비 중", CarinTheme.LIGHT_ROSE);
        head.addView(statusText);
        panel.addView(head);

        panel.addView(text("곡을 누르면 바로 재생돼요", 17, MUTED, CarinTheme.REGULAR),
                below(-1, -2, 6));

        playlistContainer = new LinearLayout(this);
        playlistContainer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(playlistContainer, below(-1, -2, 20));
        return panel;
    }

    // ── Memory Route 패널 (목적지 · 등록 · 데모 · 개발 정보) ──

    private View createRoutePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(32), dp(28), dp(32), dp(24));
        panel.setBackground(CarinTheme.glass(this, 32));

        TextView title = text("개발 정보 · MEMORY ROUTE (debug 빌드 전용)", 16, ACCENT, CarinTheme.BOLD);
        title.setLetterSpacing(0.18f);
        panel.addView(CarinTheme.iconRow(this, CarinIcon.Type.SPARKLE, ACCENT, title, 22));
        panel.addView(text("목적지가 지난 여행지와 같으면 그날의 노래를 찾아드려요", 17,
                MUTED, CarinTheme.REGULAR), below(-1, -2, 6));

        // 실제 SDK 목적지와 데모 목적지를 분리 표시
        sdkDestinationText = text("실제 내비 목적지 (NaviHelper SDK): 연결 중...", 17, MUTED, CarinTheme.REGULAR);
        panel.addView(sdkDestinationText, below(-1, -2, 18));
        demoDestinationText = text("데모 목적지: 선택 안 함", 17, MUTED, CarinTheme.REGULAR);
        panel.addView(demoDestinationText, below(-1, -2, 6));

        queryDestinationButton = new PillButton(this, "내비 목적지 조회", CarinIcon.Type.NAV, PillButton.Style.GHOST);
        queryDestinationButton.setOnClickListener(v -> querySdkDestination());
        PillButton demoDestinationButton = new PillButton(this, "데모 목적지 선택", CarinIcon.Type.PIN, PillButton.Style.GHOST);
        demoDestinationButton.setOnClickListener(v -> showDestinationPicker());
        panel.addView(buttonRow(queryDestinationButton, demoDestinationButton), below(-1, dp(60), 20));

        PillButton registerButton = new PillButton(this, "추억 등록", CarinIcon.Type.PLUS, PillButton.Style.PRIMARY);
        registerButton.setOnClickListener(v -> openRegisterDialog());
        PillButton demoDataButton = new PillButton(this, "데모 데이터 만들기", CarinIcon.Type.SPARKLE, PillButton.Style.GHOST);
        demoDataButton.setOnClickListener(v -> createDemoData());
        panel.addView(buttonRow(registerButton, demoDataButton), below(-1, dp(60), 12));

        // 개발 정보: 차량 SDK 조수석 조회 (평소에는 접어 둔다)
        LinearLayout devPanel = new LinearLayout(this);
        devPanel.setOrientation(LinearLayout.VERTICAL);
        devPanel.setVisibility(View.GONE);

        TextView devToggleLabel = text("개발 정보 · Vehicle SDK", 15, CarinTheme.FAINT, CarinTheme.MEDIUM);
        LinearLayout devToggle = CarinTheme.iconRow(this, CarinIcon.Type.CHEVRON_DOWN, CarinTheme.FAINT,
                devToggleLabel, 20);
        devToggle.setPadding(0, dp(10), 0, dp(10));
        devToggle.setOnClickListener(v -> devPanel.setVisibility(
                devPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        panel.addView(devToggle, below(-1, -2, 14));

        seatStatusText = text("조수석 상태: 조회 전", 16, MUTED, CarinTheme.REGULAR);
        devPanel.addView(seatStatusText);
        // 단순 클릭 문구가 아니라 실제 Vehicle SDK 조회
        refreshSeatButton = new PillButton(this, "조수석 상태 새로고침", null, PillButton.Style.GHOST);
        refreshSeatButton.setTextSize(16);
        refreshSeatButton.setOnClickListener(v -> readSeatOccupancy());
        LinearLayout seatRow = new LinearLayout(this);
        seatRow.addView(refreshSeatButton, new LinearLayout.LayoutParams(-2, dp(52)));
        devPanel.addView(seatRow, below(-1, -2, 10));
        panel.addView(devPanel);
        return panel;
    }

    private LinearLayout buttonRow(View first, View second) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(first, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(0, -1, 1);
        secondParams.leftMargin = dp(12);
        row.addView(second, secondParams);
        return row;
    }

    // ── 하단 미니 플레이어 ──

    private View createMiniPlayer() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), dp(16), dp(28), dp(16));
        bar.setBackground(CarinTheme.glass(this, 30));

        // 썸네일: assets 기본 사진(없으면 노을 그라디언트 + 음표)
        FrameLayout cover = new FrameLayout(this);
        cover.setBackground(CarinTheme.sunset(this, 18));
        cover.setClipToOutline(true);
        ImageView note = CarinTheme.icon(this, CarinIcon.Type.NOTE, 0xE6FFFFFF);
        cover.addView(note, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
        miniCover = new ImageView(this);
        miniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.addView(miniCover, new FrameLayout.LayoutParams(-1, -1));
        ImageLoader.load(miniCover, null, ImageLoader.SLOT_HERO, 300, null);
        bar.addView(cover, new LinearLayout.LayoutParams(dp(80), dp(80)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(20), 0, dp(20), 0);
        songTitle = text("노래 선택", 22, WHITE, CarinTheme.BOLD);
        songTitle.setSingleLine(true);
        songTitle.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(songTitle);
        songCount = text("카인 플레이리스트", 16, MUTED, CarinTheme.REGULAR);
        // 좁은 창에서 "1 / 3"만 다음 줄로 떨어지지 않도록 한 줄로 줄인다
        songCount.setSingleLine(true);
        songCount.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(songCount, below(-1, -2, 4));
        miniProgress = new ProgressLine(this, false);
        info.addView(miniProgress, below(-1, dp(10), 10));
        bar.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        previousButton = new CircleButton(this, CarinIcon.Type.PREV, CircleButton.Style.GHOST, 32);
        playButton = new CircleButton(this, CarinIcon.Type.PLAY, CircleButton.Style.PRIMARY, 34);
        nextButton = new CircleButton(this, CarinIcon.Type.NEXT, CircleButton.Style.GHOST, 32);
        bar.addView(previousButton, circleParams(72));
        bar.addView(playButton, circleParams(88));
        bar.addView(nextButton, circleParams(72));

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        CircleButton listButton = new CircleButton(this, CarinIcon.Type.LIST, CircleButton.Style.GLASS, 30);
        listButton.setOnClickListener(v -> showPlaylist());
        right.addView(listButton, new LinearLayout.LayoutParams(dp(68), dp(68)));
        bar.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        return bar;
    }

    private LinearLayout.LayoutParams circleParams(int sizeDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        params.setMargins(dp(10), 0, dp(10), 0);
        return params;
    }

    /** 목록 버튼: 플레이리스트 탭의 곡 목록으로 이동 */
    private void showPlaylist() {
        if (isKaraokeOpen()) closeKaraoke();
        selectTab(TAB_PLAYLIST);
        playlistPage.post(() -> playlistPage.smoothScrollTo(0,
                ((View) playlistPanel.getParent()).getTop()));
    }

    // ───────────────────────────────────────────
    // 플레이어: CarinPlaybackService가 ExoPlayer를 소유하고, 화면은 MediaController로 제어한다.
    // onStart에서 연결, onStop에서 연결만 해제하므로 다른 앱으로 전환해도 재생이 계속된다.
    // ───────────────────────────────────────────

    /** 재생 컨트롤 버튼 이벤트 연결 */
    private void setupButtons() {
        playButton.setOnClickListener(v -> togglePlay());
        previousButton.setOnClickListener(v -> skipPrevious());
        nextButton.setOnClickListener(v -> skipNext());
    }

    /** 재생 / 일시정지 (명시적 사용자 동작) */
    private void togglePlay() {
        if (player == null) return;

        if (player.isPlaying()) {
            player.pause();
        } else {
            // 포커스 영구 상실로 정지(IDLE)됐거나 끝까지 재생(ENDED)한 경우도 이어서 재생할 수 있게 준비
            if (player.getPlaybackState() == Player.STATE_IDLE) {
                player.prepare();
            }
            if (player.getPlaybackState() == Player.STATE_ENDED) {
                player.seekTo(0, 0);
            }
            player.play();
        }
    }

    /** 이전 곡 (첫 곡이면 처음으로) */
    private void skipPrevious() {
        if (player == null) return;

        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem();
        } else {
            player.seekTo(0);
        }
    }

    /** 플레이리스트의 index번째 곡을 처음부터 재생 (명시적 사용자 동작) */
    private void playAt(int index) {
        if (player == null) return;
        if (player.getPlaybackState() == Player.STATE_IDLE) player.prepare();
        player.seekTo(index, 0);
        player.play();
    }

    /** 다음 곡 */
    private void skipNext() {
        if (player != null && player.hasNextMediaItem()) {
            player.seekToNextMediaItem();
        }
    }

    /** 서비스 플레이어 상태 변화를 화면(미니 플레이어·곡 목록·노래방)에 반영 */
    private final Player.Listener playerListener = new Player.Listener() {

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            updateSongInfo();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            showPlayingState(isPlaying);
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_ENDED) {
                statusText.setText("재생 완료");
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            statusText.setText("재생 오류: " + error.getErrorCodeName());
            CarinTheme.styleChip(statusText, WARN);
        }
    };

    /** 재생 버튼 아이콘·상태 칩·노래방 버튼을 같은 상태로 맞춘다 */
    private void showPlayingState(boolean isPlaying) {
        playButton.setIcon(isPlaying ? CarinIcon.Type.PAUSE : CarinIcon.Type.PLAY);
        statusText.setText(isPlaying ? "♪ 재생 중" : "일시정지");
        CarinTheme.styleChip(statusText, CarinTheme.LIGHT_ROSE);
        // 노래방 화면의 재생 버튼도 같은 플레이어 상태를 따른다
        karaokePanel.setPlaying(isPlaying);
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectPlayer();
        uiHandler.post(progressTicker);
        // 화면이 보이는 동안 주행 상태 변경을 실시간으로 받아 노래방·입력 화면을 닫는다
        safetyGuard.startMonitoring(this::onDrivingStateChanged);
    }

    /** 재생 서비스에 MediaController로 연결한다 (연결만으로는 재생하지 않는다) */
    private void connectPlayer() {
        SessionToken token = new SessionToken(this, new ComponentName(this, CarinPlaybackService.class));
        final ListenableFuture<MediaController> future = new MediaController.Builder(this, token).buildAsync();
        controllerFuture = future;
        statusText.setText("플레이어 연결 중");
        future.addListener(() -> {
            if (future != controllerFuture) return;   // 그 사이 onStop으로 연결이 취소됨
            try {
                MediaController controller = future.get();
                player = controller;
                controller.addListener(playerListener);
                showPlayingState(controller.isPlaying());
                if (controller.getPlaybackState() == Player.STATE_ENDED) statusText.setText("재생 완료");
                updateSongInfo();
                Log.d("CARIN_PLAYBACK", "MediaController 연결: 곡 " + controller.getMediaItemCount()
                        + "개, 재생 중=" + controller.isPlaying());
            } catch (Exception e) {
                Log.e("CARIN_PLAYBACK", "MediaController 연결 실패", e);
                statusText.setText("플레이어 연결 실패");
                CarinTheme.styleChip(statusText, WARN);
            }
        }, getMainExecutor());
    }

    /** 현재 노래 제목 및 순번 갱신 */
    private void updateSongInfo() {
        if (player == null) return;

        MediaItem current =
                player.getCurrentMediaItem();

        if (current != null) {

            CharSequence title =
                    current.mediaMetadata.title;

            songTitle.setText(
                    title == null ? "제목 없음" : title
            );

            int index =
                    player.getCurrentMediaItemIndex() + 1;

            int total = player.getMediaItemCount();

            // 세로 창에서는 공간이 좁아 순번만 보여 준다
            songCount.setText(landscape
                    ? "카인 플레이리스트 · " + index + " / " + total
                    : "재생 순서 " + index + " / " + total);

            playlistInfo.setText(
                    "카인 플레이리스트 · "
                            + total + "곡"
            );

            SongCatalog.Song song = SongCatalog.findById(current.mediaId);
            if (song != null) {
                ImageLoader.load(miniCover, ImageLoader.assetUri(song.coverAsset),
                        ImageLoader.SLOT_HERO, 300, null);
            }

            karaokePanel.setSong(current.mediaId, title);
            // 노래방에서 곡을 넘기면 그 곡의 추억 사진으로 배경도 바꾼다
            if (isKaraokeOpen()) applyKaraokeBackground();
        }

        // 현재 곡 목록의 강조 표시도 갱신
        updatePlaylistUI();
    }

    /** 미니 플레이어 · 노래방 진행 바 갱신 (0.5초 주기) */
    private void updateProgress() {
        if (player == null) return;
        long duration = player.getDuration();
        long position = player.getCurrentPosition();
        miniProgress.setProgress(duration > 0 ? position / (float) duration : 0f);
        if (isKaraokeOpen()) karaokePanel.setProgress(position, duration);
    }

    @Override
    protected void onStop() {
        uiHandler.removeCallbacks(progressTicker);
        // 백그라운드에서는 감시를 멈춘다. 복귀 시 onStart에서 다시 등록하고 onResume에서 재확인한다
        safetyGuard.stopMonitoring();
        // 화면 연결만 해제한다. 플레이어는 서비스가 소유하므로 재생은 계속된다
        if (player != null) {
            player.removeListener(playerListener);
            player = null;
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }

        super.onStop();
    }

    private void updatePlaylistUI() {

        if (player == null || playlistContainer == null) {
            return;
        }

        // 기존 노래 목록 제거
        playlistContainer.removeAllViews();

        int currentIndex = player.getCurrentMediaItemIndex();

        for (int i = 0; i < player.getMediaItemCount(); i++) {

            final int songIndex = i;
            MediaItem item = player.getMediaItemAt(i);
            CharSequence title = item.mediaMetadata.title;
            boolean isCurrent = (i == currentIndex);

            LinearLayout songRow = new LinearLayout(this);
            songRow.setOrientation(LinearLayout.HORIZONTAL);
            songRow.setGravity(Gravity.CENTER_VERTICAL);
            songRow.setPadding(dp(22), 0, dp(22), 0);
            songRow.setBackground(isCurrent
                    ? CarinTheme.roundedStroke(this, CarinTheme.withAlpha(ACCENT, 0x26),
                    CarinTheme.withAlpha(ACCENT, 0x80), 1, 20)
                    : CarinTheme.rounded(this, 0x0FFFFFFF, 20));
            songRow.setForeground(CarinTheme.ripple(CarinTheme.rounded(this, 0xFFFFFFFF, 20)));

            TextView number = text(String.format(java.util.Locale.ROOT, "%02d", i + 1),
                    18, isCurrent ? ACCENT : CarinTheme.FAINT, CarinTheme.MEDIUM);
            songRow.addView(number, new LinearLayout.LayoutParams(dp(48), -2));

            // 커버 (assets/covers). 곡마다 다른 그림이라 목록을 한눈에 구분할 수 있다
            SongCatalog.Song song = SongCatalog.findById(item.mediaId);
            ImageView cover = new ImageView(this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(CarinTheme.sunset(this, 14));
            cover.setClipToOutline(true);
            if (song != null) {
                ImageLoader.load(cover, ImageLoader.assetUri(song.coverAsset), ImageLoader.SLOT_HERO, 200, null);
            }
            LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(dp(60), dp(60));
            coverParams.rightMargin = dp(18);
            songRow.addView(cover, coverParams);

            LinearLayout names = new LinearLayout(this);
            names.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(title != null ? title.toString() : "제목 없음", 20,
                    WHITE, isCurrent ? CarinTheme.BOLD : CarinTheme.MEDIUM);
            name.setSingleLine(true);
            names.addView(name);
            // 이 곡과 연결된 추억이 있으면 장소를, 없으면 곡 분위기를 보여 준다
            String linkedPlaces = song == null ? "" : memoryPlaces(memoriesForSong(song.id), 2);
            TextView sub = text(linkedPlaces.isEmpty()
                            ? (song == null ? "" : song.mood)
                            : "추억 · " + linkedPlaces,
                    16, linkedPlaces.isEmpty() ? MUTED : CarinTheme.LIGHT_ROSE, CarinTheme.REGULAR);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            names.addView(sub, below(-1, -2, 2));
            songRow.addView(names, new LinearLayout.LayoutParams(0, -2, 1));

            if (isCurrent) {
                TextView playing = text("재생 중", 16, ACCENT, CarinTheme.MEDIUM);
                LinearLayout.LayoutParams playingParams = new LinearLayout.LayoutParams(-2, -2);
                playingParams.rightMargin = dp(10);
                songRow.addView(playing, playingParams);
            }
            songRow.addView(CarinTheme.icon(this,
                            isCurrent ? CarinIcon.Type.NOTE : CarinIcon.Type.PLAY,
                            isCurrent ? ACCENT : MUTED),
                    new LinearLayout.LayoutParams(dp(24), dp(24)));

            // 목록에서 곡을 누르면 해당 곡 재생
            songRow.setOnClickListener(v -> {
                playAt(songIndex);
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(88));
            params.bottomMargin = dp(10);
            playlistContainer.addView(songRow, params);
        }
    }

    /**
     * 특정 곡과 연결된 추억 (최신순).
     *
     * @param songId 곡 ID
     * @return 그 곡을 "그날의 노래"로 남긴 추억 목록
     */
    private List<Memory> memoriesForSong(String songId) {
        List<Memory> matched = new ArrayList<>();
        for (Memory memory : memoryStore.getAll()) {
            if (memory.songId.equals(songId)) matched.add(memory);
        }
        return matched;
    }

    // ───────────────────────────────────────────
    // Memory Route: 추억 저장 → 목적지 매칭 → 그날의 노래 재생
    // ───────────────────────────────────────────

    // ── NaviHelper SDK 목적지 ──

    private void initializeNavi() {
        naviClient = new NaviDestinationClient();
        naviClient.connect(this, new NaviDestinationClient.Callback() {
            @Override
            public void onConnected() {
                sdkDestinationText.setText("실제 내비 목적지 (SDK): 연결됨 · 조회 중...");
                naviClient.requestDestination();
            }

            @Override
            public void onConnectionFailed(Throwable error) {
                sdkDestinationText.setText(
                        "실제 내비 목적지 (SDK): 연결 실패 · 데모 목적지를 이용해 주세요");
                sdkDestinationText.setTextColor(WARN);
            }

            @Override
            public void onDestination(DestinationInfo info) {
                handleSdkDestination(info);
            }

            @Override
            public void onDestinationChanged() {
                // 내비 앱에서 목적지가 바뀌면 다시 조회하여 화면에 반영
                naviClient.requestDestination();
            }

            @Override
            public void onNaviError(NaviErrorCode code) {
                Toast.makeText(MainActivity.this,
                        "내비 오류: " + code, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void querySdkDestination() {
        if (naviClient == null || !naviClient.isConnected()) {
            Toast.makeText(this,
                    "Navi Service에 연결되어 있지 않습니다. 데모 목적지를 이용해 주세요.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        sdkDestinationText.setText("실제 내비 목적지 (SDK): 조회 중...");
        naviClient.requestDestination();
    }

    private void handleSdkDestination(DestinationInfo info) {
        String name = info == null ? "" : safe(info.getName());
        String address = info == null ? "" : safe(info.getAddress());

        boolean requested = userRequestedNaviQuery;
        userRequestedNaviQuery = false;
        if (name.isEmpty() && address.isEmpty()) {
            sdkDestinationText.setText(
                    "실제 내비 목적지 (SDK): 설정된 목적지 없음 · 조회 성공");
            sdkDestinationText.setTextColor(MUTED);
            if (requested) {
                Toast.makeText(this, "내비에 설정된 목적지가 없어요. 시연용 목적지를 골라 보세요.",
                        Toast.LENGTH_LONG).show();
            }
            // SDK 목적지가 사라졌고 화면이 SDK 기준이었다면 매칭도 초기화
            if (destinationSource == DestinationSource.SDK) {
                applyDestination(DestinationSource.NONE, "", "");
            }
            return;
        }

        sdkDestinationText.setText(
                "실제 내비 목적지 (SDK): " + name
                        + (address.isEmpty() ? "" : " · " + address));
        sdkDestinationText.setTextColor(ACCENT);

        // 실제 목적지가 있으면 데모 목적지보다 우선한다
        if (destinationSource == DestinationSource.DEMO) {
            demoDestinationText.setText("데모 목적지: 해제됨 (실제 목적지 우선)");
            demoDestinationText.setTextColor(MUTED);
        }
        applyDestination(DestinationSource.SDK, name, address);
    }

    // ── 목적지 고르기 (실제 내비 목적지 불러오기 + 시연용 데모 목적지) ──

    /**
     * 목적지를 고르는 단일 창. 헤더 목적지 칩과 히어로의 "목적지 고르기"가 모두 여기로 온다.
     * 추억이 있는 데모 목적지에는 음표를 붙여 처음 보는 사람도 어디를 눌러야 할지 알 수 있게 한다.
     */
    private void showDestinationPicker() {
        CarinDialog dialog = new CarinDialog(this, "목적지 고르기",
                "목적지를 고르면 그 길에서 들었던 추억과 노래를 찾아드려요.");

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        // 1) 실제 내비 목적지 (NaviHelper SDK)
        LinearLayout naviRow = new LinearLayout(this);
        naviRow.setGravity(Gravity.CENTER_VERTICAL);
        String naviStatus = String.valueOf(sdkDestinationText.getText())
                .replace("실제 내비 목적지 (NaviHelper SDK): ", "")
                .replace("실제 내비 목적지 (SDK): ", "");
        naviRow.addView(text("내비 목적지 · " + naviStatus, 17, MUTED, CarinTheme.REGULAR),
                new LinearLayout.LayoutParams(0, -2, 1));
        PillButton naviButton = new PillButton(this, "내비에서 불러오기", CarinIcon.Type.NAV, PillButton.Style.GHOST);
        naviButton.setOnClickListener(v -> {
            dialog.dismiss();
            userRequestedNaviQuery = true;
            querySdkDestination();
        });
        naviRow.addView(naviButton, new LinearLayout.LayoutParams(-2, dp(60)));
        content.addView(naviRow);

        // 2) 시연용 데모 목적지
        content.addView(text("시연용 목적지 · 화면에 [DEMO]로 표시돼요 · ♪ 추억이 있는 곳", 16, WARN,
                CarinTheme.MEDIUM), below(-1, -2, 24));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        content.addView(grid, below(-1, -2, 12));
        LinearLayout row = null;
        for (int i = 0; i < DEMO_DESTINATIONS.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, below(-1, dp(64), i == 0 ? 0 : 12));
            }
            String picked = DEMO_DESTINATIONS[i];
            boolean hasMemory = !memoryStore.findMatching(picked, "").isEmpty();
            PillButton option = new PillButton(this, hasMemory ? picked + " ♪" : picked,
                    CarinIcon.Type.PIN, PillButton.Style.TAB);
            option.setTabSelected(destinationSource == DestinationSource.DEMO
                    && picked.equals(destinationName));
            option.setOnClickListener(v -> {
                dialog.dismiss();
                demoDestinationText.setText("데모 목적지: " + picked + "  [DEMO]");
                demoDestinationText.setTextColor(WARN);
                applyDestination(DestinationSource.DEMO, picked, "");
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
            if (i % 3 > 0) params.leftMargin = dp(12);
            row.addView(option, params);
        }
        dialog.setContent(content);

        if (destinationSource == DestinationSource.DEMO) {
            dialog.addButton("데모 목적지 해제", CarinIcon.Type.CLOSE, PillButton.Style.GHOST, () -> {
                dialog.dismiss();
                demoDestinationText.setText("데모 목적지: 선택 안 함");
                demoDestinationText.setTextColor(MUTED);
                applyDestination(DestinationSource.NONE, "", "");
            });
        }
        dialog.show();
    }

    // ── 목적지 매칭 ──

    /** 목적지를 확정하고 추억 매칭 · 히어로 카드 갱신 · 1회 제안을 수행한다 */
    private void applyDestination(DestinationSource source, String name, String address) {
        destinationSource = source;
        destinationName = name;
        destinationAddress = address;

        String key = source == DestinationSource.NONE ? null
                : source + "|" + PlaceMatcher.normalize(name)
                + "|" + PlaceMatcher.normalize(address);
        boolean newDestination = key != null && !key.equals(lastPromptedKey);
        if (newDestination) {
            heroDismissed = false;
            heroIndex = 0;
        }

        List<Memory> matched = refreshMemoryViews();

        if (source == DestinationSource.NONE) {
            lastPromptedKey = null;
            return;
        }

        if (matched.isEmpty()) {
            lastPromptedKey = key;
            return;
        }

        // 같은 목적지를 반복 조회해도 제안은 한 번만
        if (!newDestination) return;
        lastPromptedKey = key;

        showMemoryPrompt();
    }

    /** 추억 발견 제안: 플레이리스트 탭의 히어로 카드로 이동해 강조한다 */
    private void showMemoryPrompt() {
        if (!isKaraokeOpen()) selectTab(TAB_PLAYLIST);
        playlistPage.smoothScrollTo(0, 0);
        heroCard.animate().scaleX(1.015f).scaleY(1.015f).setDuration(220)
                .withEndAction(() -> heroCard.animate().scaleX(1f).scaleY(1f).setDuration(260))
                .start();
    }

    /** 현재 목적지와 일치하는 추억으로 히어로·목적지 칩·추억 목록을 갱신한다. 매칭 결과를 반환 */
    private List<Memory> refreshMemoryViews() {
        heroMatches = destinationSource == DestinationSource.NONE
                ? new ArrayList<>()
                : memoryStore.findMatching(destinationName, destinationAddress);
        if (heroIndex >= heroMatches.size()) heroIndex = 0;

        renderHero();

        if (destinationSource == DestinationSource.NONE) {
            destinationChip.setText("목적지 고르기");
            CarinTheme.styleChip(destinationChip, CarinTheme.LIGHT_ROSE);
        } else {
            destinationChip.setText(sourceLabel());
            CarinTheme.styleChip(destinationChip,
                    destinationSource == DestinationSource.DEMO ? WARN : ACCENT);
        }

        if (selectedTab == TAB_MEMORY) refreshMemoryPage();
        // 곡 목록의 "추억 · 장소" 줄도 추억 변경에 맞춰 다시 그린다
        updatePlaylistUI();
        return heroMatches;
    }

    // ── 재생: 서비스 플레이리스트(mediaId = SongCatalog.id)에서 곡을 찾아 재생 ──

    private void playMemorySong(Memory memory) {
        if (player == null) {
            Toast.makeText(this, "플레이어가 준비되지 않았습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < player.getMediaItemCount(); i++) {
            if (memory.songId.equals(player.getMediaItemAt(i).mediaId)) {
                playAt(i);
                Toast.makeText(this,
                        memory.place + "에서 들었던 '" + SongCatalog.titleOf(memory.songId)
                                + "'을(를) 재생합니다",
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Toast.makeText(this,
                "저장된 음악을 플레이리스트에서 찾을 수 없습니다 (" + memory.songId + ")",
                Toast.LENGTH_LONG).show();
    }

    // ── 추억 등록 / 목록 / 상세 / 데모 데이터 ──

    private void openRegisterDialog() {
        if (!ensureParkedForRestrictedFeature()) return;
        // 입력을 다 받은 뒤 저장이 막히지 않도록 쓰기 보호 상태면 폼을 열기 전에 안내한다
        if (showIfWriteProtected()) return;

        String defaultPlace = destinationSource == DestinationSource.NONE
                ? null : destinationName;

        restrictedDialog = MemoryRegisterDialog.show(this, memoryStore, defaultPlace,
                memory -> {
                    // 저장으로 직전 원문이 바뀌었으므로 더 이상 참조되지 않는 대기 사진을 정리
                    sweepReleasedPhotos();
                    refreshMemoryViews();
                });
    }

    private final MemoryListPage.Listener memoryPageListener = new MemoryListPage.Listener() {
        @Override
        public void onPlay(Memory memory) {
            playMemorySong(memory);
        }

        @Override
        public void onDetail(Memory memory) {
            showMemoryDetail(memory);
        }

        @Override
        public void onRegister() {
            openRegisterDialog();
        }

        @Override
        public void onToggleDemo(boolean demosPresent) {
            if (demosPresent) confirmDeleteDemos();
            else createDemoData();
        }
    };

    private void showMemoryDetail(Memory memory) {
        detailDialog = MemoryDetailDialog.show(this, memory, new MemoryDetailDialog.Listener() {
            @Override
            public void onPlay(Memory target) {
                playMemorySong(target);
            }

            @Override
            public void onKaraoke(Memory target) {
                // 추억의 노래로 이동한 뒤 그 추억의 사진을 배경으로 노래방 진입
                playMemorySong(target);
                openKaraoke(target);
            }

            @Override
            public boolean onPickPhoto(Memory target) {
                if (!ensureParkedForRestrictedFeature()) return false;
                // 사진을 복사한 뒤 저장이 막히면 고아 파일이 생기므로 선택기를 열기 전에 확인
                if (showIfWriteProtected()) return false;
                pendingPhotoMemoryId = target.id;
                return startPhotoPicker(REQ_PICK_MEMORY_PHOTO);
            }

            @Override
            public void onDelete(Memory target) {
                confirmDeleteMemory(target);
            }
        });
    }

    /** 주차 상태에서만, 확인 후 추억을 삭제한다 */
    private void confirmDeleteMemory(Memory memory) {
        if (!ensureParkedForRestrictedFeature()) return;
        if (showIfWriteProtected()) return;

        CarinDialog confirm = new CarinDialog(this, "추억 삭제", null);
        confirm.setMessage("'" + memory.title + "' 기록을 삭제할까요?\n삭제한 추억은 되돌릴 수 없어요.");
        confirm.addButton("취소", null, PillButton.Style.GHOST, confirm::dismiss);
        confirm.addButton("삭제", CarinIcon.Type.TRASH, PillButton.Style.PRIMARY, () -> {
            confirm.dismiss();
            // 확인 창을 띄운 뒤 주행을 시작했을 수 있으므로 실제 삭제 직전에 다시 확인
            if (!ensureParkedForRestrictedFeature()) return;
            if (memoryStore.delete(memory.id)) {
                Toast.makeText(this, "삭제되었습니다", Toast.LENGTH_SHORT).show();
                // 지운 추억의 사진도 삭제 대기에 넣는다. 직전 원문(memories_prev)이 아직 참조하므로
                // 이번에는 남고, 다음 저장으로 직전 원문에서도 빠지면 그때 지워진다
                memoryStore.deferPhotoRelease(memory.photoUri);
                sweepReleasedPhotos();
                if (detailDialog != null) detailDialog.dismiss();
                refreshMemoryViews();
            } else if (!showIfWriteProtected()) {
                Toast.makeText(this, "삭제에 실패했습니다", Toast.LENGTH_SHORT).show();
            }
        });
        restrictedDialog = confirm;
        confirm.show();
    }

    // ── 카인 노래방 (주차 상태에서만, 기존 플레이어 공유) ──

    private final KaraokePanel.Listener karaokeListener = new KaraokePanel.Listener() {
        @Override
        public void onClose() {
            closeKaraoke();
        }

        @Override
        public void onPlayPause() {
            togglePlay();
        }

        @Override
        public void onPrevious() {
            skipPrevious();
        }

        @Override
        public void onNext() {
            skipNext();
        }

        @Override
        public void onPickPhoto() {
            if (!ensureParkedForRestrictedFeature()) return;
            startPhotoPicker(REQ_PICK_KARAOKE_PHOTO);
        }

        @Override
        public void onClearPhoto() {
            // 직접 고른 사진·고정 추억을 모두 해제하고 기본 배경(assets karaoke → 그라디언트)으로
            Uri previous = loadKaraokePhoto();
            if (saveKaraokePhoto(null)) {
                // 해제된 사진은 추억이 참조하지 않으면 정리된다
                releaseReplacedPhoto(previous == null ? null : previous.toString(), null);
            }
            karaokeSessionMemory = null;
            karaokePanel.setBackgroundPhoto(null);
        }
    };

    private boolean isKaraokeOpen() {
        return karaokePanel != null && karaokePanel.getVisibility() == View.VISIBLE;
    }

    /**
     * 노래방 화면을 연다. 가사·사진은 주차 상태에서만 허용된다.
     *
     * @param sessionMemory 추억 상세에서 들어온 경우 그 추억 (배경으로 고정). 탭에서 열면 null
     */
    private void openKaraoke(Memory sessionMemory) {
        if (!ensureParkedForRestrictedFeature()) return;
        if (player == null) {
            Toast.makeText(this, "플레이어가 준비되지 않았습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        MediaItem current = player.getCurrentMediaItem();
        karaokePanel.setSong(
                current == null ? null : current.mediaId,
                current == null ? null : current.mediaMetadata.title);
        karaokePanel.setPlaying(player.isPlaying());
        karaokePanel.setProgress(player.getCurrentPosition(), player.getDuration());
        karaokeSessionMemory = sessionMemory;
        applyKaraokeBackground();
        karaokePanel.setVisibility(View.VISIBLE);
        // 로터리·방향키 포커스가 노래방 뒤에 가려진 메인 화면으로 새지 않게 막고, 재생 버튼에서 시작한다
        mainContent.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        karaokePanel.focusPrimaryControl();
        updateTabHighlight();
    }

    /**
     * 노래방 배경 우선순위:
     * 1) 추억 상세에서 들어온 추억의 사진
     * 2) 지금 재생 중인 곡과 연결된 추억 중 사진이 있는 가장 최근 추억
     * 3) 노래방에서 직접 고른 사진 (prefs)
     * 4) assets/backgrounds/karaoke.* → default.* → 노을 그라디언트
     */
    private void applyKaraokeBackground() {
        Memory memory = karaokeSessionMemory != null && karaokeSessionMemory.photoUri != null
                ? karaokeSessionMemory : null;
        if (memory == null && player != null && player.getCurrentMediaItem() != null) {
            String songId = player.getCurrentMediaItem().mediaId;
            for (Memory candidate : memoryStore.getAll()) { // 여행 날짜 최신순
                if (candidate.photoUri != null && candidate.songId.equals(songId)) {
                    memory = candidate;
                    break;
                }
            }
        }
        if (memory != null) {
            karaokePanel.setBackgroundPhoto(Uri.parse(memory.photoUri), "추억 사진 · " + memory.title);
        } else {
            karaokePanel.setBackgroundPhoto(loadKaraokePhoto(), "내 사진");
        }
    }

    private void closeKaraoke() {
        karaokePanel.setVisibility(View.GONE);
        mainContent.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        updateTabHighlight();
    }

    // ── 첫 실행 안내 ──

    /** 아직 안내를 보지 않았고 주차 중이면 안내를 띄운다 (읽을 거리라 주행 중에는 미룬다) */
    private void maybeShowOnboarding() {
        boolean done = getSharedPreferences(PREFS_GUIDE, MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDING_DONE, false);
        if (!done && safetyGuard.check().isParkedSafe()) showOnboarding();
    }

    /** 사용법 안내를 처음부터 보여 준다 (헤더의 "사용법" 버튼) */
    private void showOnboarding() {
        if (!safetyGuard.check().isParkedSafe()) {
            Toast.makeText(this, "사용법은 차를 세운 뒤에 볼 수 있어요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isKaraokeOpen()) closeKaraoke();
        mainContent.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        onboardingPanel.start();
    }

    /**
     * 안내가 끝났을 때. 마지막 장에서 체험을 고르면 데모 추억이 있는 강릉역을 목적지로 정한다.
     *
     * @param tryDemo "강릉역으로 체험하기"를 눌렀는지
     */
    private void onOnboardingFinished(boolean tryDemo) {
        getSharedPreferences(PREFS_GUIDE, MODE_PRIVATE).edit()
                .putBoolean(KEY_ONBOARDING_DONE, true).apply();
        mainContent.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (!tryDemo) return;
        if (memoryStore.findMatching(DEMO_DESTINATIONS[0], "").isEmpty()) createDemoData();
        selectTab(TAB_PLAYLIST);
        playlistPage.smoothScrollTo(0, 0);
        demoDestinationText.setText("데모 목적지: " + DEMO_DESTINATIONS[0] + "  [DEMO]");
        demoDestinationText.setTextColor(WARN);
        applyDestination(DestinationSource.DEMO, DEMO_DESTINATIONS[0], "");
    }

    @Override
    public void onBackPressed() {
        if (onboardingPanel != null && onboardingPanel.isShowing()) {
            onboardingPanel.dismiss();
            return;
        }
        if (isKaraokeOpen()) {
            closeKaraoke();
            return;
        }
        // Pleos CCP 가이드: 음악 앱은 Back에서 종료(Finish)하지 않고 숨긴다(Hide). 재생도 그대로 둔다
        if (!moveTaskToBack(true)) super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 백그라운드에 있는 동안 주행을 시작했을 수 있으므로 노래방이 열려 있으면 다시 확인
        if (isKaraokeOpen()) {
            DrivingSafetyGuard.Result result = safetyGuard.check();
            updateSafetyText(result);
            if (!result.isParkedSafe()) {
                closeKaraoke();
                Toast.makeText(this, result.detail, Toast.LENGTH_LONG).show();
            }
        }
    }

    // ── 사진 선택 (기기에서 실제 동작하는 선택기 사용, 선택본은 앱 내부에 복사) ──

    private boolean startPhotoPicker(int requestCode) {
        Intent intent = PhotoPicker.buildIntent(this);
        if (intent == null) {
            Toast.makeText(this, "이 기기에는 사용할 수 있는 사진 선택 앱이 없습니다.",
                    Toast.LENGTH_LONG).show();
            pendingPhotoMemoryId = null;
            return false;
        }
        try {
            startActivityForResult(intent, requestCode);
            return true;
        } catch (Exception e) {
            Log.w("CARIN_PHOTO", "사진 선택기를 열 수 없음", e);
            Toast.makeText(this, "사진 선택기를 열 수 없습니다.", Toast.LENGTH_LONG).show();
            pendingPhotoMemoryId = null;
            return false;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        readDebugFaults(intent);
    }

    /**
     * debug 빌드에서만 실패 주입 플래그를 읽는다. 예)
     * adb shell am start -n com.carin.noraebang/.MainActivity --ez carin_debug_fail_photo_save true
     * adb shell am start -n com.carin.noraebang/.MainActivity --ez carin_debug_fail_photo_write true
     * 주행 상태 변경 재현(실행 중인 화면에 전달해야 하므로 --activity-single-top 필요):
     * adb shell am start --activity-single-top -n com.carin.noraebang/.MainActivity --ez carin_debug_simulate_driving true
     */
    private void readDebugFaults(Intent intent) {
        if (intent == null || (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) return;
        if (intent.hasExtra("carin_debug_simulate_driving")) {
            safetyGuard.simulateForDebug(intent.getBooleanExtra("carin_debug_simulate_driving", false));
        }
        if (!intent.hasExtra("carin_debug_fail_photo_save")
                && !intent.hasExtra("carin_debug_fail_photo_write")) return;
        if (intent.hasExtra("carin_debug_fail_photo_save")) {
            debugFailPhotoSave = intent.getBooleanExtra("carin_debug_fail_photo_save", false);
        }
        if (intent.hasExtra("carin_debug_fail_photo_write")) {
            PhotoImporter.debugFailWrite = intent.getBooleanExtra("carin_debug_fail_photo_write", false);
        }
        Log.w("CARIN_PHOTO", "debug 실패 주입: save=" + debugFailPhotoSave
                + " write=" + PhotoImporter.debugFailWrite);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_KARAOKE_PHOTO && requestCode != REQ_PICK_MEMORY_PHOTO) return;

        Uri picked = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
        Log.d("CARIN_PHOTO", "선택 결과 resultCode=" + resultCode + " uri=" + picked);
        final String memoryId = pendingPhotoMemoryId;
        pendingPhotoMemoryId = null;
        if (picked == null) return;

        // 선택기에 있는 동안 저장소가 보호 상태가 됐을 수 있으므로 가져오기(파일 생성) 전에 다시 확인
        if (requestCode == REQ_PICK_MEMORY_PHOTO && showIfWriteProtected()) return;

        // 선택기 URI는 세션이 끝나면 접근이 막힐 수 있으므로 축소·방향 보정한 앱 내부 JPEG 사본을 쓴다.
        // 큰 사진의 디코딩이 화면을 멈추지 않도록 백그라운드에서 처리한다.
        Toast.makeText(this, "사진을 준비하는 중이에요…", Toast.LENGTH_SHORT).show();
        PhotoImporter.importAsync(this, picked, result -> {
            if (activityDestroyed) {
                // 결과를 저장할 화면이 없으므로 새 파일만 정리 (기존 연결은 그대로)
                if (result.ok()) PhotoImporter.deleteOwnedPhoto(this, result.uri.toString());
                return;
            }
            if (!result.ok()) {
                // 변환 실패: 기존 사진 연결을 그대로 두고 이유만 안내 (임시 파일은 PhotoImporter가 정리)
                Toast.makeText(this, result.message(), Toast.LENGTH_LONG).show();
                return;
            }
            if (requestCode == REQ_PICK_KARAOKE_PHOTO) {
                applyKaraokePhoto(result.uri);
            } else {
                applyMemoryPhoto(memoryId, result.uri);
            }
        });
    }

    /**
     * 가져온 새 사진을 추억에 연결한다. 저장에 실패하면 새 파일을 지우고 기존 연결을 유지한다.
     * 성공하면 더 이상 참조되지 않는 이전 사진 파일만 지운다.
     */
    private void applyMemoryPhoto(String memoryId, Uri newPhoto) {
        String newUri = newPhoto.toString();
        Memory target = memoryStore.findById(memoryId);
        Memory updated = target == null ? null : target.withPhoto(newUri);
        boolean saved = updated != null
                && !memoryStore.isWriteProtected()
                && !debugFailPhotoSave
                && memoryStore.update(updated);

        if (!saved) {
            PhotoImporter.deleteOwnedPhoto(this, newUri);
            if (debugFailPhotoSave) Log.w("CARIN_PHOTO", "debug 실패 주입: 추억 저장 실패로 처리");
            if (target == null) {
                Toast.makeText(this, "사진을 연결할 추억을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            } else if (!showIfWriteProtected()) {
                Toast.makeText(this, "사진 연결에 실패했어요. 기존 사진은 그대로 유지됩니다.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        releaseReplacedPhoto(target.photoUri, newUri);
        Toast.makeText(this, "'" + target.title + "'에 사진을 연결했습니다.", Toast.LENGTH_SHORT).show();
        refreshMemoryViews();
        showMemoryDetail(updated);
    }

    /** 노래방에서 고른 새 사진을 저장한다. 실패하면 새 파일을 지우고 기존 배경을 유지한다 */
    private void applyKaraokePhoto(Uri newPhoto) {
        String newUri = newPhoto.toString();
        Uri previous = loadKaraokePhoto();
        if (debugFailPhotoSave || !saveKaraokePhoto(newPhoto)) {
            PhotoImporter.deleteOwnedPhoto(this, newUri);
            Toast.makeText(this, "노래방 배경을 저장하지 못했어요. 기존 배경은 그대로 유지됩니다.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        releaseReplacedPhoto(previous == null ? null : previous.toString(), newUri);
        // 노래방에서 직접 고른 사진은 이번 세션에서 추억 사진보다 우선해 바로 보여준다
        karaokeSessionMemory = null;
        karaokePanel.setBackgroundPhoto(newPhoto, "내 사진");
    }

    /**
     * 교체되어 빠진 이전 사진을 삭제 대기 목록에 넣고, 지금 지워도 되는 대기 사진을 정리한다.
     * 교체 직후 이전 사진은 memories_prev(복원용 직전 원문)가 참조하므로 바로 지워지지 않는다.
     */
    private void releaseReplacedPhoto(String oldUri, String newUri) {
        if (oldUri != null && !oldUri.equals(newUri)) {
            memoryStore.deferPhotoRelease(oldUri);
        }
        sweepReleasedPhotos();
    }

    /**
     * 삭제 대기 사진 중 현재 원문·직전 원문·손상 백업·노래방 설정 어디에서도 참조하지 않는
     * 앱 소유 파일만 지운다. 추억 저장(등록·삭제·사진 연결·데모 생성) 성공 뒤마다 호출한다.
     */
    private void sweepReleasedPhotos() {
        Set<String> pending = memoryStore.pendingPhotoReleases();
        if (pending.isEmpty()) return;
        List<String> inUse = new ArrayList<>();
        Uri karaoke = loadKaraokePhoto();
        if (karaoke != null) inUse.add(karaoke.toString());

        List<String> releasable = PhotoRetention.releasable(pending, memoryStore.snapshotTexts(), inUse);
        for (String uri : releasable) {
            PhotoImporter.deleteOwnedPhoto(this, uri);
        }
        memoryStore.forgetPhotoReleases(releasable);
        Log.d("CARIN_PHOTO", "삭제 대기 사진 정리: 대기 " + pending.size()
                + " · 삭제 " + releasable.size() + " · 보관본 참조로 유지 " + (pending.size() - releasable.size()));
    }

    /** 노래방 배경 사진 설정을 동기 저장한다. 저장 성공 시 true */
    private boolean saveKaraokePhoto(Uri uri) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_KARAOKE, MODE_PRIVATE).edit();
        if (uri == null) editor.remove(KEY_KARAOKE_PHOTO);
        else editor.putString(KEY_KARAOKE_PHOTO, uri.toString());
        return editor.commit();
    }

    private Uri loadKaraokePhoto() {
        String saved = getSharedPreferences(PREFS_KARAOKE, MODE_PRIVATE)
                .getString(KEY_KARAOKE_PHOTO, null);
        return saved == null ? null : Uri.parse(saved);
    }

    /** 데모 추억(강릉·속초·평창)을 넣는다. 이미 있는 것은 건너뛴다 */
    private void createDemoData() {
        if (showIfWriteProtected()) return;
        int created = memoryStore.createDemos();
        if (created == 0) {
            if (!showIfWriteProtected()) {
                Toast.makeText(this, memoryStore.hasDemo()
                        ? "데모 추억이 이미 있어요." : "재생 가능한 곡이 없어 데모를 만들 수 없어요.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        Toast.makeText(this, "데모 추억 " + created + "개를 넣었어요 · 강릉 · 속초 · 평창",
                Toast.LENGTH_LONG).show();
        sweepReleasedPhotos();
        refreshMemoryViews();
    }

    /** 주차 중에만, 확인 후 데모 추억을 모두 지운다 (사용자가 직접 남긴 추억은 그대로) */
    private void confirmDeleteDemos() {
        if (!ensureParkedForRestrictedFeature()) return;
        if (showIfWriteProtected()) return;
        CarinDialog confirm = new CarinDialog(this, "데모 추억 지우기", null);
        confirm.setMessage("[데모]로 표시된 추억만 지워요.\n직접 남긴 추억은 그대로 남아요.");
        confirm.addButton("취소", null, PillButton.Style.GHOST, confirm::dismiss);
        confirm.addButton("지우기", CarinIcon.Type.TRASH, PillButton.Style.PRIMARY, () -> {
            confirm.dismiss();
            if (!ensureParkedForRestrictedFeature()) return;
            if (memoryStore.deleteDemos()) {
                Toast.makeText(this, "데모 추억을 지웠어요", Toast.LENGTH_SHORT).show();
                sweepReleasedPhotos();
                refreshMemoryViews();
            } else if (!showIfWriteProtected()) {
                Toast.makeText(this, "지울 데모 추억이 없어요", Toast.LENGTH_SHORT).show();
            }
        });
        restrictedDialog = confirm;
        confirm.show();
    }

    // ── 저장 데이터 보호 ──

    /** 저장 데이터를 보호하느라 변경을 막았을 때 보여주는 안내 */
    static final String WRITE_PROTECTED_MESSAGE =
            "저장된 추억 데이터를 읽을 수 없어 기존 기록을 보호하기 위해 변경을 막았어요. 기존 데이터는 그대로 보존됩니다.";

    /**
     * 추억 저장소가 쓰기 보호 상태(원문 배열 손상)이면 안내 토스트를 띄우고 true를 반환한다.
     * 저장 실패 원인을 일반 실패와 구분할 때도 쓴다.
     */
    private boolean showIfWriteProtected() {
        if (!memoryStore.isWriteProtected()) return false;
        Toast.makeText(this, WRITE_PROTECTED_MESSAGE, Toast.LENGTH_LONG).show();
        return true;
    }

    /** 추억 탭 목록과 저장 상태 칩을 갱신한다 */
    private void refreshMemoryPage() {
        // 보호 상태가 빈 화면 문구에 반영되도록 상태를 먼저 전달한다
        memoryPage.setStorageStatus(memoryStore.isWriteProtected(), memoryStore.unreadableCount());
        memoryPage.setMemories(memoryStore.getAll());
    }

    // ── 주행 안전 제한 ──

    /** 텍스트 입력·기록 편집 같은 제한 기능 실행 전 주차 상태를 확인한다 */
    private boolean ensureParkedForRestrictedFeature() {
        DrivingSafetyGuard.Result result = safetyGuard.check();
        updateSafetyText(result);
        if (result.isParkedSafe()) return true;

        Toast.makeText(this, result.detail, Toast.LENGTH_LONG).show();
        return false;
    }

    /**
     * 주행 상태 변경 리스너 (메인 스레드). 주차가 아니게 되면 주차 전용 화면을 즉시 닫는다.
     * 음악 재생은 제한하지 않으므로 플레이어는 건드리지 않는다.
     *
     * @param result 바뀐 뒤의 주행 상태
     */
    private void onDrivingStateChanged(DrivingSafetyGuard.Result result) {
        if (activityDestroyed) return;
        updateSafetyText(result);
        if (result.isParkedSafe()) return;

        boolean closed = false;
        if (isKaraokeOpen()) {
            closeKaraoke();
            closed = true;
        }
        if (onboardingPanel != null && onboardingPanel.isShowing()) {
            onboardingPanel.dismiss();
            closed = true;
        }
        if (restrictedDialog != null) {
            restrictedDialog.dismiss();
            restrictedDialog = null;
            closed = true;
        }
        Log.i("CARIN_SAFETY", "주행 상태 변경: " + result.state + (closed ? " → 주차 전용 화면 닫음" : ""));
        if (closed) Toast.makeText(this, result.detail, Toast.LENGTH_LONG).show();
    }

    /** 헤더의 주행 상태 칩 갱신 */
    private void updateSafetyText(DrivingSafetyGuard.Result result) {
        String label;
        int color;
        switch (result.state) {
            case PARKED:
                label = "● 주차 중 · 모든 기능 사용 가능";
                color = CarinTheme.LIGHT_ROSE;
                break;
            case RESTRICTED:
                label = "● 주행 중 · 음악 재생만 가능";
                color = WARN;
                break;
            default:
                label = "● 주행 상태 확인 불가 · 등록/삭제 제한";
                color = WARN;
        }
        safetyStateText.setText(label);
        CarinTheme.styleChip(safetyStateText, color);
        // 주차 중이면 칩을 숨겨 화면을 단순하게 둔다 (제한될 때만 알린다)
        safetyStateText.setVisibility(result.isParkedSafe() ? View.GONE : View.VISIBLE);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }


    // 1. Pleos Vehicle Service 연결
    private void initializeVehicle() {
        try {
            vehicle = new Vehicle(getApplicationContext());
            vehicle.initialize();
            seatStatusText.setText("차량 SDK 연결 완료 · 새로고침을 눌러주세요");
            Log.d("CARIN_VEHICLE", "Vehicle.initialize() 완료");
        } catch (Exception e) {
            Log.e("CARIN_VEHICLE", "Vehicle SDK 초기화 실패", e);
            seatStatusText.setText("차량 SDK 초기화 실패: " + e.getMessage());
            releaseVehicle();
        }
    }

    // 2. 새로고침 버튼을 누르면 조수석 상태를 1회 조회
    private void readSeatOccupancy() {
        if (vehicle == null) {
            seatStatusText.setText("차량 SDK가 준비되지 않았습니다.");
            return;
        }
        if (seatRequestInProgress) {
            return; // 비동기 요청 중 중복 호출 방지
        }

        seatRequestInProgress = true;
        refreshSeatButton.setEnabled(false);
        seatStatusText.setText("조수석 상태 조회 중...");

        try {
            Seat seat = vehicle.getSeat();
            SeatArea passengerSeat = new SeatArea(SeatValue.ROW_1_RIGHT);

            seat.getCurrentSeatOccupancy(
                    passengerSeat,
                    occupancy -> {
                        Log.d("CARIN_VEHICLE", "조수석 조회 성공: " + occupancy);
                        runOnUiThread(() -> finishSeatRequest(
                                "조수석 SDK 원본값: " + String.valueOf(occupancy)
                        ));
                        return Unit.INSTANCE;
                    },
                    error -> {
                        Log.e("CARIN_VEHICLE", "조수석 조회 실패", error);
                        runOnUiThread(() -> finishSeatRequest(
                                "조회 실패: " + error.getMessage()
                        ));
                        return Unit.INSTANCE;
                    }
            );
        } catch (Exception e) {
            Log.e("CARIN_VEHICLE", "조수석 조회 호출 실패", e);
            finishSeatRequest("조회 호출 오류: " + e.getMessage());
        }
    }

    // 3. SDK 응답이 도착한 후 UI에 반영 (메인 스레드에서 호출)
    private void finishSeatRequest(String message) {
        seatRequestInProgress = false;
        if (activityDestroyed) {
            // 비동기 응답 후 Vehicle SDK를 안전하게 정리
            releaseVehicle();
            return;
        }
        seatStatusText.setText(message);
        refreshSeatButton.setEnabled(true);
    }

    private void releaseVehicle() {
        if (vehicle != null) {
            try {
                vehicle.release();
            } catch (Exception e) {
                Log.e("CARIN_VEHICLE", "Vehicle.release() 실패", e);
            } finally {
                vehicle = null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        uiHandler.removeCallbacksAndMessages(null);
        safetyGuard.stopMonitoring();
        if (naviClient != null) {
            naviClient.release();
            naviClient = null;
        }
        // 조회 콜백이 진행 중인 경우 응답 처리 이후 해제
        if (!seatRequestInProgress) {
            releaseVehicle();
        }
        super.onDestroy();
    }

}
