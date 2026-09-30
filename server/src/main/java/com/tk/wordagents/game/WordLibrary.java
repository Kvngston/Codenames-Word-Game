package com.tk.wordagents.game;

import java.util.List;
import java.util.Optional;

/** Looks up a pack's words by built-in id (e.g. {@code movies}) or saved-pack code. */
@FunctionalInterface
public interface WordLibrary {

    Optional<List<String>> words(String packId);

    /** Built-in packs only; the server adds saved packs on top. */
    WordLibrary BUILT_IN = id -> WordPacks.find(id).map(WordPacks.Pack::words);
}
