package cc.kasumi.uhc.scenario;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Material;

@Getter
@AllArgsConstructor
public class CutCleanBlock {

    private final Material material;
    private final Material replacement;
    private final int exp;
}
