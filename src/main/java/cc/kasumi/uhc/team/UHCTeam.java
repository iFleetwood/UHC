package cc.kasumi.uhc.team;

import java.util.Set;
import java.util.UUID;

public interface UHCTeam {

    Set<UUID> getPlayers();
    UUID getLeader();
    int getNumber();
}
