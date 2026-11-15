package com.carin.noraebang.playback;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import com.carin.noraebang.memory.SongCatalog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 재생 서비스와 차량 미디어 시스템에 제공하는 곡·탐색 구조.
 *
 * 탐색 구조 (Pleos 가이드: 콘텐츠 종류별 구조, Music 하위 분류)
 *   CAR IN (root, browsable)
 *    └─ 카인 플레이리스트 (browsable, playlist)
 *        └─ 곡 (playable, mediaId = SongCatalog.Song.id)
 *
 * 곡 식별은 SongCatalog.Song.id = MediaItem.mediaId = Memory.songId 관계를 그대로 유지한다.
 */
public final class CarinPlaylist {

    public static final String ROOT_ID = "carin_root";
    public static final String PLAYLIST_ID = "carin_playlist";
    public static final String ARTIST = "CAR IN";
    public static final String PLAYLIST_TITLE = "카인 플레이리스트";

    private CarinPlaylist() {
    }

    /** 탐색 루트 */
    public static MediaItem root() {
        return folder(ROOT_ID, "CAR IN", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED);
    }

    /** 카인 플레이리스트 폴더 */
    public static MediaItem playlistFolder() {
        return folder(PLAYLIST_ID, PLAYLIST_TITLE, MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS);
    }

    /** 재생 가능한 전체 곡 (SongCatalog 순서) */
    public static List<MediaItem> songs(Context context) {
        List<MediaItem> items = new ArrayList<>();
        List<SongCatalog.Song> catalog = SongCatalog.all();
        for (int i = 0; i < catalog.size(); i++) {
            items.add(song(context, catalog.get(i), i + 1, catalog.size()));
        }
        return items;
    }

    /** mediaId에 해당하는 곡의 플레이리스트 위치. 없으면 -1 */
    public static int indexOf(String mediaId) {
        List<SongCatalog.Song> catalog = SongCatalog.all();
        for (int i = 0; i < catalog.size(); i++) {
            if (catalog.get(i).id.equals(mediaId)) return i;
        }
        return -1;
    }

    /** mediaId로 탐색 항목 조회 (루트·폴더·곡). 없으면 null */
    public static MediaItem find(Context context, String mediaId) {
        if (ROOT_ID.equals(mediaId)) return root();
        if (PLAYLIST_ID.equals(mediaId)) return playlistFolder();
        int index = indexOf(mediaId);
        return index < 0 ? null : songs(context).get(index);
    }

    /** 곡 제목·플레이리스트 이름 검색 (음성 검색 연동용). 빈 검색어면 전체 */
    public static List<MediaItem> search(Context context, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<MediaItem> result = new ArrayList<>();
        for (MediaItem item : songs(context)) {
            String title = String.valueOf(item.mediaMetadata.title).toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || title.contains(needle)
                    || PLAYLIST_TITLE.contains(needle) || "car in".contains(needle)) {
                result.add(item);
            }
        }
        return result;
    }

    /** 곡 커버 바이트 캐시 (assets에서 한 번만 읽는다) */
    private static final Map<String, byte[]> COVER_CACHE = new HashMap<>();

    /**
     * 곡 커버 JPEG 바이트를 읽는다. 없으면 null (차량 미디어 화면은 기본 아이콘을 쓴다).
     *
     * @param context 아무 Context
     * @param song    대상 곡
     * @return JPEG 바이트 또는 null
     */
    private static synchronized byte[] coverBytes(Context context, SongCatalog.Song song) {
        if (COVER_CACHE.containsKey(song.id)) return COVER_CACHE.get(song.id);
        byte[] bytes = null;
        try (InputStream in = context.getAssets().open(song.coverAsset);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            bytes = out.toByteArray();
        } catch (IOException e) {
            Log.w("CARIN_PLAYBACK", "커버 없음: " + song.coverAsset);
        }
        COVER_CACHE.put(song.id, bytes);
        return bytes;
    }

    private static MediaItem song(Context context, SongCatalog.Song song, int track, int total) {
        Uri uri = Uri.parse("android.resource://" + context.getPackageName() + "/" + song.rawResId);
        MediaMetadata.Builder metadata = new MediaMetadata.Builder();
        byte[] cover = coverBytes(context, song);
        if (cover != null) metadata.setArtworkData(cover, MediaMetadata.PICTURE_TYPE_FRONT_COVER);
        return new MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(uri)
                .setMediaMetadata(metadata
                        .setTitle(song.title)
                        .setDisplayTitle(song.title)
                        // 차량 목록·위젯의 두 번째 줄(MediaDescription subtitle)에 표시
                        .setSubtitle(ARTIST + " · " + PLAYLIST_TITLE)
                        .setArtist(ARTIST)
                        .setAlbumTitle(PLAYLIST_TITLE)
                        .setTrackNumber(track)
                        .setTotalTrackCount(total)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build())
                .build();
    }

    private static MediaItem folder(String id, String title, int mediaType) {
        return new MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(mediaType)
                        .build())
                .build();
    }
}
