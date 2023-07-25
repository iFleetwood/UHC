package cc.kasumi.uhc.team;

import lombok.Getter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
public class MultiPlayerTeam implements UHCTeam {

    private final Set<UUID> players = new HashSet<>();

    private UUID leader;
    private int number;

    public MultiPlayerTeam(UUID leader, int number) {
        this.leader = leader;
        this.number = number;
        players.add(leader);

    }
}
