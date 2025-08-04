package cc.kasumi.uhc.world.generator;

import cc.kasumi.uhc.util.ReflectionUtil;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.util.Arrays;

public class BiomeHandler {

    private Object[] origBiomes;

    public BiomeHandler() {
        this.origBiomes = getMcBiomesCopy();
    }

    protected void swapBiome(BiomeSwap.Biome oldBiome, BiomeSwap.Biome newBiome) {
        if (oldBiome.getId() != BiomeSwap.Biome.SKY.getId()) {
            Object[] biomes = getMcBiomes();
            biomes[oldBiome.getId()] = getOrigBiome(newBiome.getId());
        } else {
            Bukkit.getLogger().warning("Cannot swap SKY biome!");
        }
    }

    private Object[] getMcBiomesCopy() {
        Object[] b = getMcBiomes();
        return Arrays.copyOf(b, b.length);
    }

    private Object[] getMcBiomes() {
        try {
            Class<?> biomeBase = ReflectionUtil.getCraftClass("BiomeBase");
            Field biomeF = ReflectionUtil.getField(biomeBase, "biomes");
            biomeF.setAccessible(true);
            return (Object[]) biomeF.get(null);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return new Object[256];
    }

    private Object getOrigBiome(int value) {
        return this.origBiomes[value];
    }
}
