package com.tk.wordagents.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "players")
public class Player {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_code")
    private Room room;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private Team team;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private Seat seat;

    @Column(name = "join_order", nullable = false)
    private int joinOrder;

    /** Card ids this operative has highlighted this turn, comma-separated in the order tapped. Everyone sees them. */
    @Column(name = "highlights")
    private String highlights;

    protected Player() {}

    public Player(Room room, String id, String tokenHash, String name, int joinOrder) {
        this.room = room;
        this.id = id;
        this.tokenHash = tokenHash;
        this.name = name;
        this.joinOrder = joinOrder;
    }

    public boolean isSeated() {
        return team != null && seat != null;
    }

    public String getId() { return id; }
    public Room getRoom() { return room; }
    public String getTokenHash() { return tokenHash; }
    public String getName() { return name; }
    public Team getTeam() { return team; }
    public void setTeam(Team team) { this.team = team; }
    public Seat getSeat() { return seat; }
    public void setSeat(Seat seat) { this.seat = seat; }
    public int getJoinOrder() { return joinOrder; }

    public List<String> getHighlights() {
        return highlights == null || highlights.isEmpty() ? List.of() : List.of(highlights.split(","));
    }

    void setHighlights(List<String> cardIds) {
        highlights = cardIds.isEmpty() ? null : String.join(",", cardIds);
    }
}
