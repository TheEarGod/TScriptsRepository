package net.runelite.client.plugins.tscripts.api.library;

import net.runelite.api.GameObject;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.tscripts.util.Compare;
import net.runelite.client.plugins.tscripts.sevices.cache.GameCache;
import net.unethicalite.client.Static;

import java.util.Arrays;

public class TObjects
{
    public static void interact(TileObject object, int action)
    {
        WorldPoint worldPoint = object.getWorldLocation();
        if(object instanceof GameObject)
        {
            GameObject gameObject = (GameObject) object;
            worldPoint = WorldPoint.fromScene(
                    Static.getClient(),
                    gameObject.getSceneMinLocation().getX(),
                    gameObject.getSceneMinLocation().getY(),
                    gameObject.getPlane()
            );
        }
        TPackets.sendClickPacket(-1, -1);
        TPackets.sendObjectActionPacket(action, object.getId(), worldPoint.getX(), worldPoint.getY(), false);
    }

    public static void interact(TileObject object, String action)
    {
        int actionIndex = Arrays.asList(object.getActions()).indexOf(action);
        if (actionIndex == -1)
            return;
        interact(object, actionIndex);
    }

    public static TileObject getObject(Object identifier)
    {
        if(identifier instanceof TileObject)
        {
            return (TileObject) identifier;
        }
        if(identifier instanceof Integer)
        {
            return GameCache.get().objectStream()
                    .filter(o -> o.getId() == (int) identifier)
                    .min(Compare.DISTANCE).orElse(null);
        }
        else if (identifier instanceof String)
        {
            return GameCache.get().objectStream()
                    .filter(o -> o.getName().equals(identifier))
                    .min(Compare.DISTANCE).orElse(null);
        }
        return null;
    }

    public static TileObject getObjectWithin(Object identifier, int distance)
    {
        if(identifier instanceof TileObject)
        {
            TileObject object = (TileObject) identifier;
            return object.distanceTo(Static.getClient().getLocalPlayer()) <= distance ? object : null;
        }
        if(identifier instanceof Integer)
        {
            return GameCache.get().objectStream()
                    .filter(o -> o.getId() == (int) identifier && o.distanceTo(Static.getClient().getLocalPlayer()) <= distance)
                    .min(Compare.DISTANCE).orElse(null);
        }
        else if (identifier instanceof String)
        {
            return GameCache.get().objectStream()
                    .filter(o -> o.getName().equals(identifier) && o.distanceTo(Static.getClient().getLocalPlayer()) <= distance)
                    .min(Compare.DISTANCE).orElse(null);
        }
        return null;
    }

    public static TileObject getObjectAt(Object identifier, int x, int y)
    {
        if(identifier instanceof Integer)
        {
            return GameCache.get().objectStream()
                    .filter(o -> o.getId() == (int) identifier && o.getWorldLocation().getX() == x && o.getWorldLocation().getY() == y)
                    .findFirst().orElse(null);
        }
        else if (identifier instanceof String)
        {
            return GameCache.get().objectStream()
                    .filter(o -> o.getName().equals(identifier) && o.getWorldLocation().getX() == x && o.getWorldLocation().getY() == y)
                    .findFirst().orElse(null);
        }
        return null;
    }

    public static TileObject getOpenableAt(int x, int y)
    {
        return GameCache.get().objectStream()
                .filter(o -> (o.getName().toLowerCase().contains("door") || o.getName().toLowerCase().contains("gate")) && o.hasAction("Open") && o.getWorldLocation().getX() == x && o.getWorldLocation().getY() == y)
                .findFirst().orElse(null);
    }
}
