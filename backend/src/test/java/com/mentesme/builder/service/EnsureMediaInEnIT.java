package com.mentesme.builder.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused test for ensureMediaInEn() — the single compiler for EN media interleaving.
 *
 * No Spring context. No database. Tests the method directly via reflection
 * (it is private static in LearningJourneyIntegrationService).
 *
 * Verifies the architecture contract:
 * - Media tags from NL are copied into EN at correct positions
 * - EN text segments are substituted for NL text positions
 * - Stale media in EN is stripped before reconstruction
 * - Edge cases: fewer EN segments, blank EN, no media in NL
 */
class EnsureMediaInEnIT {

    private static final Method ENSURE_MEDIA;

    static {
        try {
            ENSURE_MEDIA = LearningJourneyIntegrationService.class
                    .getDeclaredMethod("ensureMediaInEn", String.class, String.class);
            ENSURE_MEDIA.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("ensureMediaInEn method not found", e);
        }
    }

    private static String ensureMediaInEn(String enText, String nlText) throws Exception {
        return (String) ENSURE_MEDIA.invoke(null, enText, nlText);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: full interleave — text + img + text + video + text
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void interleaveMediaIntoEn() throws Exception {
        String nl = "<p>A</p>\n<img src=\"x.jpg\" style=\"max-width:100%;height:auto;\" />\n<p>B</p>\n<video src=\"v.mp4\" controls style=\"max-width:100%;\"></video>\n<p>C</p>";
        String en = "<p>A-en</p>\n<p>B-en</p>\n<p>C-en</p>";

        String result = ensureMediaInEn(en, nl);

        // Correct order: A-en, img, B-en, video, C-en
        String[] parts = result.split("\n");
        assertEquals(5, parts.length, "5 parts: 3 text + 1 img + 1 video");
        assertTrue(parts[0].contains("A-en"), "Part 0 = A-en");
        assertTrue(parts[1].contains("<img src=\"x.jpg\""), "Part 1 = img from NL");
        assertTrue(parts[2].contains("B-en"), "Part 2 = B-en");
        assertTrue(parts[3].contains("<video src=\"v.mp4\""), "Part 3 = video from NL");
        assertTrue(parts[4].contains("C-en"), "Part 4 = C-en");

        // No duplicate media
        assertEquals(1, countOccurrences(result, "<img "), "Exactly 1 img tag");
        assertEquals(1, countOccurrences(result, "<video "), "Exactly 1 video tag");

        // No NL text leaking
        assertFalse(result.contains("<p>A</p>"), "NL text A must not appear");
        assertFalse(result.contains("<p>B</p>"), "NL text B must not appear");
        assertFalse(result.contains("<p>C</p>"), "NL text C must not appear");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: no media in NL → EN passes through unchanged
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void noMediaInNlPassesEnThrough() throws Exception {
        String nl = "<p>Tekst NL</p>";
        String en = "<p>Text EN</p>";

        String result = ensureMediaInEn(en, nl);
        assertEquals(en, result, "No media in NL → EN unchanged");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: null inputs
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void nullEnReturnsNull() throws Exception {
        assertNull(ensureMediaInEn(null, "<p>NL</p>\n<img src=\"x.jpg\" />"));
    }

    @Test
    void nullNlReturnsEnUnchanged() throws Exception {
        String en = "<p>EN text</p>";
        assertEquals(en, ensureMediaInEn(en, null));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: fewer EN segments than NL text positions (fail-safe)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void fewerEnSegmentsThanNlTextPositions() throws Exception {
        String nl = "<p>A</p>\n<img src=\"x.jpg\" />\n<p>B</p>\n<p>C</p>";
        String en = "<p>A-en</p>";  // only 1 EN segment, NL has 3 text positions

        String result = ensureMediaInEn(en, nl);

        String[] parts = result.split("\n", -1);  // keep trailing empty
        // Part 0: A-en, Part 1: img, Part 2: empty (fail-safe), Part 3: empty (fail-safe)
        assertTrue(parts[0].contains("A-en"), "Part 0 = A-en");
        assertTrue(parts[1].contains("<img "), "Part 1 = img preserved");
        assertEquals("", parts[2], "Part 2 = empty (fail-safe)");
        assertEquals("", parts[3], "Part 3 = empty (fail-safe)");

        // Media preserved
        assertTrue(result.contains("<img src=\"x.jpg\""), "img preserved");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: more EN segments than NL text positions (appended)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void moreEnSegmentsThanNlTextPositions() throws Exception {
        String nl = "<p>A</p>\n<img src=\"x.jpg\" />\n<p>B</p>";
        String en = "<p>A-en</p>\n<p>B-en</p>\n<p>C-extra</p>";  // 3 EN segments, NL has 2 text

        String result = ensureMediaInEn(en, nl);

        String[] parts = result.split("\n");
        assertEquals(4, parts.length, "4 parts: A-en, img, B-en, C-extra");
        assertTrue(parts[0].contains("A-en"));
        assertTrue(parts[1].contains("<img "));
        assertTrue(parts[2].contains("B-en"));
        assertTrue(parts[3].contains("C-extra"), "Extra EN segment appended");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: stale media in EN is stripped before reconstruction
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void stripsStaleMediaFromEn() throws Exception {
        String nl = "<p>A</p>\n<img src=\"correct.jpg\" />\n<p>B</p>";
        String en = "<p>A-en</p><img src=\"wrong.jpg\" />\n<p>B-en</p>";

        String result = ensureMediaInEn(en, nl);

        assertFalse(result.contains("wrong.jpg"), "Stale img stripped");
        assertTrue(result.contains("correct.jpg"), "Correct img from NL");
        assertEquals(1, countOccurrences(result, "<img "), "Exactly 1 img");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: stale video in EN is stripped
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void stripsStaleVideoFromEn() throws Exception {
        String nl = "<p>A</p>\n<video src=\"correct.mp4\" controls></video>\n<p>B</p>";
        String en = "<p>A-en</p>\n<video src=\"wrong.mp4\" controls></video>\n<p>B-en</p>";

        String result = ensureMediaInEn(en, nl);

        assertFalse(result.contains("wrong.mp4"), "Stale video stripped");
        assertTrue(result.contains("correct.mp4"), "Correct video from NL");
        assertEquals(1, countOccurrences(result, "<video "), "Exactly 1 video");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: multiple images preserve order
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void multipleImagesPreserveOrder() throws Exception {
        String nl = "<p>A</p>\n<img src=\"first.jpg\" />\n<p>B</p>\n<img src=\"second.jpg\" />\n<p>C</p>";
        String en = "<p>A-en</p>\n<p>B-en</p>\n<p>C-en</p>";

        String result = ensureMediaInEn(en, nl);

        String[] parts = result.split("\n");
        assertEquals(5, parts.length);
        assertTrue(parts[1].contains("first.jpg"), "First img at position 1");
        assertTrue(parts[3].contains("second.jpg"), "Second img at position 3");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: EN is only whitespace → returned as-is (no media in NL case)
    //       or stripped to empty then fail-safe fills with empty strings
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void blankEnWithMediaInNl() throws Exception {
        String nl = "<p>A</p>\n<img src=\"x.jpg\" />\n<p>B</p>";
        String en = "   ";  // blank

        String result = ensureMediaInEn(en, nl);

        // After stripping media (none) and trimming, cleanEn = ""
        // split("") = [""] → 1 empty segment
        // NL has 3 parts: text, img, text → enIdx consumes 1 segment for first text,
        // img copied, second text gets fail-safe empty
        assertTrue(result.contains("<img src=\"x.jpg\""), "img preserved even with blank EN");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════════

    private static int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
