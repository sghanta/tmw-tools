/*
 * TMWServ to eAthena Converter (c) 2008 Jared Adams
 * License: GPL, v2 or later
 */

package converter;

import java.awt.*;
import java.io.*;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeSet;

import tiled.core.*;
import tiled.plugins.tmw.*;

public class Process {
    private static WLKInterface wlk = null;

    public static void prepWLK(File folder) {
        try {
            wlk = new WLKInterface(folder);
        } catch (NoClassDefFoundError ncdfe) {}
    }

    private static String getProp(Properties props, String name, String def) {
        if (name == null) return def;
        for (java.util.Map.Entry<Object, Object> entry : props.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey().toString())) {
                return entry.getValue().toString();
            }
        }
        return def;
    }

    private static int getProp(Properties props, String name, int def) {
        if (name == null) return def;
        try {
            return Integer.parseInt(getProp(props, name, "?"));
        } catch (Exception e) {}
        return def;
    }

    private static int[] resolveBounds(Rectangle in, boolean warp) {
        int x = in.x / 32;
        int y = in.y / 32;
        int width = in.width / 32;
        int height = in.height / 32;
        if (!warp) {
            if (width > 1) --width;
            if (height > 1) --height;
        }
        x += width / 2;
        y += height / 2;
        if (warp) {
            width -= 2;
            height -= 2;
        }
        return new int[]{x, y, width, height};
    }

    private static void handleWarp(PrintWriter out, String map, String name, Rectangle bounds, Properties props) {
        if (out == null) return;
        String dest = getProp(props, "dest_map", null);
        if (dest == null) return;
        int x = getProp(props, "dest_x", -1);
        if (x < 0) return;
        int y = getProp(props, "dest_y", -1);
        if (y < 0) return;
        int[] shape = resolveBounds(bounds, true);
        System.out.printf("Usable warp found: %s\n", name);
        out.printf("%s.gat,%d,%d\twarp\t%s\t%d,%d,%s.gat,%d,%d\n", map, shape[0], shape[1], name, shape[2], shape[3], dest, x / 32, y / 32);
    }

    private static int handleMob(PrintWriter out, String map, String name, Rectangle bounds, Properties props) {
        if (out == null) return -1;
        int mob = getProp(props, "monster_id", -1);
        if (mob < 0) return -1;
        mob += 1002;
        int max = getProp(props, "max_beings", 1);
        int time1 = getProp(props, "eA_spawn", 0);
        int time2 = getProp(props, "eA_death", 0);
        int[] shape = resolveBounds(bounds, false);
        System.out.printf("Usable mob found: %s (%d)\n", name, mob);
        out.printf("%s.gat,%d,%d,%d,%d\tmonster\t%s\t%d,%d,%d,%d,Mob%s::On%d\n", map, shape[0], shape[1], shape[2], shape[3], name, mob, max, time1, time2, map, mob);
        return mob;
    }

    private static void processObject(MapObject mo, String map, PrintWriter warpOut, PrintWriter mobOut, TreeSet<Integer> mobs) {
        if (mo == null) return;
        String name = mo.getName();
        String type = mo.getType();
        Rectangle bounds = new Rectangle(mo.getBounds());
        Properties props = mo.getProperties();

        if (type.equalsIgnoreCase("warp")) {
            handleWarp(warpOut, map, name, bounds, props);
        } else if (type.equalsIgnoreCase("spawn")) {
            mobs.add(handleMob(mobOut, map, name, bounds, props));
        }
    }

    private static void processObjects(Iterator<MapObject> objs, String map, PrintWriter warpOut, PrintWriter mobOut, TreeSet<Integer> mobs) {
        MapObject mo;
        while (objs.hasNext()) {
            mo = objs.next();
            if (mo == null) continue;
            processObject(mo, map, warpOut, mobOut, mobs);
        }
    }

    public static void processMap(String name, Map map, PrintWriter summary) {
        if (name == null) return;
        if (map == null) return;

        Properties props = map.getProperties();
        String title = getProp(props, "name", "");
        if (summary != null) {
            summary.printf("\tName: '%s'\n", title);
            summary.printf("\tMusic: '%s'\n", getProp(props, "music", ""));
            summary.printf("\tMinimap: '%s'\n", getProp(props, "minimap", ""));
        }
        String folderName = "server-data/npc/" + name;
        if (title.length() > 0) {
            folderName += "_" + title.replaceAll("\\s", "_");
            title = name + " " + title;
        } else {
            title = name;
        }

        System.out.println(title);

        if (wlk != null) wlk.write(name, map);

        File folder = new File(folderName);
        folder.mkdirs();
        PrintWriter warpOut = Main.getWriter(new File(folder, "passages.txt"));
        PrintWriter mobOut = Main.getWriter(new File(folder, "mobs.txt"));

        warpOut.printf("// %s warps\n\n", title);
        mobOut.printf("// %s mobs\n\n", title);

        TreeSet<Integer> mobs = new TreeSet<Integer>();
        processObjects(map.getObjects(), name, warpOut, mobOut, mobs);
        for (MapLayer layer : map) {
            if (layer instanceof ObjectGroup) {
                processObjects(((ObjectGroup) layer).getObjects(), name, warpOut, mobOut, mobs);
            }
        }

        warpOut.flush();
        warpOut.close();

        System.out.println("Starting mob points");
        mobOut.printf("\n\n%s.gat,0,0,0\tscript\tMob%1$s\t-1,{\n", name);
        for (int mob : mobs) {
            if (mob == -1) continue;
            mobOut.printf("On%d:\n\tset @mobID, %d;\n\tcallfunc \"MobPoints\";\n\tbreak;\n\n", mob, mob);
        }
        mobOut.printf("\tend;\n}\n");
        System.out.println("Finished mob points");

        mobOut.flush();
        mobOut.close();
    }
}