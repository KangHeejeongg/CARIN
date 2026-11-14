package com.carin.noraebang.memory;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 추억 기록을 SharedPreferences + JSON으로 저장하는 로컬 저장소.
 * Room 등 새 라이브러리를 도입하지 않고 앱 재실행 후에도 데이터를 유지한다.
 *
 * 데이터 보호 규칙 (원문 덮어쓰기 방지)
 * - 저장 원문의 배열 자체를 읽을 수 없으면 "쓰기 보호" 상태가 되어 add/delete/update/createDemo가
 *   원문을 바꾸지 않고 실패(false/null)한다. 호출 측은 {@link #isWriteProtected()}로 이유를 구분한다.
 * - 일부 레코드만 읽을 수 없으면 읽은 기록은 그대로 쓰고, 읽지 못한 레코드는 저장할 때 원래 값 그대로 함께 쓴다.
 * - 손상을 처음 발견하면 원문을 prefs("memories_backup")와 files/memory_backups/*.json에 백업한다.
 * - 정상 저장 직전의 원문은 prefs("memories_prev")에 한 세대 보관한다.
 * - 사진 파일은 현재 원문과 위 보관본 중 하나라도 참조하면 지우지 않는다 ({@link PhotoRetention}).
 */
public final class MemoryStore {

    private static final String TAG = "CARIN_MEMORY";
    private static final String PREFS = "carin_memory_route";
    private static final String KEY_MEMORIES = "memories";
    /** 저장 직전 원문 (한 세대) */
    private static final String KEY_PREVIOUS = "memories_prev";
    /** 손상을 처음 발견했을 때의 원문 (덮어쓰지 않음) */
    private static final String KEY_BACKUP = "memories_backup";
    /** 이미 백업한 손상 원문의 해시 (같은 원문을 반복 백업하지 않기 위함) */
    private static final String KEY_BACKUP_HASH = "memories_backup_hash";
    private static final String BACKUP_DIR = "memory_backups";
    /** 교체되어 지울 후보가 된 사진 URI (보관본이 더 이상 참조하지 않을 때 삭제) */
    private static final String KEY_PENDING_RELEASE = "photos_pending_release";

    public static final String DEMO_PLACE = "강릉";
    public static final String DEMO_TITLE = "우리의 강릉 여행 (데모)";
    public static final String DEMO_DATE = "2025-08-15";

    private final Context appContext;
    private final SharedPreferences prefs;

    /** 목록을 바꾸는 저장 작업. 바꿀 대상이 없으면 false */
    private interface Change {
        boolean apply(List<Memory> memories);
    }

    public MemoryStore(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 저장된 모든 추억(읽을 수 있는 것만)을 여행 날짜 최신순으로 반환 */
    public List<Memory> getAll() {
        List<Memory> result = new ArrayList<>(load().memories);
        sortRecentFirst(result);
        return result;
    }

    /**
     * 저장 원문의 배열 자체가 손상되어 변경이 차단된 상태인지.
     * add/delete/update가 false, createDemo가 null을 반환했을 때 원인을 구분하는 데 쓴다.
     */
    public boolean isWriteProtected() {
        return load().topLevelCorrupt;
    }

    /** 읽지 못해 화면에 표시하지 않지만 원문으로 보존 중인 레코드 수 */
    public int unreadableCount() {
        return load().unreadable.size();
    }

    /**
     * 현재 원문 또는 복원용 보관본(memories_prev, memories_backup, 백업 파일) 중
     * 하나라도 이 사진 파일을 참조하는지. 읽지 못한 레코드도 원문 문자열 기준으로 포함된다.
     */
    public boolean isPhotoReferenced(String photoUri) {
        return PhotoRetention.isReferenced(photoUri, snapshotTexts());
    }

    /**
     * 사진 파일 참조 검사에 쓰는 모든 원문: 현재, 직전 한 세대, 손상 백업(prefs·파일).
     * 이 중 하나로 복원했을 때 사진이 사라지지 않도록 보관본 전체를 본다.
     */
    public List<String> snapshotTexts() {
        List<String> texts = new ArrayList<>();
        addIfPresent(texts, prefs.getString(KEY_MEMORIES, null));
        addIfPresent(texts, prefs.getString(KEY_PREVIOUS, null));
        addIfPresent(texts, prefs.getString(KEY_BACKUP, null));
        File[] files = new File(appContext.getFilesDir(), BACKUP_DIR).listFiles();
        if (files != null) {
            for (File file : files) {
                addIfPresent(texts, readText(file));
            }
        }
        return texts;
    }

    /** 교체·해제되어 지울 후보가 된 사진을 삭제 대기 목록에 넣는다 (실제 삭제는 참조가 모두 사라진 뒤) */
    public void deferPhotoRelease(String photoUri) {
        if (photoUri == null || photoUri.isEmpty()) return;
        Set<String> pending = new HashSet<>(prefs.getStringSet(KEY_PENDING_RELEASE, new HashSet<>()));
        if (pending.add(photoUri)) {
            prefs.edit().putStringSet(KEY_PENDING_RELEASE, pending).commit();
        }
    }

    /** 삭제 대기 중인 사진 URI 목록 (복사본) */
    public Set<String> pendingPhotoReleases() {
        return new HashSet<>(prefs.getStringSet(KEY_PENDING_RELEASE, new HashSet<>()));
    }

    /** 삭제했거나 더 관리할 필요가 없는 사진을 대기 목록에서 뺀다 */
    public void forgetPhotoReleases(Collection<String> photoUris) {
        if (photoUris.isEmpty()) return;
        Set<String> pending = pendingPhotoReleases();
        if (pending.removeAll(photoUris)) {
            prefs.edit().putStringSet(KEY_PENDING_RELEASE, pending).commit();
        }
    }

    private static void addIfPresent(List<String> texts, String text) {
        if (text != null && !text.isEmpty()) texts.add(text);
    }

    private static String readText(File file) {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "백업 파일 읽기 실패: " + file.getName(), e);
            return null;
        }
    }

    /** 새 추억 저장. 저장 성공 시 true (쓰기 보호 상태면 false) */
    public boolean add(Memory memory) {
        return write(list -> list.add(memory));
    }

    /** ID로 삭제. 실제로 삭제된 경우 true (쓰기 보호 상태면 false) */
    public boolean delete(String id) {
        return write(list -> {
            boolean removed = false;
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).id.equals(id)) {
                    list.remove(i);
                    removed = true;
                }
            }
            return removed;
        });
    }

    /** 같은 ID의 기록을 교체 저장. 대상이 없거나 쓰기 보호 상태면 false */
    public boolean update(Memory updated) {
        return write(list -> {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id.equals(updated.id)) {
                    list.set(i, updated);
                    return true;
                }
            }
            return false;
        });
    }

    /** ID로 1건 조회. 없으면 null */
    public Memory findById(String id) {
        if (id == null) return null;
        for (Memory memory : getAll()) {
            if (memory.id.equals(id)) return memory;
        }
        return null;
    }

    /** 사용자 입력으로 새 추억 생성 */
    public Memory create(String place, String title, String travelDate, String songId) {
        return new Memory(
                UUID.randomUUID().toString(),
                place.trim(),
                title.trim(),
                travelDate,
                songId,
                System.currentTimeMillis(),
                false
        );
    }

    /** 데모 기록이 이미 존재하는지 확인 (중복 생성 방지) */
    public boolean hasDemo() {
        for (Memory memory : getAll()) {
            if (memory.demo) return true;
        }
        return false;
    }

    /**
     * 강릉 데모 기록 1건을 생성한다.
     * 재생 가능한 곡이 없거나 이미 데모가 있으면 null을 반환한다.
     */
    public Memory createDemo() {
        if (hasDemo()) return null;
        List<SongCatalog.Song> songs = SongCatalog.all();
        if (songs.isEmpty()) return null;
        Memory demo = new Memory(
                "demo-gangneung",
                DEMO_PLACE,
                DEMO_TITLE,
                DEMO_DATE,
                songs.get(0).id,
                System.currentTimeMillis(),
                true
        );
        return add(demo) ? demo : null;
    }

    /** 목적지 이름/주소와 일치하는 추억을 최신순으로 반환 */
    public List<Memory> findMatching(String destinationName, String destinationAddress) {
        List<Memory> matched = new ArrayList<>();
        for (Memory memory : getAll()) {
            if (PlaceMatcher.matches(memory.place, destinationName, destinationAddress)) {
                matched.add(memory);
            }
        }
        return matched;
    }

    /** 저장 원문을 읽는다. 손상이 있으면 (처음 한 번) 원문을 백업한다 */
    private MemoryCodec.DecodeResult load() {
        String raw = prefs.getString(KEY_MEMORIES, null);
        MemoryCodec.DecodeResult result = MemoryCodec.decode(raw);
        if (result.hasProblem()) {
            Log.w(TAG, "저장 데이터 손상 감지: topLevelCorrupt=" + result.topLevelCorrupt
                    + " unreadable=" + result.unreadable.size()
                    + " readable=" + result.memories.size());
            backupOnce(raw);
        }
        return result;
    }

    /**
     * 현재 원문을 읽고, 변경을 적용해 저장한다.
     * 배열 전체가 손상된 경우에는 원문을 건드리지 않고 false를 반환한다.
     */
    private boolean write(Change change) {
        MemoryCodec.DecodeResult state = load();
        if (!state.canWrite()) {
            Log.w(TAG, "쓰기 차단: 저장 원문을 읽을 수 없어 기존 데이터를 보존합니다");
            return false;
        }

        List<Memory> memories = new ArrayList<>(state.memories);
        sortRecentFirst(memories);
        if (!change.apply(memories)) return false;
        sortRecentFirst(memories);

        String encoded = MemoryCodec.encode(memories, state.unreadable);
        if (encoded == null) {
            Log.e(TAG, "추억 데이터 직렬화 실패 — 저장하지 않음");
            return false;
        }

        SharedPreferences.Editor editor = prefs.edit();
        if (state.originalRaw != null) {
            editor.putString(KEY_PREVIOUS, state.originalRaw);
        }
        editor.putString(KEY_MEMORIES, encoded);
        boolean saved = editor.commit();
        if (!saved) Log.e(TAG, "추억 데이터 저장(commit) 실패 — 기존 원문 유지");
        return saved;
    }

    /** 손상된 원문을 같은 내용에 대해 한 번만 prefs와 파일로 백업한다 */
    private void backupOnce(String raw) {
        if (raw == null) return;
        String hash = Integer.toHexString(raw.hashCode()) + ":" + raw.length();
        if (hash.equals(prefs.getString(KEY_BACKUP_HASH, null))) return;

        SharedPreferences.Editor editor = prefs.edit();
        if (!prefs.contains(KEY_BACKUP)) {
            editor.putString(KEY_BACKUP, raw);
        }
        editor.putString(KEY_BACKUP_HASH, hash);
        if (!editor.commit()) {
            Log.e(TAG, "손상 원문 prefs 백업 실패");
        }

        File dir = new File(appContext.getFilesDir(), BACKUP_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "백업 폴더 생성 실패: " + dir);
            return;
        }
        File file = new File(dir, "memory_backup_" + System.currentTimeMillis() + ".json");
        try (Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            out.write(raw);
            Log.w(TAG, "손상 원문 백업: " + file.getName());
        } catch (Exception e) {
            Log.e(TAG, "손상 원문 파일 백업 실패", e);
        }
    }

    private static void sortRecentFirst(List<Memory> list) {
        Collections.sort(list, new Comparator<Memory>() {
            @Override
            public int compare(Memory a, Memory b) {
                int byDate = b.travelDate.compareTo(a.travelDate);
                if (byDate != 0) return byDate;
                return Long.compare(b.createdAt, a.createdAt);
            }
        });
    }
}
