package com.carin.noraebang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.carin.noraebang.memory.PlaceMatcher;

import org.junit.Test;

public class PlaceMatcherTest {

    @Test
    public void normalizeStripsWhitespaceAndAdminSuffix() {
        assertEquals("강릉", PlaceMatcher.normalize("  강릉시 "));
        assertEquals("강릉", PlaceMatcher.normalize("강릉역"));
        assertEquals("부산", PlaceMatcher.normalize("부산광역시"));
        assertEquals("", PlaceMatcher.normalize("   "));
        assertEquals("", PlaceMatcher.normalize(null));
    }

    @Test
    public void matchesSameRegionByNameOrAddress() {
        assertTrue(PlaceMatcher.matches("강릉", "강릉역", ""));
        assertTrue(PlaceMatcher.matches("강릉", "경포해변", "강원특별자치도 강릉시 경포로 365"));
        assertTrue(PlaceMatcher.matches("강릉시", "강릉", ""));
        assertTrue(PlaceMatcher.matches("부산", "부산역", "부산광역시 동구 중앙대로 206"));
        assertTrue(PlaceMatcher.matches(" 부산 ", "해운대해수욕장", "부산 해운대구 우동"));
    }

    @Test
    public void doesNotMatchDifferentRegionOrSubstring() {
        assertFalse(PlaceMatcher.matches("강릉", "부산역", "부산광역시 동구"));
        assertFalse(PlaceMatcher.matches("부산", "강릉역", "강원특별자치도 강릉시"));
        // "산" 같은 짧은 키가 "부산"에 substring으로 걸리지 않아야 한다
        assertFalse(PlaceMatcher.matches("산", "부산역", ""));
        assertFalse(PlaceMatcher.matches("강릉", "", ""));
        assertFalse(PlaceMatcher.matches("", "강릉역", ""));
    }
}
