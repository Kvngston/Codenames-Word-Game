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

/** A clue in the room's history. */
@Entity
@Table(name = "clues")
public class Clue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_code")
    private Room room;

    @Column(nullable = false)
    private int turn;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private Team team;

    @Column(nullable = false)
    private String word;

    @Column(name = "clue_number")
    private Integer number;

    protected Clue() {}

    Clue(Room room, int turn, ActiveClue clue) {
        this.room = room;
        this.turn = turn;
        this.team = clue.team();
        this.word = clue.word();
        this.number = clue.number().value();
    }

    public int getTurn() { return turn; }
    public Team getTeam() { return team; }
    public String getWord() { return word; }
    public Count getNumber() { return Count.fromColumn(number); }
}
