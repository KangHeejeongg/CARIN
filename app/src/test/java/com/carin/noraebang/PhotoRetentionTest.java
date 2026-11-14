package com.carin.noraebang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.carin.noraebang.memory.Memory;
import com.carin.noraebang.memory.MemoryCodec;
import com.carin.noraebang.memory.PhotoRetention;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 사진 교체 후 복원용 보관본(memories_prev, 손상 백업)이 참조하는 사진이 지워지지 않는지,
 * 그리고 직전 원문으로 복원하면 추억과 사진이 함께 돌아오는지 검증한다.
 *
 * MemoryStore의 저장 흐름을 그대로 흉내 낸다:
 *   저장 시 prev ← 현재 원문, 현재 ← 새 원문 → 교체된 사진은 삭제 대기 → 대기 목록 정리
 */
public class PhotoRetentionTest {

    private static final String DIR = "file:///data/user/10/com.carin.noraebang/files/photos/";
    private static final String PHOTO_A = DIR + "aaaa-1111.jpg";
    private static final String PHOTO_B = DIR + "bbbb-2222.jpg";
    private static final String PHOTO_C = DIR + "cccc-3333.jpg";

    /** 저장소 상태 흉내: 현재 원문, 직전 원문, 손상 백업, 삭제 대기, 실제로 존재하는 파일 */
    private String current;
    private String previous;
    private String corruptionBackup;
    private final Set<String> pending = new HashSet<>();
    private final Set<String> files = new HashSet<>();

    private static Memory memoryWith(String photoUri) {
        return new Memory("m1", "강릉", "강릉 여행", "2025-08-15", "carin_song_01", 1L, false, photoUri);
    }

    private static String raw(Memory... memories) {
        return MemoryCodec.encode(Arrays.asList(memories), Collections.emptyList());
    }

    private List<String> snapshots() {
        List<String> list = new ArrayList<>();
        for (String text : new String[] {current, previous, corruptionBackup}) {
            if (text != null) list.add(text);
        }
        return list;
    }

    /** MemoryStore.write + MainActivity.releaseReplacedPhoto/sweepReleasedPhotos 흐름 */
    private void replacePhoto(String oldPhoto, String newPhoto) {
        files.add(newPhoto);
        previous = current;
        current = raw(memoryWith(newPhoto));
        pending.add(oldPhoto);
        sweep(Collections.emptyList());
    }

    private void sweep(List<String> otherInUse) {
        List<String> releasable = PhotoRetention.releasable(pending, snapshots(), otherInUse);
        files.removeAll(releasable);
        pending.removeAll(releasable);
    }

    private void start(String photo) {
        files.add(photo);
        current = raw(memoryWith(photo));
    }

    @Test
    public void replacedPhoto_isKeptWhilePreviousSnapshotReferencesIt() {
        start(PHOTO_A);
        replacePhoto(PHOTO_A, PHOTO_B);

        assertTrue("교체 직후 memories_prev가 A를 참조하므로 A 파일은 남아야 한다", files.contains(PHOTO_A));
        assertTrue(pending.contains(PHOTO_A));
        assertTrue(files.contains(PHOTO_B));
    }

    @Test
    public void restoringPreviousSnapshot_bringsBackMemoryAndPhotoTogether() {
        start(PHOTO_A);
        replacePhoto(PHOTO_A, PHOTO_B);

        // 직전 원문으로 복원 (memories ← memories_prev)
        current = previous;
        MemoryCodec.DecodeResult restored = MemoryCodec.decode(current);

        assertEquals(1, restored.memories.size());
        Memory memory = restored.memories.get(0);
        assertEquals("강릉 여행", memory.title);
        assertEquals(PHOTO_A, memory.photoUri);
        assertTrue("복원한 추억이 가리키는 사진 파일이 존재해야 한다", files.contains(memory.photoUri));
    }

    @Test
    public void photoIsReleasedOnlyAfterNoSnapshotReferencesIt() {
        start(PHOTO_A);
        replacePhoto(PHOTO_A, PHOTO_B);
        replacePhoto(PHOTO_B, PHOTO_C);   // prev가 B로 바뀌면서 A는 어디에서도 참조되지 않음

        assertFalse("보관본에서 빠진 A는 이제 지워진다", files.contains(PHOTO_A));
        assertFalse(pending.contains(PHOTO_A));
        assertTrue("새 prev가 참조하는 B는 남는다", files.contains(PHOTO_B));
        assertTrue(pending.contains(PHOTO_B));
        assertTrue(files.contains(PHOTO_C));

        // 다시 직전 원문으로 복원해도 사진이 함께 돌아온다
        Memory restored = MemoryCodec.decode(previous).memories.get(0);
        assertTrue(files.contains(restored.photoUri));
    }

    @Test
    public void corruptionBackup_keepsItsPhotoAcrossManyWrites() {
        start(PHOTO_A);
        corruptionBackup = current;           // 손상 발견 시점에 A를 참조하던 원문 백업
        replacePhoto(PHOTO_A, PHOTO_B);
        replacePhoto(PHOTO_B, PHOTO_C);

        assertTrue("손상 백업이 참조하는 A는 계속 남아야 한다", files.contains(PHOTO_A));
        assertFalse("손상 백업도 prev도 참조하지 않는 사진은 없다(B는 prev가 참조)", pending.isEmpty());
    }

    @Test
    public void photoInUseOutsideStore_isNotReleased() {
        pending.add(PHOTO_A);
        files.add(PHOTO_A);
        current = raw(memoryWith(PHOTO_B));
        sweep(Collections.singletonList(PHOTO_A));   // 노래방 배경으로 사용 중

        assertTrue(files.contains(PHOTO_A));
    }

    @Test
    public void unreadableRecordAndEscapedSlashes_stillCountAsReferences() {
        // 읽지 못한 레코드가 A를 참조 + Android JSON처럼 '/'가 '\/'로 이스케이프된 원문
        String escaped = "[{\"id\":\"broken\",\"photoUri\":\"file:\\/\\/\\/data\\/photos\\/aaaa-1111.jpg\"}]";
        assertTrue(PhotoRetention.isReferenced(PHOTO_A, Collections.singletonList(escaped)));
        assertFalse(PhotoRetention.isReferenced(PHOTO_C, Collections.singletonList(escaped)));
        assertTrue(PhotoRetention.releasable(Collections.singletonList(PHOTO_A),
                Collections.singletonList(escaped), Collections.emptyList()).isEmpty());
    }
}
