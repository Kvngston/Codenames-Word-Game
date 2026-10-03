package com.tk.wordagents.game;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Tells real words from made-up ones for clues. A word is real if GloVe knows it
 * (the 40k most common words, so plurals, tenses, names and modern words like
 * LAPTOP) or Webster's Second International lists it ({@code resources/words/web2.txt.gz},
 * public domain, lower-cased from {@code /usr/share/dict/web2}) for rarer ones.
 */
final class EnglishWords {

    private static final String RESOURCE = "/words/web2.txt.gz";
    private static final String[] WEBSTER = load();
    /** Endings Webster's leaves out, each with what to put back: BONFIRES, CARRIED, BAKING. */
    private static final String[][] ENDINGS = {
        {"ies", "y"}, {"ied", "y"}, {"ier", "y"}, {"iest", "y"}, {"ily", "y"},
        {"es", ""}, {"s", ""}, {"ed", ""}, {"ed", "e"}, {"ing", ""}, {"ing", "e"},
        {"er", ""}, {"er", "e"}, {"ers", ""}, {"ers", "e"}, {"est", ""}, {"est", "e"}, {"ly", ""},
    };

    private EnglishWords() {}

    /**
     * Whether a one-word clue is made of real words: the whole of it (X-RAY), or
     * each part of a hyphenated one (SPACE-AGE). Numbers count, accents are ignored,
     * a common ending counts if the word without it is listed (BONFIRES), and a word
     * with an apostrophe (DON'T, DOG'S) counts by its first part.
     */
    static boolean isReal(String clue) {
        String word = plain(clue);
        if (known(word)) return true;
        String[] parts = word.split("-");
        if (parts.length < 2) return false;
        return Arrays.stream(parts).allMatch(EnglishWords::known);
    }

    private static boolean known(String word) {
        if (word.isEmpty()) return false;
        if (word.chars().allMatch(Character::isDigit)) return true;
        if (listed(word) || listedWithEnding(word)) return true;
        int apostrophe = word.indexOf('\'');
        return apostrophe > 0 && listed(word.substring(0, apostrophe));
    }

    private static boolean listedWithEnding(String word) {
        for (String[] ending : ENDINGS) {
            if (!word.endsWith(ending[0])) continue;
            String stem = word.substring(0, word.length() - ending[0].length());
            if (stem.length() < 3) continue;
            if (listed(stem + ending[1])) return true;
            // A doubled final consonant: RUNNING, STOPPED, BIGGER.
            int last = stem.length() - 1;
            if (ending[1].isEmpty() && stem.charAt(last) == stem.charAt(last - 1) && listed(stem.substring(0, last))) return true;
        }
        return false;
    }

    private static boolean listed(String word) {
        return WordVectors.get().knows(word) || Arrays.binarySearch(WEBSTER, word) >= 0;
    }

    private static String plain(String clue) {
        String lower = clue.trim().toLowerCase(Locale.ROOT).replace('’', '\'');
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String[] load() {
        try (InputStream raw = EnglishWords.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) throw new IllegalStateException("Missing word list " + RESOURCE);
            BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8));
            String[] words = in.lines().filter(line -> !line.isEmpty()).toArray(String[]::new);
            Arrays.sort(words);
            return words;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
