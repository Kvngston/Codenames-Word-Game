package com.tk.wordagents.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One board card. Rows are reused across deals; {@link #cardId} is the id clients see. */
@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_code")
    private Room room;

    @Column(name = "card_id", nullable = false, unique = true)
    private String cardId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String word;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private CardRole role;

    @Column(nullable = false)
    private boolean revealed;

    protected Card() {}

    Card(Room room, int position) {
        this.room = room;
        this.position = position;
    }

    void deal(String cardId, String word, CardRole role) {
        this.cardId = cardId;
        this.word = word;
        this.role = role;
        this.revealed = false;
    }

    public String getCardId() { return cardId; }
    public int getPosition() { return position; }
    public String getWord() { return word; }
    public CardRole getRole() { return role; }
    public boolean isRevealed() { return revealed; }
    void reveal() { this.revealed = true; }
}
