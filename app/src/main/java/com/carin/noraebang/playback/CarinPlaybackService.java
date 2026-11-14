package com.carin.noraebang.playback;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;

import com.carin.noraebang.MainActivity;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

/**
 * CAR IN 재생 서비스. 앱 프로세스에서 ExoPlayer를 하나만 소유한다.
 *
 * - MainActivity, 차량 미디어 센터·위젯, 미디어 버튼은 모두 이 MediaLibrarySession을 통해 제어한다.
 *   (Manifest에 MediaLibraryService와 MediaBrowserService 액션을 함께 선언 — Pleos Music/Audio 가이드)
 * - 자동 재생 금지: 생성 시 곡 목록만 준비하고 playWhenReady=false. 재생은 명시적 재생 명령이나
 *   시스템의 재개 요청(onPlaybackResumption)에서만 시작한다.
 * - 오디오 포커스: Media3가 처리(일시 상실 → 일시정지, 재획득 → 재개). 완전히 잃으면 정지한다.
 * - 이어폰/출력 분리: 일시정지. 재생 종료: 다음 재개 위치를 첫 곡으로 저장.
 */
public final class CarinPlaybackService extends MediaLibraryService {

    private static final String TAG = "CARIN_PLAYBACK";
    private static final String PREFS = "carin_playback";
    private static final String KEY_LAST_MEDIA_ID = "last_media_id";
    private static final String KEY_LAST_POSITION = "last_position_ms";

    private MediaLibrarySession session;

    @Override
    public void onCreate() {
        super.onCreate();

        ExoPlayer player = new ExoPlayer.Builder(this)
                // handleAudioFocus=true: 포커스 요청·일시 상실 시 일시정지·재획득 시 재개를 Media3가 처리
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        player.addListener(playerListener);
        // 곡 목록만 준비하고 재생은 시작하지 않는다 (앱 연결만으로 자동 재생 금지)
        player.setMediaItems(CarinPlaylist.songs(this));
        player.setPlayWhenReady(false);
        player.prepare();

        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent sessionActivity = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        session = new MediaLibrarySession.Builder(this, player, new LibraryCallback())
                .setSessionActivity(sessionActivity)
                .build();
        Log.d(TAG, "재생 서비스 생성 · 곡 " + player.getMediaItemCount() + "개 준비 (자동 재생 없음)");
    }

    @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    /** 앱 작업이 제거되면 재생을 정지하고 서비스를 끝낸다 (Pleos: stopMedia → stopSelf → super) */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (session != null) {
            Player player = session.getPlayer();
            saveResumePoint(player);
            player.stop();
        }
        Log.d(TAG, "앱 작업 제거 → 재생 정지, 서비스 종료");
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            Player player = session.getPlayer();
            saveResumePoint(player);
            player.removeListener(playerListener);
            player.release();
            session.release();
            session = null;
        }
        Log.d(TAG, "재생 서비스 해제");
        super.onDestroy();
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (playWhenReady || session == null) return;
            Player player = session.getPlayer();
            saveResumePoint(player);
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                // 포커스를 완전히 잃음(다른 앱이 재생 시작): 정지해 세션이 계속 재생 중으로 남지 않게 한다
                Log.d(TAG, "오디오 포커스 상실(영구) → 정지");
                player.stop();
            } else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                Log.d(TAG, "오디오 출력 분리 → 일시정지");
            }
        }

        @Override
        public void onPlaybackSuppressionReasonChanged(int reason) {
            // 일시적 포커스 상실은 playWhenReady를 유지한 채 재생만 억제된다 (재획득 시 Media3가 재개)
            Log.d(TAG, reason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
                    ? "오디오 포커스 일시 상실 → 일시정지(재획득 시 재개)"
                    : "재생 억제 해제/변경: " + reason);
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_ENDED) {
                // 마지막 곡까지 끝나면 다음 재개는 첫 곡부터
                Log.d(TAG, "재생 종료 → 다음 재개 위치를 첫 곡으로");
                saveResumePoint(CarinPlaylist.songs(CarinPlaybackService.this).get(0).mediaId, 0);
            }
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            if (session != null) saveResumePoint(session.getPlayer());
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Log.e(TAG, "재생 오류: " + error.getErrorCodeName(), error);
        }
    };

    private void saveResumePoint(Player player) {
        MediaItem current = player.getCurrentMediaItem();
        if (current == null || player.getPlaybackState() == Player.STATE_ENDED) return;
        saveResumePoint(current.mediaId, Math.max(0, player.getCurrentPosition()));
    }

    private void saveResumePoint(String mediaId, long positionMs) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_MEDIA_ID, mediaId).putLong(KEY_LAST_POSITION, positionMs).apply();
    }

    /** 차량 미디어 시스템·MainActivity·미디어 버튼 요청 처리 */
    private final class LibraryCallback implements MediaLibrarySession.Callback {

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                MediaLibrarySession session, MediaSession.ControllerInfo browser, LibraryParams params) {
            Log.d(TAG, "탐색 루트 요청: " + browser.getPackageName());
            return Futures.immediateFuture(LibraryResult.ofItem(CarinPlaylist.root(), params));
        }

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
                MediaLibrarySession session, MediaSession.ControllerInfo browser, String mediaId) {
            MediaItem item = CarinPlaylist.find(CarinPlaybackService.this, mediaId);
            return Futures.immediateFuture(item != null
                    ? LibraryResult.ofItem(item, null)
                    : LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                MediaLibrarySession session, MediaSession.ControllerInfo browser, String parentId,
                int page, int pageSize, LibraryParams params) {
            Log.d(TAG, "탐색 하위 목록 요청: " + parentId + " from " + browser.getPackageName());
            List<MediaItem> children;
            if (CarinPlaylist.ROOT_ID.equals(parentId)) {
                children = ImmutableList.of(CarinPlaylist.playlistFolder());
            } else if (CarinPlaylist.PLAYLIST_ID.equals(parentId)) {
                children = CarinPlaylist.songs(CarinPlaybackService.this);
            } else {
                return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(children, params));
        }

        @Override
        public ListenableFuture<LibraryResult<Void>> onSearch(
                MediaLibrarySession session, MediaSession.ControllerInfo browser, String query,
                LibraryParams params) {
            int count = CarinPlaylist.search(CarinPlaybackService.this, query).size();
            session.notifySearchResultChanged(browser, query, count, params);
            return Futures.immediateFuture(LibraryResult.ofVoid());
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResult(
                MediaLibrarySession session, MediaSession.ControllerInfo browser, String query,
                int page, int pageSize, LibraryParams params) {
            return Futures.immediateFuture(LibraryResult.ofItemList(
                    CarinPlaylist.search(CarinPlaybackService.this, query), params));
        }

        /** 외부 컨트롤러가 mediaId만 보낸 항목을 실제 재생 가능한 항목(URI 포함)으로 바꾼다 */
        @Override
        public ListenableFuture<List<MediaItem>> onAddMediaItems(
                MediaSession mediaSession, MediaSession.ControllerInfo controller, List<MediaItem> mediaItems) {
            List<MediaItem> resolved = new ArrayList<>();
            for (MediaItem item : mediaItems) {
                int index = CarinPlaylist.indexOf(item.mediaId);
                if (index >= 0) resolved.add(CarinPlaylist.songs(CarinPlaybackService.this).get(index));
            }
            return Futures.immediateFuture(resolved);
        }

        /**
         * 차량에서 곡 하나(또는 플레이리스트)를 골라 재생할 때도 전체 플레이리스트를 유지하고 그 곡부터 재생한다.
         * 플레이리스트가 한 곡으로 바뀌면 추억 재생(mediaId로 곡 찾기)과 이전/다음 곡이 깨지기 때문이다.
         */
        @Override
        public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(
                MediaSession mediaSession, MediaSession.ControllerInfo controller, List<MediaItem> mediaItems,
                int startIndex, long startPositionMs) {
            List<MediaItem> all = CarinPlaylist.songs(CarinPlaybackService.this);
            int target = 0;
            if (!mediaItems.isEmpty()) {
                int safeIndex = startIndex == C.INDEX_UNSET ? 0 : Math.min(startIndex, mediaItems.size() - 1);
                int found = CarinPlaylist.indexOf(mediaItems.get(Math.max(0, safeIndex)).mediaId);
                if (found >= 0) target = found;
            }
            long position = startPositionMs == C.TIME_UNSET ? 0 : startPositionMs;
            return Futures.immediateFuture(new MediaSession.MediaItemsWithStartPosition(all, target, position));
        }

        /** 시스템 재개 요청(미디어 버튼 등): 마지막으로 듣던 곡·위치부터 */
        @Override
        public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(
                MediaSession mediaSession, MediaSession.ControllerInfo controller) {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            int index = Math.max(0, CarinPlaylist.indexOf(prefs.getString(KEY_LAST_MEDIA_ID, null)));
            long position = prefs.getLong(KEY_LAST_POSITION, 0);
            Log.d(TAG, "시스템 재개 요청 → " + index + "번째 곡 " + position + "ms");
            return Futures.immediateFuture(new MediaSession.MediaItemsWithStartPosition(
                    CarinPlaylist.songs(CarinPlaybackService.this), index, position));
        }
    }
}
