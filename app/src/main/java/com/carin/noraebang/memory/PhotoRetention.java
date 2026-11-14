package com.carin.noraebang.memory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 사진 파일을 지워도 되는지 판단하는 규칙 (Android 비의존, JVM 테스트 대상).
 *
 * 추억 저장소는 현재 원문 외에 복원용 보관본을 가진다.
 * - memories_prev: 마지막 저장 직전 원문 (한 세대)
 * - memories_backup, files/memory_backups/*.json: 손상을 처음 발견했을 때의 원문
 * 사진 교체 직후 이전 사진은 memories_prev가 참조하므로, 보관본 중 하나라도 파일 이름을 담고 있으면
 * 지우지 않는다. 교체된 사진은 "삭제 대기"로 두었다가, 이후 저장으로 보관본이 바뀌어 더 이상
 * 아무도 참조하지 않을 때 지운다.
 */
public final class PhotoRetention {

    private PhotoRetention() {
    }

    /**
     * 사진 URI의 파일 이름 (예: file:///.../photos/abc.jpg → abc.jpg).
     * JSON 원문은 '/'를 '\/'로 쓸 수 있으므로 경로가 아닌 파일 이름으로 비교한다.
     */
    public static String fileName(String photoUri) {
        if (photoUri == null) return "";
        String trimmed = photoUri.trim();
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }

    /** 보관본(원문 문자열) 중 하나라도 이 사진을 참조하는지 */
    public static boolean isReferenced(String photoUri, Collection<String> snapshots) {
        String name = fileName(photoUri);
        if (name.isEmpty()) return false;
        for (String snapshot : snapshots) {
            if (snapshot != null && snapshot.contains(name)) return true;
        }
        return false;
    }

    /**
     * 삭제 대기 사진 중 지금 지워도 되는 것.
     *
     * @param pending      교체·해제되어 삭제 대기 중인 사진 URI
     * @param snapshots    현재 원문과 모든 복원용 보관본
     * @param otherInUse   추억 저장소 밖에서 쓰는 사진 URI (노래방 배경 등)
     */
    public static List<String> releasable(Collection<String> pending, Collection<String> snapshots,
                                          Collection<String> otherInUse) {
        List<String> result = new ArrayList<>();
        for (String uri : pending) {
            if (isReferenced(uri, snapshots)) continue;
            if (isReferenced(uri, otherInUse)) continue;
            result.add(uri);
        }
        return result;
    }
}
