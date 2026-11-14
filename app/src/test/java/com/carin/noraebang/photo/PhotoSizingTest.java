package com.carin.noraebang.photo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 새 사진의 축소 크기 계산 검증 (Android 비의존 부분) */
public class PhotoSizingTest {

    @Test
    public void sampleSize_keepsLongSideAtLeastMax() {
        assertEquals(1, PhotoSizing.sampleSizeFor(1600, 900, 1600));
        assertEquals(1, PhotoSizing.sampleSizeFor(3199, 2000, 1600));
        assertEquals(2, PhotoSizing.sampleSizeFor(3200, 2000, 1600));
        assertEquals(2, PhotoSizing.sampleSizeFor(6000, 4000, 1600));   // 3000x2000으로 디코딩
        assertEquals(4, PhotoSizing.sampleSizeFor(4000, 8000, 1600));   // 세로 사진도 긴 변 기준
        assertEquals(1, PhotoSizing.sampleSizeFor(800, 600, 1600));
    }

    @Test
    public void targetSize_fitsLongSideAndKeepsRatio() {
        assertArrayEquals(new int[] {1600, 1067}, PhotoSizing.targetSize(3000, 2000, 1600));
        assertArrayEquals(new int[] {900, 1600}, PhotoSizing.targetSize(1800, 3200, 1600));
        assertArrayEquals(new int[] {800, 600}, PhotoSizing.targetSize(800, 600, 1600));   // 작은 사진은 확대하지 않음
        assertArrayEquals(new int[] {1600, 1}, PhotoSizing.targetSize(20000, 5, 1600));   // 극단 비율도 최소 1px
    }
}
