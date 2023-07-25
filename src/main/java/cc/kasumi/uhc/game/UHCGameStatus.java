package cc.kasumi.uhc.game;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UHCGameStatus {

    IDLE(""),
    STARTING("starting"),
    SCATTERING("scattering"),
    STOPPING(""),
    FINISHED("");

    private String string;
}
