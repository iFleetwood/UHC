package cc.kasumi.uhc.team;

import lombok.Getter;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Getter
public class SingePlayerTeam implements UHCTeam {

    private final UUID leader;

    public SingePlayerTeam(UUID leader) {
        this.leader = leader;
    }

    @Override
    public Set<UUID> getPlayers() {
        return Collections.singleton(leader);
    }

    @Override
    public int getNumber() {
        return -1;
    }
}