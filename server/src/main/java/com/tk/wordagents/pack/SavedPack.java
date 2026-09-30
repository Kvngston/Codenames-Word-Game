package com.tk.wordagents.pack;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

/**
 * A word pack someone saved. Anyone with the code can play it; only the
 * device holding the edit token (stored as a hash) can change or delete it.
 */
@Entity
@Table(name = "word_packs")
public class SavedPack {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    /** One word per line. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String words;

    @Column(name = "edit_token_hash", nullable = false)
    private String editTokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavedPack() {}

    SavedPack(String code, String editTokenHash, String name, List<String> words) {
        this.code = code;
        this.editTokenHash = editTokenHash;
        this.createdAt = Instant.now();
        update(name, words);
    }

    void update(String name, List<String> words) {
        this.name = name;
        this.words = String.join("\n", words);
        this.updatedAt = Instant.now();
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public List<String> getWords() { return words.lines().toList(); }
    String getEditTokenHash() { return editTokenHash; }
}
