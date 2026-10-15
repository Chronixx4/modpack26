package com.keybindoverview;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class KeybindOverviewScreen extends Screen {
    private static final int GAP = 8, ROW = 19;
    private final Screen parent;
    private final List<Card> cards = new ArrayList<>();
    private int left, right, top, bottom, contentHeight, keyCount;
    private double scroll, grabOffset;
    private boolean dragging;
    private EditBox search;
    private String query = "";
    private int filterMode, totalKeyCount;
    private final Set<String> collapsed = new HashSet<>();
    private boolean pickingKey;
    private Button undoButton;
    private Button duplicatesButton;
    private Button unboundButton;
    private final Deque<List<BindingChange>> history = new ArrayDeque<>();
    private boolean fromList;
    private String noticeText = "";
    private boolean noticeWarn;
    private long noticeUntil;
    private record BindingChange(KeyMapping mapping, InputConstants.Key key, KeyModifier modifier) {}
    private boolean keyboardView;
    private boolean germanLayout = true;
    private KeyMapping editing;
    private InputConstants.Key pressedKey = InputConstants.UNKNOWN;
    private InputConstants.Key pressedModifier = InputConstants.UNKNOWN;
    private final Map<InputConstants.Key, List<KeyMapping>> assignments = new HashMap<>();
    private final List<KeyBox> keyBoxes = new ArrayList<>();
    private double keyboardScroll;
    private int keyboardHeight;
    private record KeyBox(InputConstants.Key key, String label, int x, int y, int width, int height) {}
    private record Card(String id, String title, List<KeyMapping> keys, int x, int y, int width, int height) {}

    public KeybindOverviewScreen(Screen parent) {
        super(Component.translatable("keybindoverview.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(900, Math.max(1, width - 24));
        left = (width - panelWidth) / 2;
        right = left + panelWidth;
        top = 115;
        bottom = Math.max(top + 1, height - 38);
        rebuildAssignments();
        search = new EditBox(font, left + 12, 33, Math.max(50, panelWidth - 112), 18, Component.translatable("keybindoverview.search"));
        search.setHint(Component.translatable("keybindoverview.search"));
        search.setValue(query);
        search.setResponder(value -> { query = value; scroll = 0; collapsed.clear(); rebuildCards(); });
        addRenderableWidget(search);
        int tabWidth = Math.min(110, Math.max(40, (panelWidth - 96) / 2));
        addRenderableWidget(Button.builder(Component.translatable("keybindoverview.list"), button -> {
            keyboardView = false; dragging = false;
        }).bounds(left, 60, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("keybindoverview.keyboard"), button -> {
            keyboardView = true; dragging = false;
        }).bounds(left + tabWidth + 6, 60, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(germanLayout ? "QWERTZ" : "QWERTY"), button -> {
            germanLayout = !germanLayout;
            button.setMessage(Component.literal(germanLayout ? "QWERTZ" : "QWERTY"));
        }).bounds(right - 80, 60, 80, 20).build());
        int actionWidth = Math.max(40, Math.min(110, (panelWidth - 12) / 3));
        duplicatesButton = addRenderableWidget(Button.builder(Component.translatable("keybindoverview.duplicates"), button -> {
            filterMode = filterMode == 1 ? 0 : 1;
            keyboardView = false; scroll = 0; rebuildCards();
        }).bounds(left, 84, actionWidth, 20).build());
        unboundButton = addRenderableWidget(Button.builder(Component.translatable("keybindoverview.unbound"), button -> {
            filterMode = filterMode == 2 ? 0 : 2;
            keyboardView = false; scroll = 0; rebuildCards();
        }).bounds(left + actionWidth + 6, 84, actionWidth, 20).build());
        undoButton = addRenderableWidget(Button.builder(Component.translatable("keybindoverview.undo"), button -> undo())
                .bounds(left + (actionWidth + 6) * 2, 84, actionWidth, 20).build());
        int ioWidth = Math.max(40, Math.min(70, panelWidth / 8));
        addRenderableWidget(Button.builder(Component.translatable("keybindoverview.import"), button -> importBindings())
                .bounds(right - ioWidth, 84, ioWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("keybindoverview.export"), button -> exportBindings())
                .bounds(right - 2 * ioWidth - 6, 84, ioWidth, 20).build());
        rebuildAssignments();
        clampScroll();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // render() paints our background before the content. Screen.render() calls this
        // again when drawing widgets; its default blur would blur the completed UI.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        undoButton.active = canUndo();
        duplicatesButton.setMessage(Component.translatable(filterMode == 1 ? "keybindoverview.show_all" : "keybindoverview.duplicates"));
        unboundButton.setMessage(Component.translatable(filterMode == 2 ? "keybindoverview.show_all" : "keybindoverview.unbound"));
        search.visible = editing == null;
        search.active = editing == null;
        g.fill(0, 0, width, height, 0xF0101520);
        g.fill(left, 18, left + 3, 47, 0xFF65C7E8);
        Component heading = editing == null ? title : Component.translatable("keybindoverview.pick_target", Component.translatable(editing.getName()));
        g.drawString(font, fit(heading.getString(), right - left - 12), left + 12, 19, 0xFFF0F5FC, false);
        Component subtitle = editing == null ? Component.translatable("keybindoverview.subtitle", keyCount)
                : Component.translatable("keybindoverview.pick_help");
        if (editing != null) g.drawString(font, fit(subtitle.getString(), right - left - 12), left + 12, 36, 0xFF91A1B7, false);
        else {
            String count = keyCount + "/" + totalKeyCount;
            g.drawString(font, count, right - font.width(count), 38, 0xFF91A1B7, false);
        }
        g.fill(left, 54, right, 55, 0xFF2B3648);
        if (keyboardView) {
            renderKeyboard(g, mouseX, mouseY, partialTick);
            return;
        }
        g.enableScissor(left, top, right - 10, bottom);
        KeyMapping hovered = null;
        for (Card card : cards) {
            int y = top + card.y() - (int) scroll;
            if (y + card.height() <= top || y >= bottom) continue;
            int x = card.x();
            g.fill(x, y, x + card.width(), y + card.height(), 0xFF192231);
            g.fill(x, y, x + card.width(), y + 27, 0xFF25364B);
            g.fill(x, y, x + 2, y + 27, 0xFF65C7E8);
            String count = Integer.toString(card.keys().size());
            String categoryTitle = (collapsed.contains(card.id()) ? "+ " : "- ") + card.title();
            g.drawString(font, fit(categoryTitle, card.width() - font.width(count) - 28), x + 10, y + 9, 0xFFCEEAF5, false);
            g.drawString(font, count, x + card.width() - font.width(count) - 10, y + 9, 0xFF91A1B7, false);
            if (collapsed.contains(card.id())) continue;
            int rowY = y + 32;
            for (KeyMapping key : card.keys()) {
                boolean hover = mouseX >= x && mouseX < x + card.width() && mouseY >= rowY
                        && mouseY < rowY + ROW && mouseY >= top && mouseY < bottom;
                if (hover) {
                    g.fill(x + 4, rowY - 2, x + card.width() - 4, rowY + ROW - 3, 0xFF243247);
                    hovered = key;
                }
                String binding = editing == key ? "> ... <" : key.getTranslatedKeyMessage().getString();
                int badgeWidth = Math.min(Math.max(32, font.width(binding) + 14), Math.max(32, card.width() / 2));
                int badgeX = x + card.width() - badgeWidth - 8;
                boolean conflict = conflicts(key);
                g.fill(badgeX, rowY, badgeX + badgeWidth, rowY + 17,
                        editing == key ? 0xFF946E22 : conflict ? 0xFF8B4259 : 0xFF33455C);
                String badgeText = fit(binding, badgeWidth - 10);
                g.drawString(font, badgeText, badgeX + (badgeWidth - font.width(badgeText)) / 2,
                        rowY + 4, key.isUnbound() ? 0xFF91A1B7 : 0xFFF0F5FC, false);
                g.drawString(font, fit(Component.translatable(key.getName()).getString(), badgeX - x - 16),
                        x + 9, rowY + 4, 0xFFD6DFEB, false);
                rowY += ROW;
            }
        }
        g.disableScissor();
        if (cards.isEmpty()) g.drawString(font, Component.translatable("keybindoverview.no_results"), left, top + 8, 0xFF91A1B7, false);
        if (maxScroll() > 0) {
            g.fill(right - 5, top, right - 2, bottom, 0xFF253247);
            int thumbY = top + (int) (scroll / maxScroll() * (bottom - top - thumbHeight()));
            g.fill(right - 6, thumbY, right - 1, thumbY + thumbHeight(), 0xFF65C7E8);
        }
        g.fill(left, height - 29, right, height - 28, 0xFF2B3648);
        g.drawString(font, fit(footer(Component.translatable(editing == null ? "keybindoverview.edit_hint" : "keybindoverview.recording").getString()), Math.max(0, right - left - font.width(Component.translatable("keybindoverview.close")) - 12)), left, height - 19, footerColor(), false);
        Component close = Component.translatable("keybindoverview.close");
        g.drawString(font, close, right - font.width(close), height - 19, 0xFFCEEAF5, false);
        super.render(g, mouseX, mouseY, partialTick);
        if (hovered != null) showAssignments(g, hovered.getKey(), mouseX, mouseY);
    }

    private String fit(String text, int available) {
        if (available <= 0) return "";
        if (font.width(text) <= available) return text;
        return font.plainSubstrByWidth(text, Math.max(0, available - font.width("..."))) + "...";
    }

    private void rebuildAssignments() {
        assignments.clear();
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (!mapping.isUnbound()) assignments.computeIfAbsent(mapping.getKey(), ignored -> new ArrayList<>()).add(mapping);
        }
        rebuildCards();
    }

    private void rebuildCards() {
        Map<String, List<KeyMapping>> groups = new TreeMap<>(Comparator.comparing((String id) -> Component.translatable(id).getString()).thenComparing(Comparator.naturalOrder()));
        String needle = query.strip().toLowerCase(Locale.ROOT);
        KeyMapping[] all = Minecraft.getInstance().options.keyMappings;
        totalKeyCount = all.length;
        keyCount = 0;
        for (KeyMapping mapping : all) {
            if (filterMode == 1 && !conflicts(mapping)) continue;
            if (filterMode == 2 && !mapping.isUnbound()) continue;
            String text = Component.translatable(mapping.getName()).getString() + " "
                    + Component.translatable(mapping.getCategory()).getString() + " "
                    + mapping.getTranslatedKeyMessage().getString() + " " + mapping.getName();
            if (!text.toLowerCase(Locale.ROOT).contains(needle)) continue;
            groups.computeIfAbsent(mapping.getCategory(), ignored -> new ArrayList<>()).add(mapping);
            keyCount++;
        }
        cards.clear();
        int panelWidth = right - left;
        int columns = Math.max(1, Math.min(4, (panelWidth - 12 + GAP) / 210));
        int cardWidth = Math.max(1, (panelWidth - 12 - (columns - 1) * GAP) / columns);
        int[] heights = new int[columns];
        for (var entry : groups.entrySet()) {
            entry.getValue().sort(Comparator.comparing(mapping -> Component.translatable(mapping.getName()).getString()));
            int column = 0;
            for (int i = 1; i < columns; i++) if (heights[i] < heights[column]) column = i;
            int cardHeight = collapsed.contains(entry.getKey()) ? 28 : 39 + entry.getValue().size() * ROW;
            cards.add(new Card(entry.getKey(), Component.translatable(entry.getKey()).getString(), entry.getValue(),
                    left + column * (cardWidth + GAP), heights[column], cardWidth, cardHeight));
            heights[column] += cardHeight + GAP;
        }
        contentHeight = Math.max(0, Arrays.stream(heights).max().orElse(0) - GAP);
        clampScroll();
    }

    private KeyMapping mappingAt(double mouseX, double mouseY) {
        for (Card card : cards) {
            if (collapsed.contains(card.id()) || mouseX < card.x() || mouseX >= card.x() + card.width()) continue;
            int relative = (int) (mouseY - top - card.y() + (int) scroll - 32);
            if (relative >= 0 && relative < card.keys().size() * ROW) return card.keys().get(relative / ROW);
        }
        return null;
    }

    private void assign(InputConstants.Key key, KeyModifier modifier) {
        changeBinding(editing, key, modifier);
        finishPick();
    }

    private void finishPick() {
        editing = null;
        pickingKey = false;
        pressedKey = pressedModifier = InputConstants.UNKNOWN;
        if (fromList) { keyboardView = false; fromList = false; }
    }

    void changeBinding(KeyMapping mapping, InputConstants.Key key, KeyModifier modifier) {
        if (mapping.getKey().equals(key) && mapping.getKeyModifier() == modifier) return;
        history.push(List.of(new BindingChange(mapping, mapping.getKey(), mapping.getKeyModifier())));
        mapping.setKeyModifierAndCode(modifier, key);
        saveBindings();
        warnConflicts(mapping);
    }

    void resetBinding(KeyMapping mapping) {
        history.push(List.of(new BindingChange(mapping, mapping.getKey(), mapping.getKeyModifier())));
        mapping.setToDefault();
        saveBindings();
        warnConflicts(mapping);
    }

    void undo() {
        if (history.isEmpty()) return;
        List<BindingChange> previous = history.pop();
        for (int i = previous.size() - 1; i >= 0; i--) {
            BindingChange change = previous.get(i);
            change.mapping().setKeyModifierAndCode(change.modifier(), change.key());
        }
        saveBindings();
        notice(Component.translatable("keybindoverview.notice.undone").getString(), false);
    }

    /** Same key and same modifier: a real collision. Different modifiers (Ctrl+X vs. X) are separate. */
    private boolean conflicts(KeyMapping mapping) {
        if (mapping.isUnbound()) return false;
        int same = 0;
        for (KeyMapping other : assignments.getOrDefault(mapping.getKey(), List.of()))
            if (other.getKeyModifier() == mapping.getKeyModifier()) same++;
        return same > 1;
    }

    /** 0 = free, 1 = used, 2 = a real collision exists on this key. */
    private int state(InputConstants.Key key) {
        List<KeyMapping> list = assignments.getOrDefault(key, List.of());
        if (list.isEmpty()) return 0;
        for (KeyMapping mapping : list) if (conflicts(mapping)) return 2;
        return 1;
    }

    private void notice(String text, boolean warn) {
        noticeText = text;
        noticeWarn = warn;
        noticeUntil = System.currentTimeMillis() + 9000;
    }

    private boolean noticeActive() { return System.currentTimeMillis() < noticeUntil && !noticeText.isEmpty(); }
    private String footer(String fallback) { return noticeActive() ? noticeText : fallback; }
    private int footerColor() { return noticeActive() ? (noticeWarn ? 0xFFFFCD70 : 0xFF8FE3A8) : 0xFF91A1B7; }

    private void warnConflicts(KeyMapping mapping) {
        if (mapping.isUnbound()) {
            notice(Component.translatable("keybindoverview.notice.removed", Component.translatable(mapping.getName())).getString(), false);
            return;
        }
        List<String> others = new ArrayList<>();
        for (KeyMapping other : assignments.getOrDefault(mapping.getKey(), List.of()))
            if (other != mapping && other.getKeyModifier() == mapping.getKeyModifier())
                others.add(Component.translatable(other.getName()).getString());
        if (others.isEmpty()) {
            notice(Component.translatable("keybindoverview.notice.ok", mapping.getTranslatedKeyMessage()).getString(), false);
            return;
        }
        String list = String.join(", ", others.subList(0, Math.min(3, others.size())))
                + (others.size() > 3 ? " +" + (others.size() - 3) : "");
        notice(Component.translatable("keybindoverview.notice.conflict", mapping.getTranslatedKeyMessage(), list).getString(), true);
    }

    private Path exchangeFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("keybindoverview").resolve("keybindings.txt");
    }

    private void exportBindings() {
        try {
            Path file = exchangeFile();
            Files.createDirectories(file.getParent());
            List<String> lines = new ArrayList<>();
            for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings)
                lines.add("key_" + mapping.getName() + ":" + mapping.saveString()
                        + (mapping.getKeyModifier() != KeyModifier.NONE ? ":" + mapping.getKeyModifier() : ""));
            Files.write(file, lines, StandardCharsets.UTF_8);
            notice(Component.translatable("keybindoverview.notice.exported", lines.size()).getString(), false);
        } catch (IOException e) {
            notice(Component.translatable("keybindoverview.notice.io_error", String.valueOf(e.getMessage())).getString(), true);
        }
    }

    private void importBindings() {
        Path file = exchangeFile();
        if (!Files.exists(file)) {
            notice(Component.translatable("keybindoverview.notice.no_file").getString(), true);
            return;
        }
        try {
            Map<String, KeyMapping> byName = new HashMap<>();
            for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) byName.put(mapping.getName(), mapping);
            List<BindingChange> changes = new ArrayList<>();
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.strip();
                int colon = line.indexOf(':');
                if (!line.startsWith("key_") || colon < 0) continue;
                KeyMapping mapping = byName.get(line.substring(4, colon));
                if (mapping == null) continue;
                String[] parts = line.substring(colon + 1).split(":");
                InputConstants.Key key;
                try { key = InputConstants.getKey(parts[0]); } catch (IllegalArgumentException e) { continue; }
                KeyModifier modifier = parts.length > 1 ? KeyModifier.valueFromString(parts[1]) : KeyModifier.NONE;
                if (mapping.getKey().equals(key) && mapping.getKeyModifier() == modifier) continue;
                changes.add(new BindingChange(mapping, mapping.getKey(), mapping.getKeyModifier()));
                mapping.setKeyModifierAndCode(modifier, key);
            }
            if (!changes.isEmpty()) { history.push(changes); saveBindings(); }
            notice(Component.translatable("keybindoverview.notice.imported", changes.size()).getString(), false);
        } catch (IOException e) {
            notice(Component.translatable("keybindoverview.notice.io_error", String.valueOf(e.getMessage())).getString(), true);
        }
    }

    /** Free letter keys closest to the movement block, shown as suggestions while picking a key. */
    private Set<InputConstants.Key> suggestions() {
        KeyBox anchor = null;
        for (KeyBox box : keyBoxes) if (box.key().getValue() == GLFW.GLFW_KEY_S && box.key().getType() == InputConstants.Type.KEYSYM) anchor = box;
        if (anchor == null) return Set.of();
        final KeyBox center = anchor;
        List<KeyBox> free = new ArrayList<>();
        for (KeyBox box : keyBoxes) {
            int code = box.key().getValue();
            if (box.key().getType() == InputConstants.Type.KEYSYM && code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z
                    && assignments.getOrDefault(box.key(), List.of()).isEmpty()) free.add(box);
        }
        free.sort(Comparator.comparingDouble(box -> Math.pow(box.x() - center.x(), 2) + Math.pow(box.y() - center.y(), 2)));
        Set<InputConstants.Key> result = new HashSet<>();
        for (int i = 0; i < Math.min(4, free.size()); i++) result.add(free.get(i).key());
        return result;
    }

    boolean canUndo() { return !history.isEmpty(); }

    void beginMove(KeyMapping mapping) {
        fromList = false;
        keyboardView = true;
        editing = mapping;
        pickingKey = true;
        pressedKey = pressedModifier = InputConstants.UNKNOWN;
        Minecraft.getInstance().setScreen(this);
    }

    private void openKeyEditor(InputConstants.Key key) {
        Minecraft.getInstance().setScreen(new KeyAssignmentScreen(this, key));
    }

    private KeyBox keyAt(double mouseX, double mouseY) {
        if (mouseY < top + 17 || mouseY >= bottom || mouseX >= right - 9) return null;
        for (KeyBox box : keyBoxes) {
            if (mouseX >= box.x() && mouseX < box.x() + box.width()
                    && mouseY >= box.y() && mouseY < box.y() + box.height()) return box;
        }
        return null;
    }

    private void saveBindings() {
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        rebuildAssignments();
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (editing != null) {
            InputConstants.Key released = InputConstants.getKey(keyCode, scanCode);
            if (!pressedKey.equals(InputConstants.UNKNOWN) && released.equals(pressedKey)) {
                assign(pressedKey, KeyModifier.getKeyModifier(pressedModifier));
            } else if (pressedKey.equals(InputConstants.UNKNOWN) && released.equals(pressedModifier)) {
                assign(pressedModifier, KeyModifier.NONE);
            }
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void renderKeyboard(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int legendX = left;
        for (int state = 0; state < 3; state++) {
            g.fill(legendX, top, legendX + 7, top + 7, statusColor(state));
            String label = Component.translatable("keybindoverview.status." + state).getString();
            g.drawString(font, label, legendX + 11, top, 0xFFD6DFEB, false);
            legendX += font.width(label) + 23;
        }
        int unit = Math.max(10, (right - left - 12) / 23);
        keyboardHeight = (int) (unit * 8.2) + 46;
        int visibleHeight = Math.max(1, bottom - top - 20);
        keyboardScroll = Math.max(0, Math.min(keyboardScroll, Math.max(0, keyboardHeight - visibleHeight)));
        int originY = top + 20 - (int) keyboardScroll;
        keyBoxes.clear();
        for (KeyboardLayout.Key key : KeyboardLayout.create(germanLayout)) {
            keyBoxes.add(new KeyBox(InputConstants.Type.KEYSYM.getOrCreate(key.code()), key.label(),
                    left + (int) (key.x() * unit), originY + (int) (key.y() * unit),
                    (int) (key.width() * unit) - 2, unit - 2));
        }
        int mouseYStart = originY + (int) (unit * 6.8);
        for (int i = 0; i < 5; i++) {
            keyBoxes.add(new KeyBox(InputConstants.Type.MOUSE.getOrCreate(i),
                    Component.translatable("keybindoverview.mouse", i + 1).getString(),
                    left + i * 4 * unit, mouseYStart, 4 * unit - 3, unit + 4));
        }
        int freeKeys = 0;
        for (KeyBox box : keyBoxes) if (state(box.key()) == 0) freeKeys++;
        String freeText = Component.translatable("keybindoverview.free_count", freeKeys).getString();
        g.drawString(font, freeText, right - font.width(freeText) - 10, top, 0xFF8FE3A8, false);
        Set<InputConstants.Key> suggested = editing != null ? suggestions() : Set.of();
        KeyBox hovered = null;
        Set<KeyMapping> searchMatches = new HashSet<>();
        for (Card card : cards) searchMatches.addAll(card.keys());
        String needle = query.strip().toLowerCase(Locale.ROOT);
        g.enableScissor(left, top + 17, right - 9, bottom);
        for (KeyBox box : keyBoxes) {
            int boxState = state(box.key());
            boolean hover = mouseX >= box.x() && mouseX < box.x() + box.width()
                    && mouseY >= box.y() && mouseY < box.y() + box.height()
                    && mouseY >= top + 17 && mouseY < bottom;
            boolean matchesSearch = needle.isEmpty()
                    || assignments.getOrDefault(box.key(), List.of()).stream().anyMatch(searchMatches::contains)
                    || box.key().getDisplayName().getString().toLowerCase(Locale.ROOT).contains(needle);
            g.fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(),
                    matchesSearch ? statusColor(boxState) : 0xFF202632);
            if (hover) {
                hovered = box;
                g.renderOutline(box.x(), box.y(), box.width(), box.height(), 0xFFF0F5FC);
            }
            if (suggested.contains(box.key()))
                g.renderOutline(box.x(), box.y(), box.width(), box.height(), 0xFF7CFFB0);
            if (editing != null && editing.getKey().equals(box.key()))
                g.renderOutline(box.x(), box.y(), box.width(), box.height(), 0xFFFFCD70);
            // Keep F10/F11/F12 and navigation labels distinguishable at small GUI sizes.
            float labelScale = Math.min(1.0f, (box.width() - 4.0f) / Math.max(1, font.width(box.label())));
            g.pose().pushPose();
            g.pose().translate(box.x() + (box.width() - font.width(box.label()) * labelScale) / 2,
                    box.y() + (box.height() - 8 * labelScale) / 2, 0);
            g.pose().scale(labelScale, labelScale, 1);
            g.drawString(font, box.label(), 0, 0, 0xFFF0F5FC, false);
            g.pose().popPose();
        }
        int noteY = originY + (int) (unit * 8.2);
        for (var line : font.split(Component.translatable("keybindoverview.keyboard_note"), Math.max(1, right - left - 12))) {
            g.drawString(font, line, left, noteY, 0xFF91A1B7, false);
            noteY += 11;
        }
        g.disableScissor();
        int max = Math.max(0, keyboardHeight - visibleHeight);
        if (max > 0) {
            int thumb = Math.min(visibleHeight, Math.max(18, visibleHeight * visibleHeight / keyboardHeight));
            int y = top + 20 + (int) (keyboardScroll / max * (visibleHeight - thumb));
            g.fill(right - 5, top + 20, right - 2, bottom, 0xFF253247);
            g.fill(right - 6, y, right - 1, y + thumb, 0xFF65C7E8);
        }
        g.fill(left, height - 29, right, height - 28, 0xFF2B3648);
        String keyboardHint = editing == null ? Component.translatable("keybindoverview.keyboard_hint").getString()
                : Component.translatable("keybindoverview.move_hint", Component.translatable(editing.getName())).getString();
        g.drawString(font, fit(footer(keyboardHint), Math.max(0, right - left - font.width(Component.translatable("keybindoverview.close")) - 12)), left, height - 19, footerColor(), false);
        Component close = Component.translatable("keybindoverview.close");
        g.drawString(font, close, right - font.width(close), height - 19, 0xFFCEEAF5, false);
        super.render(g, mouseX, mouseY, partialTick);
        if (hovered != null) showAssignments(g, hovered.key(), mouseX, mouseY);
    }

    private int statusColor(int state) {
        return switch (state) {
            case 0 -> 0xFF304B3C;
            case 1 -> 0xFF305A78;
            default -> 0xFF8B4259;
        };
    }

    private void showAssignments(GuiGraphics g, InputConstants.Key key, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        var mappings = assignments.getOrDefault(key, List.of());
        lines.add(key.getDisplayName().copy().append(" · ")
                .append(Component.translatable("keybindoverview.status." + state(key))));
        for (KeyMapping mapping : mappings) {
            lines.add(Component.translatable(mapping.getName()).append(" — ")
                    .append(mapping.getTranslatedKeyMessage()).append(" (")
                    .append(Component.translatable(mapping.getCategory())).append(")"));
        }
        if (state(key) == 2) lines.add(Component.translatable("keybindoverview.multiple_note"));
        else if (mappings.size() > 1) lines.add(Component.translatable("keybindoverview.context_note"));
        var wrapped = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (Component line : lines) wrapped.addAll(font.split(line, Math.max(80, Math.min(280, width - 24))));
        g.renderTooltip(font, wrapped, mouseX, mouseY);
    }

    private int maxScroll() { return Math.max(0, contentHeight - (bottom - top)); }
    private void clampScroll() { scroll = Math.max(0, Math.min(scroll, maxScroll())); }
    private int thumbHeight() {
        return Math.min(bottom - top, Math.max(24, (bottom - top) * (bottom - top) / Math.max(1, contentHeight)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (keyboardView) {
            keyboardScroll -= vertical * 30;
            keyboardScroll = Math.max(0, Math.min(keyboardScroll, Math.max(0, keyboardHeight - (bottom - top - 20))));
            return true;
        }
        scroll -= vertical * 30;
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editing != null) {
            if (pickingKey) {
                KeyBox target = keyAt(mouseX, mouseY);
                if (button == 0 && target != null) assign(target.key(), editing.getKeyModifier());
                return true;
            }
            assign(InputConstants.Type.MOUSE.getOrCreate(button), KeyModifier.getActiveModifier());
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (!keyboardView && mouseY >= top && mouseY < bottom && mouseX >= left && mouseX < right - 10) {
            if (button == 0) for (Card card : cards) {
                int y = top + card.y() - (int) scroll;
                if (mouseX >= card.x() && mouseX < card.x() + card.width() && mouseY >= y && mouseY < y + 27) {
                    if (!collapsed.add(card.id())) collapsed.remove(card.id());
                    rebuildCards();
                    return true;
                }
            }
            KeyMapping target = mappingAt(mouseX, mouseY);
            if (target != null && (button == 0 || button == 1)) {
                if (button == 1) { resetBinding(target); }
                else { fromList = true; keyboardView = true; editing = target; pickingKey = true; pressedKey = pressedModifier = InputConstants.UNKNOWN; }
                return true;
            }
        }
        if (keyboardView) {
            KeyBox target = keyAt(mouseX, mouseY);
            if (button == 0 && target != null) { openKeyEditor(target.key()); return true; }
            return false;
        }
        if (button == 0 && maxScroll() > 0 && mouseX >= right - 9 && mouseX <= right && mouseY >= top && mouseY < bottom) {
            double thumbY = top + scroll / maxScroll() * (bottom - top - thumbHeight());
            grabOffset = mouseY >= thumbY && mouseY < thumbY + thumbHeight() ? mouseY - thumbY : thumbHeight() / 2.0;
            dragging = true;
            moveThumb(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void moveThumb(double mouseY) {
        int travel = bottom - top - thumbHeight();
        scroll = travel > 0 ? (mouseY - top - grabOffset) / travel * maxScroll() : 0;
        clampScroll();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) { moveThumb(mouseY); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) { dragging = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editing != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishPick();
            } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
                assign(InputConstants.UNKNOWN, KeyModifier.NONE);
            } else {
                InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
                if (KeyModifier.isKeyCodeModifier(key)) pressedModifier = key;
                else pressedKey = key;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F && hasControlDown() && editing == null) { setFocused(search); search.setFocused(true); return true; }
        if (editing == null && search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (!query.isEmpty()) search.setValue("");
                else { search.setFocused(false); setFocused(null); }
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
    @Override
    public boolean isPauseScreen() { return false; }
}


