package com.carin.noraebang.memory;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.carin.noraebang.ui.CarinIcon;
import com.carin.noraebang.ui.CarinTheme;
import com.carin.noraebang.ui.ImageLoader;
import com.carin.noraebang.ui.PillButton;

import java.util.List;

/** "카인 추억" 탭 화면: 저장된 추억을 사진 카드 그리드로 보여준다 */
public final class MemoryListPage extends LinearLayout {

    /** 목록 화면에서 발생하는 동작 */
    public interface Listener {
        /** 해당 추억의 노래를 기존 플레이어로 재생 */
        void onPlay(Memory memory);

        /** 상세 보기 (사진 · 노래방 · 사진 연결 · 삭제) */
        void onDetail(Memory memory);

        /** 등록 다이얼로그 열기 */
        void onRegister();

        /** 강릉 데모 기록 만들기 */
        void onCreateDemo();
    }

    private final Listener listener;
    private final int columns;
    private final LinearLayout grid;
    private final TextView countChip;
    /** 저장 데이터 손상 시 보호 상태를 알리는 칩 (정상일 때 숨김) */
    private final TextView storageChip;

    /** @param columns 한 줄에 놓을 카드 수 */
    public MemoryListPage(Context context, Listener listener, int columns) {
        super(context);
        this.listener = listener;
        this.columns = Math.max(1, columns);
        setOrientation(VERTICAL);

        // 헤더
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(VERTICAL);
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(CarinTheme.text(context, "카인 추억", 30, CarinTheme.GLASS_WHITE, CarinTheme.BOLD));
        countChip = CarinTheme.chip(context, "0개", CarinTheme.LIGHT_ROSE);
        LayoutParams chipParams = new LayoutParams(-2, -2);
        chipParams.leftMargin = dp(16);
        titleRow.addView(countChip, chipParams);
        storageChip = CarinTheme.chip(context, "", CarinTheme.AMBER);
        storageChip.setVisibility(GONE);
        LayoutParams storageParams = new LayoutParams(-2, -2);
        storageParams.leftMargin = dp(12);
        titleRow.addView(storageChip, storageParams);
        titles.addView(titleRow);
        LayoutParams subParams = new LayoutParams(-1, -2);
        subParams.topMargin = dp(6);
        titles.addView(CarinTheme.text(context, "여행의 음악과 사진을, 하나의 이야기로",
                18, CarinTheme.MUTED, CarinTheme.REGULAR), subParams);
        header.addView(titles, new LayoutParams(0, -2, 1));

        PillButton demo = new PillButton(context, "데모 데이터", CarinIcon.Type.SPARKLE, PillButton.Style.GHOST);
        demo.setOnClickListener(v -> listener.onCreateDemo());
        header.addView(demo, buttonParams());
        PillButton register = new PillButton(context, "추억 등록", CarinIcon.Type.PLUS, PillButton.Style.PRIMARY);
        register.setOnClickListener(v -> listener.onRegister());
        header.addView(register, buttonParams());
        addView(header);

        grid = new LinearLayout(context);
        grid.setOrientation(VERTICAL);
        LayoutParams gridParams = new LayoutParams(-1, -2);
        gridParams.topMargin = dp(28);
        addView(grid, gridParams);
    }

    /**
     * 저장 데이터 상태 칩을 갱신한다.
     *
     * @param writeProtected 원문 배열이 손상되어 변경이 차단된 상태
     * @param unreadable     읽지 못했지만 원문으로 보존 중인 레코드 수
     */
    public void setStorageStatus(boolean writeProtected, int unreadable) {
        if (writeProtected) {
            storageChip.setText("저장 데이터 보호 중 · 변경 차단");
        } else if (unreadable > 0) {
            storageChip.setText("읽지 못한 기록 " + unreadable + "개 · 원본 보존");
        } else {
            storageChip.setVisibility(GONE);
            return;
        }
        storageChip.setVisibility(VISIBLE);
    }

    /** 목록을 다시 그린다 */
    public void setMemories(List<Memory> memories) {
        grid.removeAllViews();
        countChip.setText(memories.size() + "개");

        if (memories.isEmpty()) {
            grid.addView(emptyState());
            return;
        }

        LinearLayout row = null;
        for (int i = 0; i < memories.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(HORIZONTAL);
                LayoutParams rowParams = new LayoutParams(-1, -2);
                rowParams.bottomMargin = dp(24);
                grid.addView(row, rowParams);
            }
            row.addView(card(memories.get(i)), cellParams(i % columns));
        }
        // 마지막 줄의 빈 칸을 채워 카드 너비를 일정하게 유지
        int remainder = memories.size() % columns;
        if (remainder != 0 && row != null) {
            for (int i = remainder; i < columns; i++) {
                row.addView(new View(getContext()), cellParams(i));
            }
        }
    }

    private View card(Memory memory) {
        Context c = getContext();
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(VERTICAL);
        card.setBackground(CarinTheme.glass(c, 28));
        card.setClipToOutline(true);
        card.setClickable(true);
        card.setForeground(CarinTheme.ripple(CarinTheme.rounded(c, 0xFFFFFFFF, 28)));
        card.setOnClickListener(v -> listener.onDetail(memory));

        // 사진 영역 (없으면 assets 기본 사진 → 노을 그라디언트)
        FrameLayout photo = new FrameLayout(c);
        photo.setBackground(CarinTheme.sunset(c, 0));
        ImageView image = new ImageView(c);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.addView(image, new FrameLayout.LayoutParams(-1, -1));
        ImageLoader.load(image, memory.photoUri == null ? null : Uri.parse(memory.photoUri),
                ImageLoader.SLOT_MEMORY, 900, null);
        View scrim = new View(c);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x001A191E, 0x331A191E, 0xE61A191E}));
        photo.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        TextView title = CarinTheme.text(c, memory.title, 24, CarinTheme.GLASS_WHITE, CarinTheme.BOLD);
        title.setMaxLines(2);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        titleParams.setMargins(dp(24), 0, dp(24), dp(18));
        photo.addView(title, titleParams);

        if (memory.demo) {
            TextView demo = CarinTheme.chip(c, "데모 데이터", CarinTheme.AMBER);
            FrameLayout.LayoutParams demoParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            demoParams.setMargins(0, dp(16), dp(16), 0);
            photo.addView(demo, demoParams);
        }
        card.addView(photo, new LayoutParams(-1, dp(230)));

        // 메타 정보
        LinearLayout info = new LinearLayout(c);
        info.setOrientation(VERTICAL);
        info.setPadding(dp(24), dp(20), dp(24), dp(22));
        info.addView(CarinTheme.iconRow(c, CarinIcon.Type.CALENDAR, CarinTheme.LIGHT_ROSE,
                CarinTheme.text(c, CarinTheme.displayDate(memory.travelDate), 18,
                        CarinTheme.GLASS_WHITE, CarinTheme.REGULAR), 22));
        info.addView(metaRow(CarinIcon.Type.PIN, memory.place, CarinTheme.GLASS_WHITE));
        info.addView(metaRow(CarinIcon.Type.NOTE, SongCatalog.titleOf(memory.songId), CarinTheme.LIGHT_ROSE));

        LinearLayout actions = new LinearLayout(c);
        actions.setOrientation(HORIZONTAL);
        PillButton play = new PillButton(c, "그날의 노래", CarinIcon.Type.PLAY, PillButton.Style.PRIMARY);
        play.setTextSize(17);
        play.setOnClickListener(v -> listener.onPlay(memory));
        PillButton detail = new PillButton(c, "자세히", CarinIcon.Type.SPARKLE, PillButton.Style.GHOST);
        detail.setTextSize(17);
        detail.setOnClickListener(v -> listener.onDetail(memory));
        LayoutParams playParams = new LayoutParams(0, dp(56), 1);
        playParams.rightMargin = dp(10);
        actions.addView(play, playParams);
        actions.addView(detail, new LayoutParams(0, dp(56), 1));
        LayoutParams actionsParams = new LayoutParams(-1, -2);
        actionsParams.topMargin = dp(20);
        info.addView(actions, actionsParams);

        card.addView(info);
        return card;
    }

    private View metaRow(CarinIcon.Type type, String value, int color) {
        LinearLayout row = CarinTheme.iconRow(getContext(), type, CarinTheme.LIGHT_ROSE,
                CarinTheme.text(getContext(), value, 18, color, CarinTheme.REGULAR), 22);
        LayoutParams params = new LayoutParams(-1, -2);
        params.topMargin = dp(10);
        row.setLayoutParams(params);
        return row;
    }

    private View emptyState() {
        Context c = getContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(40), dp(56), dp(40), dp(56));
        box.setBackground(CarinTheme.glass(c, 32));

        ImageView icon = CarinTheme.icon(c, CarinIcon.Type.SPARKLE, CarinTheme.CORAL);
        box.addView(icon, new LayoutParams(dp(64), dp(64)));
        TextView title = CarinTheme.text(c, "아직 저장된 추억이 없어요", 26, CarinTheme.GLASS_WHITE, CarinTheme.BOLD);
        title.setGravity(Gravity.CENTER);
        LayoutParams titleParams = new LayoutParams(-2, -2);
        titleParams.topMargin = dp(20);
        box.addView(title, titleParams);
        TextView body = CarinTheme.text(c,
                "여행지에서 들었던 노래를 등록하면, 다음에 그 길을 달릴 때 그날의 노래를 다시 들려드릴게요.",
                18, CarinTheme.MUTED, CarinTheme.REGULAR);
        body.setGravity(Gravity.CENTER);
        LayoutParams bodyParams = new LayoutParams(-2, -2);
        bodyParams.topMargin = dp(10);
        box.addView(body, bodyParams);

        PillButton register = new PillButton(c, "첫 추억 등록하기", CarinIcon.Type.PLUS, PillButton.Style.PRIMARY);
        register.setOnClickListener(v -> listener.onRegister());
        LayoutParams registerParams = new LayoutParams(-2, dp(64));
        registerParams.topMargin = dp(28);
        box.addView(register, registerParams);
        return box;
    }

    private LayoutParams buttonParams() {
        LayoutParams params = new LayoutParams(-2, dp(60));
        params.leftMargin = dp(12);
        return params;
    }

    private LayoutParams cellParams(int column) {
        LayoutParams params = new LayoutParams(0, -2, 1);
        if (column > 0) params.leftMargin = dp(24);
        return params;
    }

    private int dp(float value) {
        return CarinTheme.dp(getContext(), value);
    }
}
