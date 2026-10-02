package com.tk.wordagents.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Picks a board's words and who owns each one, so the board hangs together and the
 * assassin sits among the team words instead of off on its own:
 * <ol>
 *   <li>the host's custom words go on first, then the packs share the rest evenly;</li>
 *   <li>within a pack the words come from a few of its sub-themes, not all of them;</li>
 *   <li>red and blue cards are spread across the sources in proportion;</li>
 *   <li>the assassin is the non-team card closest in meaning to both teams' words.</li>
 * </ol>
 */
final class BoardDealer {

    record Dealt(String word, CardRole role) {}

    /** Where a board word came from: the host's list or a pack, and which of its sub-themes ({@link #NO_THEME} if it has none). */
    private record Origin(String source, int group) {}

    private static final String CUSTOM = "custom";
    private static final int NO_THEME = -1;
    /** A pack draws from enough sub-themes to have this many times its share of words to choose from. */
    private static final double THEME_SLACK = 1.5;
    private static final int ROLE_ATTEMPTS = 64;
    /** The assassin is one of this many best-placed non-team cards, so it isn't always the very closest. */
    private static final int ASSASSIN_SHORTLIST = 3;
    private static final double SAME_THEME = 0.5;
    private static final double SAME_SOURCE = 0.25;

    private BoardDealer() {}

    static List<Dealt> deal(Room room, WordLibrary library, Team starting, Random random) {
        Map<String, Origin> board = drawWords(room, library, random);
        List<String> words = new ArrayList<>(board.keySet());
        Collections.shuffle(words, random);
        Map<String, CardRole> roles = assignRoles(words, board, starting, random);
        return words.stream().map(word -> new Dealt(word, roles.get(word))).toList();
    }

    // ---- Words ----

    /** The board's 25 words, in draw order, each with where it came from. */
    private static Map<String, Origin> drawWords(Room room, WordLibrary library, Random random) {
        List<String> custom = new ArrayList<>(room.getCustomWords());
        Set<String> taken = new LinkedHashSet<>(custom);
        List<List<List<String>>> packs = new ArrayList<>();
        List<String> packIds = new ArrayList<>();
        for (String id : room.getWordPacks()) {
            library.words(id).ifPresent(words -> {
                packIds.add(id);
                packs.add(WordPacks.find(id).filter(pack -> pack.words().equals(words)).map(WordPacks.Pack::groups).orElse(List.of(words)));
            });
        }
        int pool = poolSize(custom, packs);
        // A saved pack may have been deleted or trimmed since it was picked; never deal a short board.
        if (pool < GameRules.BOARD_SIZE) {
            packIds.add(WordPacks.DEFAULT_ID);
            packs.add(WordPacks.defaultPack().groups());
            pool = poolSize(custom, packs);
        }
        room.setPoolSize(pool);

        Map<String, Origin> board = new LinkedHashMap<>();
        Collections.shuffle(custom, random);
        custom.stream().limit(GameRules.BOARD_SIZE).forEach(word -> board.put(word, new Origin(CUSTOM, NO_THEME)));

        // Even shares, the odd cards going to random packs, so a big pack can't crowd out a small one.
        int open = GameRules.BOARD_SIZE - board.size();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < packs.size(); i++) order.add(i);
        Collections.shuffle(order, random);
        for (int rank = 0; rank < order.size() && open > 0; rank++) {
            int left = order.size() - rank;
            draw(board, taken, packIds.get(order.get(rank)), packs.get(order.get(rank)), (open + left - 1) / left, random);
            open = GameRules.BOARD_SIZE - board.size();
        }
        // Packs that came up short (words shared with the custom list or another pack) are made up by the rest.
        for (int pack : order) {
            if (board.size() < GameRules.BOARD_SIZE) draw(board, taken, packIds.get(pack), packs.get(pack), GameRules.BOARD_SIZE - board.size(), random);
        }
        return board;
    }

    private static void draw(Map<String, Origin> board, Set<String> taken, String packId, List<List<String>> groups, int count, Random random) {
        themedDraw(groups, taken, count, random).forEach((word, group) -> {
            board.put(word, new Origin(packId, groups.size() > 1 ? group : NO_THEME));
            taken.add(word);
        });
    }

    private static int poolSize(List<String> custom, List<List<List<String>>> packs) {
        Set<String> words = new LinkedHashSet<>(custom);
        packs.forEach(groups -> groups.forEach(words::addAll));
        return words.size();
    }

    /**
     * Up to {@code count} untaken words from one pack, from as few of its sub-themes
     * as leave some choice; a small pack gives what it has and the next pack makes it up.
     */
    private static Map<String, Integer> themedDraw(List<List<String>> groups, Set<String> taken, int count, Random random) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) order.add(i);
        Collections.shuffle(order, random);
        Map<String, Integer> candidates = new LinkedHashMap<>();
        for (int group : order) {
            if (candidates.size() >= Math.ceil(count * THEME_SLACK)) break;
            groups.get(group).stream().filter(word -> !taken.contains(word)).forEach(word -> candidates.putIfAbsent(word, group));
        }
        List<String> words = new ArrayList<>(candidates.keySet());
        Collections.shuffle(words, random);
        Map<String, Integer> picked = new LinkedHashMap<>();
        words.stream().limit(count).forEach(word -> picked.put(word, candidates.get(word)));
        return picked;
    }

    // ---- Roles ----

    private static Map<String, CardRole> assignRoles(List<String> words, Map<String, Origin> origins, Team starting, Random random) {
        CardRole first = starting.cardRole();
        CardRole second = starting.other().cardRole();
        // NEUTRAL here stands for every non-team card; the assassin is picked from them afterwards.
        List<CardRole> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) slots.add(first);
        for (int i = 0; i < 8; i++) slots.add(second);
        while (slots.size() < words.size()) slots.add(CardRole.NEUTRAL);

        List<CardRole> best = null;
        double bestSpread = Double.MAX_VALUE;
        for (int attempt = 0; attempt < ROLE_ATTEMPTS && bestSpread > 0; attempt++) {
            Collections.shuffle(slots, random);
            double spread = spread(words, origins, slots, first, second);
            if (spread < bestSpread) {
                bestSpread = spread;
                best = new ArrayList<>(slots);
            }
        }
        Map<String, CardRole> roles = new HashMap<>();
        for (int i = 0; i < words.size(); i++) roles.put(words.get(i), best.get(i));
        roles.put(pickAssassin(words, origins, roles, first, second, random), CardRole.ASSASSIN);
        return roles;
    }

    /** How far each source's red and blue counts are from its fair share; 0 is perfectly even. */
    private static double spread(List<String> words, Map<String, Origin> origins, List<CardRole> slots, CardRole first, CardRole second) {
        Map<String, double[]> counts = new HashMap<>();
        for (int i = 0; i < words.size(); i++) {
            double[] count = counts.computeIfAbsent(origins.get(words.get(i)).source(), source -> new double[3]);
            count[0]++;
            if (slots.get(i) == first) count[1]++;
            if (slots.get(i) == second) count[2]++;
        }
        double spread = 0;
        for (double[] count : counts.values()) {
            double share = count[0] / words.size();
            spread += Math.abs(count[1] - 9 * share) + Math.abs(count[2] - 8 * share);
        }
        return spread;
    }

    /**
     * The non-team card that most looks like both teams' words: each candidate scores
     * its closeness to the nearer of the two teams' best matches, and candidates whose
     * source holds both teams' cards go first. One of the top few is picked at random.
     */
    private static String pickAssassin(List<String> words, Map<String, Origin> origins, Map<String, CardRole> roles, CardRole first, CardRole second, Random random) {
        WordVectors vectors = WordVectors.get();
        Map<String, Optional<float[]>> meaning = new HashMap<>();
        words.forEach(word -> meaning.put(word, vectors.vector(word)));
        List<String> firstTeam = words.stream().filter(word -> roles.get(word) == first).toList();
        List<String> secondTeam = words.stream().filter(word -> roles.get(word) == second).toList();

        Comparator<String> byFit = Comparator
            .comparing((String word) -> holdsBothTeams(origins.get(word).source(), words, origins, roles, first, second))
            .thenComparingDouble(word -> Math.min(closeness(word, firstTeam, origins, meaning), closeness(word, secondTeam, origins, meaning)))
            .reversed();
        List<String> candidates = words.stream().filter(word -> roles.get(word) == CardRole.NEUTRAL).sorted(byFit).toList();
        return candidates.get(random.nextInt(Math.min(ASSASSIN_SHORTLIST, candidates.size())));
    }

    private static boolean holdsBothTeams(String source, List<String> words, Map<String, Origin> origins, Map<String, CardRole> roles, CardRole first, CardRole second) {
        List<CardRole> held = words.stream().filter(word -> origins.get(word).source().equals(source)).map(roles::get).toList();
        return held.contains(first) && held.contains(second);
    }

    /** The mean of a word's two best matches among a team's words. */
    private static double closeness(String word, List<String> team, Map<String, Origin> origins, Map<String, Optional<float[]>> meaning) {
        return team.stream()
            .mapToDouble(other -> similarity(word, other, origins, meaning))
            .boxed()
            .sorted(Comparator.reverseOrder())
            .limit(2)
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0);
    }

    /** Vector closeness where both words have one, never below what their shared pack and sub-theme imply. */
    private static double similarity(String a, String b, Map<String, Origin> origins, Map<String, Optional<float[]>> meaning) {
        Origin x = origins.get(a);
        Origin y = origins.get(b);
        double tagged = !x.source().equals(y.source()) ? 0 : x.group() != NO_THEME && x.group() == y.group() ? SAME_THEME : SAME_SOURCE;
        Optional<float[]> u = meaning.get(a);
        Optional<float[]> v = meaning.get(b);
        return u.isPresent() && v.isPresent() ? Math.max(tagged, WordVectors.cosine(u.get(), v.get())) : tagged;
    }
}
