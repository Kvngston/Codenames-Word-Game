package com.tk.wordagents.game;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The built-in word packs. Each pack's words live in {@code resources/packs/<id>.txt},
 * one per line; add a line to {@link #ALL} and a file to ship a new genre.
 */
public final class WordPacks {

    public record Pack(String id, String name, String description, List<String> words) {}

    public static final String DEFAULT_ID = "classic";

    private static final List<Pack> ALL = List.of(
        load("classic", "Classic", "Double-meaning nouns for the original game."),
        load("movies", "Movies & TV", "Blockbusters, genres and life on set."),
        load("science", "Science & Space", "Atoms, orbits and everything in the lab."),
        load("food", "Food & Drink", "Kitchens, snacks and the dinner table."),
        load("sports", "Sports & Games", "Stadiums, board games and the big match."),
        load("travel", "Places & Travel", "Landmarks, landscapes and a packed suitcase."),
        load("fantasy", "Myth & Fantasy", "Dragons, gods and a quest or two."),
        load("arts", "Music & Arts", "Studios, galleries and the concert hall."));

    private WordPacks() {}

    public static List<Pack> all() {
        return ALL;
    }

    public static Optional<Pack> find(String id) {
        if (id == null) return Optional.empty();
        String key = id.trim().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(pack -> pack.id().equals(key)).findFirst();
    }

    public static Pack defaultPack() {
        return find(DEFAULT_ID).orElseThrow();
    }

    private static Pack load(String id, String name, String description) {
        try (InputStream in = WordPacks.class.getResourceAsStream("/packs/" + id + ".txt")) {
            if (in == null) throw new IllegalStateException("Missing word pack file packs/" + id + ".txt");
            List<String> words = WordLists.cleanAll(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
            if (words.size() < GameRules.BOARD_SIZE) throw new IllegalStateException("Word pack " + id + " needs at least " + GameRules.BOARD_SIZE + " words.");
            return new Pack(id, name, description, words);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
