package com.tk.wordagents.pack;

import com.tk.wordagents.game.GameException;
import com.tk.wordagents.game.GameRules;
import com.tk.wordagents.game.WordLibrary;
import com.tk.wordagents.game.WordLists;
import com.tk.wordagents.game.WordPacks;
import com.tk.wordagents.room.SeatTokens;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Built-in and saved word packs. Rooms resolve their packs through this {@link WordLibrary}. */
@Service
public class PackService implements WordLibrary {

    public record BuiltInPack(String id, String name, String description, int size, List<String> sample) {}

    public record PackView(String code, String name, List<String> words) {}

    /** Returned once, on create: the edit token is never shown again. */
    public record PackGrant(String code, String editToken, PackView pack) {}

    public static final int MAX_WORDS = 500;
    private static final int SAMPLE_SIZE = 8;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SavedPackRepository packs;

    PackService(SavedPackRepository packs) {
        this.packs = packs;
    }

    public List<BuiltInPack> builtIns() {
        return WordPacks.all().stream()
            .map(pack -> new BuiltInPack(pack.id(), pack.name(), pack.description(), pack.words().size(), pack.words().subList(0, SAMPLE_SIZE)))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<List<String>> words(String packId) {
        if (packId == null) return Optional.empty();
        Optional<List<String>> builtIn = BUILT_IN.words(packId);
        return builtIn.isPresent() ? builtIn : packs.findById(normalize(packId)).map(SavedPack::getWords);
    }

    @Transactional
    public PackGrant create(String name, List<String> words) {
        String code;
        do {
            code = newCode();
        } while (packs.existsById(code) || WordPacks.find(code).isPresent());
        String token = SeatTokens.generate();
        SavedPack pack = packs.save(new SavedPack(code, SeatTokens.hash(token), cleanName(name), cleanWords(words)));
        return new PackGrant(code, token, view(pack));
    }

    @Transactional(readOnly = true)
    public PackView get(String code) {
        return view(find(code));
    }

    @Transactional
    public PackView update(String code, String token, String name, List<String> words) {
        SavedPack pack = owned(code, token);
        pack.update(cleanName(name), cleanWords(words));
        return view(pack);
    }

    @Transactional
    public void delete(String code, String token) {
        packs.delete(owned(code, token));
    }

    private SavedPack find(String code) {
        return packs.findById(normalize(code)).orElseThrow(PackException::notFound);
    }

    private SavedPack owned(String code, String token) {
        SavedPack pack = find(code);
        if (token == null || !pack.getEditTokenHash().equals(SeatTokens.hash(token))) throw PackException.notYours();
        return pack;
    }

    private static PackView view(SavedPack pack) {
        return new PackView(pack.getCode(), pack.getName(), pack.getWords());
    }

    private static String cleanName(String name) {
        String clean = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (clean.isEmpty()) throw new PackException(HttpStatus.BAD_REQUEST, "Name your pack.");
        if (clean.length() > 40) throw new PackException(HttpStatus.BAD_REQUEST, "Keep pack names to 40 characters.");
        return clean;
    }

    private static List<String> cleanWords(List<String> raw) {
        List<String> words;
        try {
            words = WordLists.cleanAll(raw == null ? List.of() : raw);
        } catch (GameException e) {
            throw new PackException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (words.size() < GameRules.BOARD_SIZE) {
            throw new PackException(HttpStatus.BAD_REQUEST, "A pack needs at least " + GameRules.BOARD_SIZE + " different words to fill a board. This one has " + words.size() + ".");
        }
        if (words.size() > MAX_WORDS) throw new PackException(HttpStatus.BAD_REQUEST, "Keep packs to " + MAX_WORDS + " words.");
        return words;
    }

    private static String newCode() {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return code.toString();
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
