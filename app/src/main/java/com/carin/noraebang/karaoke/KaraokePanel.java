package com.carin.noraebang.karaoke;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.carin.noraebang.ui.CarinIcon;
import com.carin.noraebang.ui.CarinTheme;
import com.carin.noraebang.ui.CircleButton;
import com.carin.noraebang.ui.ImageLoader;
import com.carin.noraebang.ui.LogoView;
import com.carin.noraebang.ui.PillButton;
import com.carin.noraebang.ui.ProgressLine;

import java.util.Locale;

/**
 * 카인 노래방 전체 화면 오버레이.
 * 별도 Activity가 아니라 MainActivity 안의 뷰이므로 기존 ExoPlayer를 그대로 공유한다.
 * 재생 제어는 Listener를 통해 MainActivity가 수행한다.
 *
 * 가사는 정적 데모이며 음악과 동기화되지 않는다. 사용자가 가사 영역을 눌러 줄을 넘긴다.
 */
public final class KaraokePanel extends FrameLayout {

    /** 패널 버튼 동작. 실제 재생 제어는 MainActivity가 기존 플레이어로 수행 */
    public interface Listener {
        void onClose();

        void onPlayPause();

        void onPrevious();

        void onNext();

        void onPickPhoto();

        void onClearPhoto();
    }

    private final ImageView backgroundImage;
    private final TextView photoStateText;
    private final TextView songTitleText;
    private final TextView previousLine;
    private final TextView currentLine;
    private final TextView nextLine;
    private final TextView lineCounter;
    private final CircleButton playButton;
    private final ProgressLine progress;
    private final TextView elapsedText;
    private final TextView durationText;
    private String[] lines = new String[0];
    private int lineIndex = 0;

    /** 노래방 오버레이를 만든다 (처음에는 숨김) */
    public KaraokePanel(Context context, Listener listener) {
        super(context);
        setBackgroundColor(CarinTheme.DEEP_VINYL);
        setClickable(true); // 아래 플레이어 화면으로 터치가 새지 않도록
        setFocusable(false); // 터치만 막는 판이므로 로터리 포커스 대상은 아니다

        // 1. 배경 사진 (없으면 assets 기본 사진 → 노을 그라디언트)
        backgroundImage = new ImageView(context);
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backgroundImage.setBackground(CarinTheme.sunset(context, 0));
        addView(backgroundImage, new LayoutParams(-1, -1));

        // 2. 가독성용 스크림 (상단·하단 어둡게)
        View scrim = new View(context);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xD91A191E, 0x8C1A191E, 0x991A191E, 0xF21A191E}));
        addView(scrim, new LayoutParams(-1, -1));

        // 3. 콘텐츠
        // 세로 창(약 840dp)에서는 헤더 안내 칩·배경 상태 문구를 옮기거나 숨겨 글자가 세로로 쪼개지지 않게 한다.
        // 창 크기가 바뀌면 Activity가 다시 만들어지므로 생성 시점의 방향만 보면 된다
        boolean compact = context.getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE;

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(compact ? 32 : 48), dp(28), dp(compact ? 32 : 48), dp(32));
        addView(column, new LayoutParams(-1, -1));

        // 상단 바: 로고 · 제목 · 안내 · 닫기
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(new LogoView(context), compact
                ? new LinearLayout.LayoutParams(dp(150), dp(56))
                : new LinearLayout.LayoutParams(dp(210), dp(76)));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = CarinTheme.text(context, "카인 노래방", 28, CarinTheme.GLASS_WHITE, CarinTheme.BOLD);
        title.setSingleLine(true);
        titles.addView(title);
        TextView subtitle = CarinTheme.text(context, "추억이 배경이 되는, 나만의 노래방", 17,
                CarinTheme.MUTED, CarinTheme.REGULAR);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(subtitle);
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(0, -2, 1);
        titlesParams.leftMargin = dp(20);
        header.addView(titles, titlesParams);

        TextView notice = CarinTheme.chip(context, DemoLyrics.NOTICE, CarinTheme.AMBER);
        if (!compact) header.addView(notice);

        CircleButton close = new CircleButton(context, CarinIcon.Type.CLOSE, CircleButton.Style.GLASS, 28);
        close.setOnClickListener(v -> listener.onClose());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(68), dp(68));
        closeParams.leftMargin = dp(20);
        header.addView(close, closeParams);
        column.addView(header);

        // 곡 제목
        songTitleText = CarinTheme.text(context, "노래 선택", 22, CarinTheme.LIGHT_ROSE, CarinTheme.MEDIUM);
        songTitleText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(20);
        column.addView(songTitleText, titleParams);
        if (compact) {
            // 세로 창: 데모 가사 안내는 헤더 대신 곡 제목 아래 가운데에
            LinearLayout noticeRow = new LinearLayout(context);
            noticeRow.setGravity(Gravity.CENTER);
            noticeRow.addView(notice);
            LinearLayout.LayoutParams noticeParams = new LinearLayout.LayoutParams(-1, -2);
            noticeParams.topMargin = dp(12);
            column.addView(noticeRow, noticeParams);
        }

        // 가사: 이전 줄(흐림) · 현재 줄(코랄 강조) · 다음 줄
        FrameLayout lyricsArea = new FrameLayout(context);
        LinearLayout lyrics = new LinearLayout(context);
        lyrics.setOrientation(LinearLayout.VERTICAL);
        lyrics.setGravity(Gravity.CENTER);
        previousLine = lyricLine(context, 26, CarinTheme.withAlpha(CarinTheme.GLASS_WHITE, 0x73), CarinTheme.REGULAR);
        currentLine = lyricLine(context, compact ? 46 : 56, CarinTheme.CORAL, CarinTheme.BOLD);
        currentLine.setShadowLayer(dp(18), 0, 0, 0x99FF7F88);
        nextLine = lyricLine(context, compact ? 32 : 38, CarinTheme.GLASS_WHITE, CarinTheme.MEDIUM);
        nextLine.setShadowLayer(dp(8), 0, 0, 0xAA000000);
        lineCounter = lyricLine(context, 15, CarinTheme.MUTED, CarinTheme.REGULAR);
        lyrics.addView(previousLine);
        lyrics.addView(currentLine, lineParams(18));
        lyrics.addView(nextLine, lineParams(14));
        lyrics.addView(lineCounter, lineParams(30));
        lyricsArea.addView(lyrics, new LayoutParams(-1, -1));

        // 가사 영역 왼쪽 1/3은 이전 줄, 나머지는 다음 줄 (동기화가 없으므로 사용자가 직접 넘긴다)
        LinearLayout tapZones = new LinearLayout(context);
        View backZone = new View(context);
        backZone.setOnClickListener(v -> showLine(lineIndex - 1));
        View forwardZone = new View(context);
        forwardZone.setOnClickListener(v -> showLine(lineIndex + 1));
        // 가사 넘기기는 터치 전용 부가 기능이다. 로터리 포커스는 하단 조작 버튼으로만 이동한다
        backZone.setFocusable(false);
        forwardZone.setFocusable(false);
        tapZones.addView(backZone, new LinearLayout.LayoutParams(0, -1, 1));
        tapZones.addView(forwardZone, new LinearLayout.LayoutParams(0, -1, 2));
        lyricsArea.addView(tapZones, new LayoutParams(-1, -1));

        LinearLayout.LayoutParams lyricsParams = new LinearLayout.LayoutParams(-1, 0, 1);
        lyricsParams.topMargin = dp(12);
        column.addView(lyricsArea, lyricsParams);

        // 하단 유리 바: 진행 바 + 컨트롤
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(32), dp(22), dp(32), dp(22));
        bar.setBackground(CarinTheme.glass(context, 32));

        LinearLayout progressRow = new LinearLayout(context);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        elapsedText = CarinTheme.text(context, "0:00", 16, CarinTheme.MUTED, CarinTheme.REGULAR);
        durationText = CarinTheme.text(context, "0:00", 16, CarinTheme.MUTED, CarinTheme.REGULAR);
        progress = new ProgressLine(context, true);
        progressRow.addView(elapsedText);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(0, dp(24), 1);
        progressParams.setMargins(dp(16), 0, dp(16), 0);
        progressRow.addView(progress, progressParams);
        progressRow.addView(durationText);
        bar.addView(progressRow);

        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout leftGroup = new LinearLayout(context);
        leftGroup.setGravity(Gravity.CENTER_VERTICAL);
        PillButton pick = new PillButton(context, "사진 선택", CarinIcon.Type.PHOTO, PillButton.Style.GHOST);
        pick.setOnClickListener(v -> listener.onPickPhoto());
        leftGroup.addView(pick, new LinearLayout.LayoutParams(-2, dp(60)));
        photoStateText = CarinTheme.text(context, "배경: 기본", 15, CarinTheme.MUTED, CarinTheme.REGULAR);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-2, -2);
        stateParams.leftMargin = dp(14);
        photoStateText.setSingleLine(true);
        photoStateText.setEllipsize(TextUtils.TruncateAt.END);
        // 세로 창에서는 버튼 사이에 공간이 없어 글자가 세로로 쪼개지므로 숨긴다 (배경은 화면으로 바로 보임)
        if (compact) photoStateText.setVisibility(GONE);
        leftGroup.addView(photoStateText, stateParams);
        controls.addView(leftGroup, new LinearLayout.LayoutParams(0, -2, 1));

        CircleButton prev = new CircleButton(context, CarinIcon.Type.PREV, CircleButton.Style.GHOST, 34);
        prev.setOnClickListener(v -> listener.onPrevious());
        playButton = new CircleButton(context, CarinIcon.Type.PLAY, CircleButton.Style.PRIMARY, 38);
        playButton.setOnClickListener(v -> listener.onPlayPause());
        CircleButton next = new CircleButton(context, CarinIcon.Type.NEXT, CircleButton.Style.GHOST, 34);
        next.setOnClickListener(v -> listener.onNext());
        controls.addView(prev, circleParams(76));
        controls.addView(playButton, circleParams(96));
        controls.addView(next, circleParams(76));

        LinearLayout rightGroup = new LinearLayout(context);
        rightGroup.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        PillButton clear = new PillButton(context, "기본 배경", CarinIcon.Type.CLOSE, PillButton.Style.GHOST);
        clear.setOnClickListener(v -> listener.onClearPhoto());
        rightGroup.addView(clear, new LinearLayout.LayoutParams(-2, dp(60)));
        controls.addView(rightGroup, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(-1, -2);
        controlsParams.topMargin = dp(12);
        bar.addView(controls, controlsParams);

        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, -2);
        barParams.topMargin = dp(16);
        column.addView(bar, barParams);

        setBackgroundPhoto(null);
        setVisibility(GONE);
    }

    /** 로터리·방향키 사용 중이면 재생 버튼에 포커스를 둔다 (터치 모드에서는 아무 일도 하지 않음) */
    public void focusPrimaryControl() {
        playButton.requestFocus();
    }

    /** 현재 곡 제목과 데모 가사 표시 (첫 줄부터) */
    public void setSong(String songId, CharSequence title) {
        songTitleText.setText("♪  " + (title == null ? "제목 없음" : title));
        lines = DemoLyrics.linesFor(songId);
        showLine(0);
    }

    /** 재생 버튼 아이콘을 플레이어 상태에 맞춘다 */
    public void setPlaying(boolean playing) {
        playButton.setIcon(playing ? CarinIcon.Type.PAUSE : CarinIcon.Type.PLAY);
    }

    /** 재생 위치 표시. durationMs가 0 이하이면 길이를 모르는 상태 */
    public void setProgress(long positionMs, long durationMs) {
        elapsedText.setText(formatTime(positionMs));
        durationText.setText(durationMs > 0 ? formatTime(durationMs) : "--:--");
        progress.setProgress(durationMs > 0 ? positionMs / (float) durationMs : 0f);
    }

    /**
     * 배경 사진을 비동기로 읽어 표시한다. null이면 assets 기본 사진(없으면 노을 그라디언트).
     */
    public void setBackgroundPhoto(Uri uri) {
        setBackgroundPhoto(uri, "내 사진");
    }

    /**
     * 배경 사진을 표시하고, 성공하면 "배경: {label}"로 출처를 알려준다.
     *
     * @param label 사진 출처 (예: "추억 사진 · 우리의 강릉 여행")
     */
    public void setBackgroundPhoto(Uri uri, String label) {
        photoStateText.setText(uri == null ? "배경: 기본" : "배경: 사진 불러오는 중…");
        photoStateText.setTextColor(CarinTheme.MUTED);
        ImageLoader.load(backgroundImage, uri, ImageLoader.SLOT_KARAOKE, 1600, bitmap -> {
            if (uri == null) return;
            if (bitmap == null) {
                photoStateText.setText("배경: 사진을 열 수 없어 기본 배경 사용");
                photoStateText.setTextColor(CarinTheme.AMBER);
                // 사용자 사진이 깨졌으면 빈 화면 대신 기본 배경(assets → 그라디언트)을 다시 깐다
                ImageLoader.load(backgroundImage, null, ImageLoader.SLOT_KARAOKE, 1600, null);
            } else {
                photoStateText.setText("배경: " + label);
                photoStateText.setTextColor(CarinTheme.LIGHT_ROSE);
            }
        });
    }

    private void showLine(int index) {
        if (lines.length == 0) {
            previousLine.setText("");
            currentLine.setText("");
            nextLine.setText("");
            lineCounter.setText("");
            return;
        }
        lineIndex = Math.max(0, Math.min(lines.length - 1, index));
        previousLine.setText(lineIndex > 0 ? lines[lineIndex - 1] : " ");
        currentLine.setText(lines[lineIndex]);
        nextLine.setText(lineIndex + 1 < lines.length ? lines[lineIndex + 1] : " ");
        lineCounter.setText(String.format(Locale.ROOT, "%d / %d  ·  가사를 누르면 다음 줄, 왼쪽을 누르면 이전 줄",
                lineIndex + 1, lines.length));
    }

    private static String formatTime(long ms) {
        long totalSeconds = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private TextView lyricLine(Context context, float sp, int color, int weight) {
        TextView view = CarinTheme.text(context, "", sp, color, weight);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private LinearLayout.LayoutParams lineParams(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topDp);
        return params;
    }

    private LinearLayout.LayoutParams circleParams(int sizeDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        params.setMargins(dp(12), 0, dp(12), 0);
        return params;
    }

    private int dp(float value) {
        return CarinTheme.dp(getContext(), value);
    }
}
