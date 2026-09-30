package com.tk.wordagents.game;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Cleans words from any source (built-in packs, saved packs, a host's custom list) the same way. */
public final class WordLists {

    public static final int MAX_WORD_LENGTH = 20;

    private static final Pattern ALLOWED = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}' .&-]*");

    private WordLists() {}

    /** Trims, collapses spaces and upper-cases one word, or explains why it can't go on a card. */
    public static String clean(String raw) {
        String word = raw == null ? "" : raw.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (word.isEmpty()) throw new GameException("Words can't be blank.");
        if (word.length() > MAX_WORD_LENGTH) throw new GameException("\"" + word + "\" is too long. Keep words to " + MAX_WORD_LENGTH + " characters.");
        if (!ALLOWED.matcher(word).matches()) throw new GameException("\"" + word + "\" has characters that can't go on a card. Use letters, numbers, spaces, hyphens or apostrophes.");
        return word;
    }

    /** Cleans every word, skipping blanks and duplicates while keeping the original order. */
    public static List<String> cleanAll(Collection<String> raw) {
        Set<String> words = new LinkedHashSet<>();
        for (String word : raw) {
            if (word != null && !word.isBlank()) words.add(clean(word));
        }
        return List.copyOf(words);
    }
}
