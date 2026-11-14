package com.carin.noraebang;

import android.app.Activity;
import android.content.ComponentName;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import com.carin.noraebang.playback.CarinPlaybackService;

import java.util.List;

/**
 * debug 빌드 전용 점검 화면 (release 미포함).
 * 차량 미디어 시스템과 같은 방식(플랫폼 MediaBrowser = MediaBrowserService 프로토콜)으로
 * CarinPlaybackService에 연결해 탐색 구조와 mediaId 재생을 확인한다.
 *
 * adb shell am start --user 10 -n com.carin.noraebang/.MediaBrowseProbeActivity
 * adb logcat -s CARIN_BROWSE_PROBE
 */
public class MediaBrowseProbeActivity extends Activity {

    private static final String TAG = "CARIN_BROWSE_PROBE";
    private MediaBrowser browser;
    private TextView output;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        output = new TextView(this);
        output.setTextSize(18);
        output.setPadding(40, 40, 40, 40);
        setContentView(output);

        browser = new MediaBrowser(this, new ComponentName(this, CarinPlaybackService.class),
                new MediaBrowser.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        String root = browser.getRoot();
                        log("연결됨 · root=" + root);
                        browse(root, 0);
                    }

                    @Override
                    public void onConnectionFailed() {
                        log("연결 실패");
                    }
                }, null);
        browser.connect();
    }

    private void browse(String parentId, int depth) {
        browser.subscribe(parentId, new MediaBrowser.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(String id, List<MediaBrowser.MediaItem> children) {
                for (MediaBrowser.MediaItem child : children) {
                    log("  ".repeat(depth + 1) + child.getMediaId() + " | " + child.getDescription().getTitle()
                            + " | browsable=" + child.isBrowsable() + " playable=" + child.isPlayable()
                            + " | subtitle=" + child.getDescription().getSubtitle());
                    if (child.isBrowsable()) browse(child.getMediaId(), depth + 1);
                }
                if (depth == 1) playSecondSongById();
            }

            @Override
            public void onError(String id) {
                log("하위 목록 오류: " + id);
            }
        });
    }

    /** 차량에서 곡을 고르는 것과 같은 경로: playFromMediaId → 전체 목록 유지 여부 확인 */
    private void playSecondSongById() {
        MediaController controller = new MediaController(this, browser.getSessionToken());
        log("playFromMediaId(carin_song_02)");
        controller.getTransportControls().playFromMediaId("carin_song_02", null);
        handler.postDelayed(() -> {
            PlaybackState state = controller.getPlaybackState();
            List<android.media.session.MediaSession.QueueItem> queue = controller.getQueue();
            log("재생 상태=" + (state == null ? "null" : state.getState())
                    + " · 현재 곡=" + (controller.getMetadata() == null ? "null"
                    : controller.getMetadata().getDescription().getTitle())
                    + " · 큐 길이=" + (queue == null ? "null" : queue.size()));
            controller.getTransportControls().pause();
            log("pause 전송 · 점검 끝");
        }, 2500);
    }

    private void log(String message) {
        Log.d(TAG, message);
        output.append(message + "\n");
    }

    @Override
    protected void onDestroy() {
        if (browser != null) browser.disconnect();
        super.onDestroy();
    }
}
