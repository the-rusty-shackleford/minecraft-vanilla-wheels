/*
 * Vanilla Wheels - a vehicle protocol.
 * Copyright (C) 2026 Chunkworks
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.vanillawheels.client.keyring;

import com.chunkworks.vanillawheels.domain.Whereabouts;
import com.chunkworks.vanillawheels.keyring.KeyringMenu;
import com.chunkworks.vanillawheels.net.Payloads;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The keyring's screen (D-0041): a list of the player's vehicles, one row each -- its paint, its
 * name, where it is and what a recall costs as a share of its tank -- with a Recall button, which
 * becomes the exact quote with Confirm and Cancel once the server has found the vehicle. Every
 * word shown is the server's, from the menu's last rows; the buttons are the menu's button clicks.
 * Drawn by hand, as the lift's repair panel is: a panel, rows, flat buttons, a scrollbar.
 */
public final class KeyringScreen extends AbstractContainerScreen<KeyringMenu> {
    private static final int WIDTH = 320;
    private static final int ROW = 40;
    private static final int VISIBLE = 4;
    private static final int LIST_TOP = 20;
    private static final int LIST_LEFT = 8;
    private static final int LIST_WIDTH = WIDTH - 16;
    private static final int BUTTON_W = 64;
    private static final int BUTTON_H = 18;
    private static final int SMALL_W = 52;
    private int scroll;

    public KeyringScreen(KeyringMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = LIST_TOP + VISIBLE * ROW + 30;
        titleLabelX = 8;
        titleLabelY = 6;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        List<Payloads.KeyringRows.Row> rows = menu.shown().rows();
        g.drawString(font, Component.translatable(rows.size() == 1 ? "vanillawheels.ring.one" : "vanillawheels.ring.vehicles", rows.size()),
                WIDTH - 8 - font.width(Component.translatable(rows.size() == 1 ? "vanillawheels.ring.one" : "vanillawheels.ring.vehicles", rows.size())), titleLabelY, 0x808080, false);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF373737);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFFFFFFFF);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFFC6C6C6);
        List<Payloads.KeyringRows.Row> rows = menu.shown().rows();
        Payloads.KeyringRows.Request request = menu.shown().request();
        int listX = x + LIST_LEFT, listY = y + LIST_TOP;
        g.fill(listX, listY, listX + LIST_WIDTH, listY + VISIBLE * ROW, 0xFF8B8B8B);
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - VISIBLE));
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("vanillawheels.ring.empty"), x + imageWidth / 2, listY + VISIBLE * ROW / 2 - 4, 0xFFFFFF);
        }
        for (int i = 0; i < VISIBLE && scroll + i < rows.size(); i++) {
            Payloads.KeyringRows.Row row = rows.get(scroll + i);
            int ry = listY + i * ROW;
            boolean mine = request.present() && request.binding().equals(row.binding());
            g.fill(listX + 1, ry + 1, listX + LIST_WIDTH - 1, ry + ROW - 1, mine ? 0xFFDCD4A8 : 0xFFC6C6C6);
            g.fill(listX + 6, ry + 14, listX + 18, ry + 26, 0xFF373737);
            g.fill(listX + 7, ry + 15, listX + 17, ry + 25, 0xFF000000 | row.colour());
            // Three lines, each kept short of the buttons -- the one Recall button, or the Confirm and Cancel pair on
            // the quoted row: the name, where it is, what a recall costs (or the quote).
            int textWidth = LIST_WIDTH - 24 - 12 - (mine && request.quoted() ? 2 * SMALL_W + 4 : BUTTON_W);
            g.drawString(font, fit(row.name(), textWidth), listX + 24, ry + 5, 0x202020, false);
            Component[] lines = mine && request.quoted() ? quote(request) : where(row);
            g.drawString(font, fit(lines[0], textWidth), listX + 24, ry + 16, mine ? 0x3A3A10 : 0x505050, false);
            g.drawString(font, fit(lines[1], textWidth), listX + 24, ry + 27, mine ? 0x3A3A10 : 0x707070, false);
            if (!row.onRing()) {
                continue;   // the row says the key is missing; nothing to press
            }
            int by = ry + (ROW - BUTTON_H) / 2;
            if (!mine) {
                if (row.kind() != Whereabouts.Kind.COLLECTED.ordinal()) {
                    button(g, listX + LIST_WIDTH - 6 - BUTTON_W, by, BUTTON_W, Component.translatable("vanillawheels.ring.recall"), mouseX, mouseY, true);
                }
            } else if (!request.quoted()) {
                button(g, listX + LIST_WIDTH - 6 - BUTTON_W, by, BUTTON_W, Component.translatable("vanillawheels.ring.locating"), mouseX, mouseY, false);
            } else {
                button(g, listX + LIST_WIDTH - 6 - SMALL_W, by, SMALL_W, Component.translatable("vanillawheels.ring.cancel"), mouseX, mouseY, true);
                button(g, listX + LIST_WIDTH - 10 - 2 * SMALL_W, by, SMALL_W, Component.translatable("vanillawheels.ring.confirm"), mouseX, mouseY, true);
            }
        }
        if (rows.size() > VISIBLE) {
            int barX = listX + LIST_WIDTH - 4, barH = VISIBLE * ROW;
            int knob = Math.max(10, barH * VISIBLE / rows.size());
            int knobY = listY + (barH - knob) * scroll / (rows.size() - VISIBLE);
            g.fill(barX, listY, barX + 4, listY + barH, 0xFF5A5A5A);
            g.fill(barX, knobY, barX + 4, knobY + knob, 0xFFE0E0E0);
        }
        Component notice = menu.shown().notice();
        if (!notice.getString().isEmpty()) {
            g.drawString(font, notice, x + 8, y + imageHeight - 18, 0x404040, false);
        }
    }

    /** effects: {@code text} cut to {@code width}, in visual order for drawing */
    private net.minecraft.util.FormattedCharSequence fit(Component text, int width) {
        return net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(text, width));
    }

    /** effects: the row's second and third lines: where the vehicle is, and the share of a tank a recall from there costs; or that its key is missing */
    private static Component[] where(Payloads.KeyringRows.Row row) {
        if (!row.onRing()) {
            return new Component[] {Component.translatable("vanillawheels.ring.key_missing"), Component.translatable("vanillawheels.ring.key_missing_2")};
        }
        Whereabouts.Kind kind = Whereabouts.Kind.values()[Mth.clamp(row.kind(), 0, Whereabouts.Kind.values().length - 1)];
        String share = String.format(Locale.ROOT, "%d", Math.max(1, Math.round(100.0f * row.share())));
        Component cost = row.sameDimension() ? Component.translatable("vanillawheels.ring.share", share) : Component.translatable("vanillawheels.ring.share_all");
        return switch (kind) {
            case COLLECTED -> new Component[] {Component.translatable("vanillawheels.ring.collected"), Component.empty()};
            case ON_THE_GROUND -> new Component[] {row.sameDimension()
                    ? Component.translatable("vanillawheels.ring.on_the_ground", row.about())
                    : Component.translatable("vanillawheels.ring.on_the_ground_away"), cost};
            case DEPLOYED -> new Component[] {row.sameDimension()
                    ? Component.translatable("vanillawheels.ring.deployed", row.about())
                    : Component.translatable("vanillawheels.ring.deployed_away"), cost};
        };
    }

    /** effects: the quoted price on two lines: the fuel, and the condition lost if the tank is short */
    private static Component[] quote(Payloads.KeyringRows.Request request) {
        return new Component[] {
                Component.translatable("vanillawheels.ring.quote_fuel", String.format(Locale.ROOT, "%.1f", request.fuelPercent())),
                Component.translatable("vanillawheels.ring.quote_condition", String.format(Locale.ROOT, "%.1f", request.conditionLoss()))};
    }

    /** effects: draws a flat button; lit under the mouse when it can be pressed */
    private void button(GuiGraphics g, int x, int y, int w, Component label, int mouseX, int mouseY, boolean active) {
        boolean hover = active && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + BUTTON_H;
        g.fill(x, y, x + w, y + BUTTON_H, 0xFF202020);
        g.fill(x + 1, y + 1, x + w - 1, y + BUTTON_H - 1, !active ? 0xFF6A6A6A : hover ? 0xFF8A9AC8 : 0xFF707070);
        g.drawCenteredString(font, label, x + w / 2, y + 5, active ? 0xFFFFFF : 0xA0A0A0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<Payloads.KeyringRows.Row> rows = menu.shown().rows();
            Payloads.KeyringRows.Request request = menu.shown().request();
            int listX = leftPos + LIST_LEFT, listY = topPos + LIST_TOP;
            for (int i = 0; i < VISIBLE && scroll + i < rows.size(); i++) {
                Payloads.KeyringRows.Row row = rows.get(scroll + i);
                int ry = listY + i * ROW;
                int by = ry + (ROW - BUTTON_H) / 2;
                if (mouseY < by || mouseY >= by + BUTTON_H || !row.onRing()) {
                    continue;
                }
                boolean mine = request.present() && request.binding().equals(row.binding());
                if (!mine) {
                    if (row.kind() != Whereabouts.Kind.COLLECTED.ordinal() && within(mouseX, listX + LIST_WIDTH - 6 - BUTTON_W, BUTTON_W)) {
                        press(KeyringMenu.RECALL + scroll + i);
                        return true;
                    }
                } else if (request.quoted()) {
                    if (within(mouseX, listX + LIST_WIDTH - 6 - SMALL_W, SMALL_W)) {
                        press(KeyringMenu.CANCEL);
                        return true;
                    }
                    if (within(mouseX, listX + LIST_WIDTH - 10 - 2 * SMALL_W, SMALL_W)) {
                        press(KeyringMenu.CONFIRM);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean within(double x, int left, int width) {
        return x >= left && x < left + width;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = menu.shown().rows().size();
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, rows - VISIBLE));
        return true;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }
}
