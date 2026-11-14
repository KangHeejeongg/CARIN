package com.carin.noraebang.photo;

/** 새 사진 축소 크기 계산 (Android 비의존, JVM 단위 테스트 대상) */
public final class PhotoSizing {

    private PhotoSizing() {
    }

    /**
     * 긴 변이 maxSide 이상 남는 가장 큰 2의 거듭제곱 샘플 크기.
     * 예) 6000x4000, 1600 → 2 (3000x2000으로 디코딩 후 1600으로 정확히 축소)
     */
    public static int sampleSizeFor(int width, int height, int maxSide) {
        int longSide = Math.max(width, height);
        int sample = 1;
        while (longSide / (sample * 2) >= maxSide) {
            sample *= 2;
        }
        return sample;
    }

    /** 긴 변을 maxSide 이하로 맞춘 크기 {w, h}. 이미 작으면 그대로 (확대하지 않음) */
    public static int[] targetSize(int width, int height, int maxSide) {
        int longSide = Math.max(width, height);
        if (longSide <= maxSide) return new int[] {width, height};
        double scale = maxSide / (double) longSide;
        return new int[] {
                Math.max(1, (int) Math.round(width * scale)),
                Math.max(1, (int) Math.round(height * scale))
        };
    }
}
