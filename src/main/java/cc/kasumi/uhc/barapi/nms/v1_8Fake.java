package cc.kasumi.uhc.barapi.nms;

import org.bukkit.Location;
import cc.kasumi.uhc.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This is the FakeDragon class for BarAPI.
 * It is based on the code by SoThatsIt.
 * <p>
 * http://forums.bukkit.org/threads/tutorial-utilizing-the-boss-health-bar.158018/page-5#post-2053705
 *
 * @author James Mortemore
 */

public class v1_8Fake extends FakeDragon {

  private Object dragon;
  private int id;

  public v1_8Fake(String name, Location loc) {
    super(name, loc);
  }

  public static boolean isUsable() {
    Class<?> PacketPlayOutEntityTeleport = ReflectionUtil.getCraftClass("PacketPlayOutEntityTeleport");

    try {
      PacketPlayOutEntityTeleport
              .getConstructor(new Class<?>[] { int.class, int.class, int.class, int.class, byte.class, byte.class, boolean.class, boolean.class });
    } catch (IllegalArgumentException e) {
      return false;
    } catch (SecurityException e) {
      return false;
    } catch (NoSuchMethodException e) {
      return false;
    }

    return true;
  }

  @Override
  public Object getSpawnPacket() {
    Class<?> Entity = ReflectionUtil.getCraftClass("Entity");
    Class<?> EntityLiving = ReflectionUtil.getCraftClass("EntityLiving");
    Class<?> EntityEnderDragon = ReflectionUtil.getCraftClass("EntityEnderDragon");
    Object packet = null;
    try {
      dragon = EntityEnderDragon.getConstructor(ReflectionUtil.getCraftClass("World")).newInstance(getWorld());

      Method setLocation = ReflectionUtil
              .getMethod(EntityEnderDragon, "setLocation", new Class<?>[] { double.class, double.class, double.class, float.class, float.class });
      setLocation.invoke(dragon, getX(), getY(), getZ(), getPitch(), getYaw());

      Method setInvisible = ReflectionUtil.getMethod(EntityEnderDragon, "setInvisible", new Class<?>[] { boolean.class });
      setInvisible.invoke(dragon, true);

      Method setCustomName = ReflectionUtil.getMethod(EntityEnderDragon, "setCustomName", new Class<?>[] { String.class });
      setCustomName.invoke(dragon, name);

      Method setHealth = ReflectionUtil.getMethod(EntityEnderDragon, "setHealth", new Class<?>[] { float.class });
      setHealth.invoke(dragon, health);

      Field motX = ReflectionUtil.getField(Entity, "motX");
      motX.set(dragon, getXvel());

      Field motY = ReflectionUtil.getField(Entity, "motY");
      motY.set(dragon, getYvel());

      Field motZ = ReflectionUtil.getField(Entity, "motZ");
      motZ.set(dragon, getZvel());

      Method getId = ReflectionUtil.getMethod(EntityEnderDragon, "getId", new Class<?>[] {});
      this.id = (Integer) getId.invoke(dragon);

      Class<?> PacketPlayOutSpawnEntityLiving = ReflectionUtil.getCraftClass("PacketPlayOutSpawnEntityLiving");

      packet = PacketPlayOutSpawnEntityLiving.getConstructor(new Class<?>[] { EntityLiving }).newInstance(dragon);
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    } catch (SecurityException e) {
      e.printStackTrace();
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }

    return packet;
  }

  @Override
  public Object getDestroyPacket() {
    Class<?> PacketPlayOutEntityDestroy = ReflectionUtil.getCraftClass("PacketPlayOutEntityDestroy");

    Object packet = null;
    try {
      packet = PacketPlayOutEntityDestroy.newInstance();
      Field a = PacketPlayOutEntityDestroy.getDeclaredField("a");
      a.setAccessible(true);
      a.set(packet, new int[] { id });
    } catch (SecurityException e) {
      e.printStackTrace();
    } catch (NoSuchFieldException e) {
      e.printStackTrace();
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    }

    return packet;
  }

  @Override
  public Object getMetaPacket(Object watcher) {
    Class<?> DataWatcher = ReflectionUtil.getCraftClass("DataWatcher");

    Class<?> PacketPlayOutEntityMetadata = ReflectionUtil.getCraftClass("PacketPlayOutEntityMetadata");

    Object packet = null;
    try {
      packet = PacketPlayOutEntityMetadata.getConstructor(new Class<?>[] { int.class, DataWatcher, boolean.class })
              .newInstance(id, watcher, true);
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    } catch (SecurityException e) {
      e.printStackTrace();
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }

    return packet;
  }

  @Override
  public Object getTeleportPacket(Location loc) {
    Class<?> PacketPlayOutEntityTeleport = ReflectionUtil.getCraftClass("PacketPlayOutEntityTeleport");
    Object packet = null;

    try {
      packet = PacketPlayOutEntityTeleport
              .getConstructor(new Class<?>[] { int.class, int.class, int.class, int.class, byte.class, byte.class, boolean.class, boolean.class })
              .newInstance(this.id, loc.getBlockX() * 32, loc.getBlockY() * 32, loc.getBlockZ() * 32, (byte) ((int) loc
                      .getYaw() * 256 / 360), (byte) ((int) loc.getPitch() * 256 / 360), false, false);
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    } catch (SecurityException e) {
      e.printStackTrace();
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }

    return packet;
  }

  @Override
  public Object getWatcher() {
    Class<?> Entity = ReflectionUtil.getCraftClass("Entity");
    Class<?> DataWatcher = ReflectionUtil.getCraftClass("DataWatcher");

    Object watcher = null;
    try {
      watcher = DataWatcher.getConstructor(new Class<?>[] { Entity }).newInstance(dragon);
      Method a = ReflectionUtil.getMethod(DataWatcher, "a", new Class<?>[] { int.class, Object.class });

      a.invoke(watcher, 5, isVisible() ? (byte) 0 : (byte) 0x20);
      a.invoke(watcher, 6, (Float) health);
      a.invoke(watcher, 7, (Integer) 0);
      a.invoke(watcher, 8, (Byte) (byte) 0);
      a.invoke(watcher, 10, name);
      a.invoke(watcher, 11, (Byte) (byte) 1);
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    } catch (SecurityException e) {
      e.printStackTrace();
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }

    return watcher;
  }
}
