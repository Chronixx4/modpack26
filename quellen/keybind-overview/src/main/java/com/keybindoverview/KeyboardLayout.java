package com.keybindoverview;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFW;

/** Positions in key units, independent of GUI scale. GLFW codes identify physical keys. */
public final class KeyboardLayout {
    public record Key(int code, String label, double x, double y, double width) {}

    public static List<Key> create(boolean german) {
        List<Key> keys = new ArrayList<>();
        add(keys, GLFW.GLFW_KEY_ESCAPE, "Esc", 0, 0, 1);
        for (int i = 0; i < 12; i++) add(keys, GLFW.GLFW_KEY_F1 + i, "F" + (i + 1), 2 + i, 0, 1);
        add(keys, GLFW.GLFW_KEY_PRINT_SCREEN, "Prt", 15.5, 0, 1);
        add(keys, GLFW.GLFW_KEY_SCROLL_LOCK, "Scr", 16.5, 0, 1);
        add(keys, GLFW.GLFW_KEY_PAUSE, "Pause", 17.5, 0, 1);
        row(keys, 0, 1.4, new int[]{96,49,50,51,52,53,54,55,56,57,48,45,61},
                german ? new String[]{"^","1","2","3","4","5","6","7","8","9","0","ß","´"}
                        : new String[]{"`","1","2","3","4","5","6","7","8","9","0","-","="});
        add(keys, GLFW.GLFW_KEY_BACKSPACE, "Back", 13, 1.4, 2);
        add(keys, GLFW.GLFW_KEY_TAB, "Tab", 0, 2.4, 1.5);
        row(keys, 1.5, 2.4, new int[]{81,87,69,82,84,89,85,73,79,80,91,93},
                german ? new String[]{"Q","W","E","R","T","Z","U","I","O","P","Ü","+"}
                        : new String[]{"Q","W","E","R","T","Y","U","I","O","P","[","]"});
        add(keys, 92, german ? "#" : "\\", 13.5, 2.4, 1.5);
        add(keys, GLFW.GLFW_KEY_CAPS_LOCK, "Caps", 0, 3.4, 1.75);
        row(keys, 1.75, 3.4, new int[]{65,83,68,70,71,72,74,75,76,59,39},
                german ? new String[]{"A","S","D","F","G","H","J","K","L","Ö","Ä"}
                        : new String[]{"A","S","D","F","G","H","J","K","L",";","'"});
        add(keys, GLFW.GLFW_KEY_ENTER, "Enter", 12.75, 3.4, 2.25);
        add(keys, GLFW.GLFW_KEY_LEFT_SHIFT, "Shift", 0, 4.4, german ? 1.25 : 2.25);
        if (german) add(keys, GLFW.GLFW_KEY_WORLD_1, "<", 1.25, 4.4, 1);
        row(keys, 2.25, 4.4, new int[]{90,88,67,86,66,78,77,44,46,47},
                german ? new String[]{"Y","X","C","V","B","N","M",",",".","-"}
                        : new String[]{"Z","X","C","V","B","N","M",",",".","/"});
        add(keys, GLFW.GLFW_KEY_RIGHT_SHIFT, "Shift", 12.25, 4.4, 2.75);
        add(keys, GLFW.GLFW_KEY_LEFT_CONTROL, "Ctrl", 0, 5.4, 1.5);
        add(keys, GLFW.GLFW_KEY_LEFT_SUPER, "Win", 1.5, 5.4, 1.25);
        add(keys, GLFW.GLFW_KEY_LEFT_ALT, "Alt", 2.75, 5.4, 1.25);
        add(keys, GLFW.GLFW_KEY_SPACE, "Space", 4, 5.4, 6.5);
        add(keys, GLFW.GLFW_KEY_RIGHT_ALT, "Alt", 10.5, 5.4, 1.25);
        add(keys, GLFW.GLFW_KEY_RIGHT_SUPER, "Win", 11.75, 5.4, 1.25);
        add(keys, GLFW.GLFW_KEY_MENU, "Menu", 13, 5.4, 1);
        add(keys, GLFW.GLFW_KEY_RIGHT_CONTROL, "Ctrl", 14, 5.4, 1);
        row(keys, 15.5, 1.4, new int[]{260,268,266}, new String[]{"Ins","Home","PgUp"});
        row(keys, 15.5, 2.4, new int[]{261,269,267}, new String[]{"Del","End","PgDn"});
        add(keys, GLFW.GLFW_KEY_UP, "↑", 16.5, 4.4, 1);
        row(keys, 15.5, 5.4, new int[]{263,264,262}, new String[]{"←","↓","→"});
        row(keys, 19, 1.4, new int[]{282,331,332,333}, new String[]{"Num","/","*","-"});
        row(keys, 19, 2.4, new int[]{327,328,329,334}, new String[]{"7","8","9","+"});
        row(keys, 19, 3.4, new int[]{324,325,326}, new String[]{"4","5","6"});
        row(keys, 19, 4.4, new int[]{321,322,323,335}, new String[]{"1","2","3","Enter"});
        add(keys, 320, "0", 19, 5.4, 2);
        add(keys, 330, ".", 21, 5.4, 1);
        return List.copyOf(keys);
    }

    private static void row(List<Key> keys, double x, double y, int[] codes, String[] labels) {
        for (int i = 0; i < codes.length; i++) add(keys, codes[i], labels[i], x + i, y, 1);
    }

    private static void add(List<Key> keys, int code, String label, double x, double y, double width) {
        keys.add(new Key(code, label, x, y, width));
    }
}
