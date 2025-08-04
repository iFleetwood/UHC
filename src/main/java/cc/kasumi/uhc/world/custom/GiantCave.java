package cc.kasumi.uhc.world.custom;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;

import java.util.Random;

public class GiantCave extends BlockPopulator {

    private final Material material = Material.AIR;

    public void populate(World world, Random random, Chunk source) {
        GCRandom gcRandom = new GCRandom(source);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = CaveSettings.CAVE_MAX_Y; y >= CaveSettings.CAVE_MIN_Y; y--) {
                    if (gcRandom.isInGiantCave(x, y, z)) {
                        Block block = source.getBlock(x, y, z);
                        block.setType(this.material);
                    }
                }
            }
        }
    }
}
