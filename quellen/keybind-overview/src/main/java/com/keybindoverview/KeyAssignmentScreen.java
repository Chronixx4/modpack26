package com.keybindoverview;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Edit individual actions on a physical key, including independently removing duplicates. */
public class KeyAssignmentScreen extends Screen {
    private final KeybindOverviewScreen overview;
    private final InputConstants.Key key;
    private final List<Button> rowButtons = new ArrayList<>();
    private List<KeyMapping> matches = List.of();
    private EditBox search;
    private Button previous, next, undo;
    private boolean allActions, duplicatesOnly;
    private String query = "";
    private int left, right, page, perPage;

    public KeyAssignmentScreen(KeybindOverviewScreen overview, InputConstants.Key key) {
        super(Component.translatable("keybindoverview.key_editor", key.getDisplayName()));
        this.overview = overview;
        this.key = key;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(720, width - 24);
        left = (width - panelWidth) / 2;
        right = left + panelWidth;
        perPage = Math.max(1, (height - 142) / 51);
        search = new EditBox(font, left, 49, panelWidth, 20, Component.translatable("keybindoverview.search"));
        search.setHint(Component.translatable("keybindoverview.search"));
        search.setValue(query);
        search.setResponder(value -> { query = value; page = 0; rebuildRows(); });
        addRenderableWidget(search);
        int buttonWidth = (panelWidth - 12) / 3;
        addRenderableWidget(Button.builder(Component.translatable("keybindoverview.add_action"), button -> {
            allActions = !allActions;
            button.setMessage(Component.translatable(allActions ? "keybindoverview.on_key" : "keybindoverview.add_action"));
            page = 0; rebuildRows();
        }).bounds(left, 76, buttonWidth, 20).build()).setMessage(Component.translatable(allActions ? "keybindoverview.on_key" : "keybindoverview.add_action"));
        addRenderableWidget(Button.builder(Component.translatable("keybindoverview.duplicates"), button -> {
            duplicatesOnly = !duplicatesOnly;
            button.setMessage(Component.translatable(duplicatesOnly ? "keybindoverview.show_all" : "keybindoverview.duplicates"));
            page = 0; rebuildRows();
        }).bounds(left + buttonWidth + 6, 76, buttonWidth, 20).build()).setMessage(Component.translatable(duplicatesOnly ? "keybindoverview.show_all" : "keybindoverview.duplicates"));
        undo = addRenderableWidget(Button.builder(Component.translatable("keybindoverview.undo"), button -> {
            overview.undo(); rebuildRows();
        }).bounds(left + 2 * (buttonWidth + 6), 76, buttonWidth, 20).build());
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuildRows(); })
                .bounds(left, height - 28, 30, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuildRows(); })
                .bounds(left + 36, height - 28, 30, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(right - 80, height - 28, 80, 20).build());
        rebuildRows();
    }

    private void rebuildRows() {
        for (Button button : rowButtons) removeWidget(button);
        rowButtons.clear();
        List<KeyMapping> all = Arrays.asList(Minecraft.getInstance().options.keyMappings);
        Map<String, Integer> counts = new HashMap<>();
        for (KeyMapping mapping : all) if (!mapping.isUnbound()) counts.merge(mapping.getKey().getName() + "|" + mapping.getKeyModifier(), 1, Integer::sum);
        String needle = query.toLowerCase(Locale.ROOT).strip();
        matches = all.stream()
                .filter(mapping -> allActions || (key.equals(InputConstants.UNKNOWN) ? mapping.isUnbound() : mapping.getKey().equals(key)))
                .filter(mapping -> !duplicatesOnly || counts.getOrDefault(mapping.getKey().getName() + "|" + mapping.getKeyModifier(), 0) > 1)
                .filter(mapping -> (Component.translatable(mapping.getName()).getString() + " "
                        + Component.translatable(mapping.getCategory()).getString() + " "
                        + mapping.getTranslatedKeyMessage().getString()).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(mapping -> Component.translatable(mapping.getName()).getString()))
                .toList();
        page = Math.max(0, Math.min(page, Math.max(0, (matches.size() - 1) / perPage)));
        for (int i = page * perPage; i < Math.min(matches.size(), (page + 1) * perPage); i++) {
            KeyMapping mapping = matches.get(i);
            int y = 129 + (i - page * perPage) * 51;
            int slots = allActions ? 4 : 3;
            int buttonWidth = (right - left - 12 - (slots - 1) * 5) / slots;
            int x = left + 6;
            if (allActions) {
                Button assign = Button.builder(Component.translatable("keybindoverview.assign_here"), button -> {
                    overview.changeBinding(mapping, key, mapping.getKeyModifier()); rebuildRows();
                }).bounds(x, y, buttonWidth, 18).build();
                assign.active = !key.equals(InputConstants.UNKNOWN);
                addRow(assign);
                x += buttonWidth + 5;
            }
            addRow(Button.builder(Component.translatable("keybindoverview.move"), button -> overview.beginMove(mapping))
                    .bounds(x, y, buttonWidth, 18).build());
            x += buttonWidth + 5;
            Button remove = Button.builder(Component.translatable("keybindoverview.remove"), button -> {
                overview.changeBinding(mapping, InputConstants.UNKNOWN, KeyModifier.NONE); rebuildRows();
            }).bounds(x, y, buttonWidth, 18).build();
            remove.active = !mapping.isUnbound();
            addRow(remove);
            x += buttonWidth + 5;
            addRow(Button.builder(Component.translatable("keybindoverview.default"), button -> {
                overview.resetBinding(mapping); rebuildRows();
            }).bounds(x, y, buttonWidth, 18).build());
        }
        previous.active = page > 0;
        next.active = (page + 1) * perPage < matches.size();
        undo.active = overview.canUndo();
    }

    private void addRow(Button button) { rowButtons.add(addRenderableWidget(button)); }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101520);
        graphics.drawString(font, fit(title.getString(), right - left), left, 16, 0xFFCEEAF5, false);
        graphics.drawString(font, fit(Component.translatable("keybindoverview.editor_help").getString(), right - left), left, 32, 0xFF91A1B7, false);
        for (int i = page * perPage; i < Math.min(matches.size(), (page + 1) * perPage); i++) {
            KeyMapping mapping = matches.get(i);
            int y = 104 + (i - page * perPage) * 51;
            graphics.fill(left, y, right, y + 47, 0xFF192231);
            graphics.drawString(font, fit(Component.translatable(mapping.getName()).getString() + " · "
                    + mapping.getTranslatedKeyMessage().getString(), right - left - 12), left + 6, y + 4, 0xFFF0F5FC, false);
            graphics.drawString(font, fit(Component.translatable(mapping.getCategory()).getString(), right - left - 12), left + 6, y + 15, 0xFF91A1B7, false);
        }
        if (matches.isEmpty()) graphics.drawString(font, Component.translatable("keybindoverview.empty"), left, 111, 0xFF91A1B7, false);
        graphics.drawString(font, fit(Component.translatable("keybindoverview.results", matches.size(), page + 1,
                Math.max(1, (matches.size() + perPage - 1) / perPage)).getString(), right - left - 166), left + 74, height - 22, 0xFF91A1B7, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String fit(String text, int available) {
        if (font.width(text) <= available) return text;
        return font.plainSubstrByWidth(text, Math.max(0, available - font.width("..."))) + "...";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical > 0 && page > 0) page--;
        else if (vertical < 0 && (page + 1) * perPage < matches.size()) page++;
        rebuildRows();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(overview); }

    @Override
    public boolean isPauseScreen() { return false; }
}
