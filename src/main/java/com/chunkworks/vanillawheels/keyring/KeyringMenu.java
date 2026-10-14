/*
 * Vanilla Wheels - a vehicle protocol.
 * Copyright (C) 2026 Rusty Shackleford and nfx
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
package com.chunkworks.vanillawheels.keyring;

import com.chunkworks.vanillawheels.Keyring;
import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.RecoveryData;
import com.chunkworks.vanillawheels.domain.RecallCost;
import com.chunkworks.vanillawheels.net.Payloads;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The keyring's menu (D-0041): no slots. Every tick the server reads the player's pairings and
 * their recall request from {@link RecoveryData} and, when anything changed, sends the rows down
 * in one payload; the buttons come back up as the menu's own button clicks, so nothing of the
 * ring's state is trusted from the client. The client's copy holds the last rows it was sent.
 */
public final class KeyringMenu extends AbstractContainerMenu {
    public static final int CANCEL = 0;
    public static final int CONFIRM = 1;
    /** Plus the row's index. */
    public static final int RECALL = 2;

    private final Player player;
    @Nullable private Payloads.KeyringRows last;
    private Payloads.KeyringRows shown = new Payloads.KeyringRows(List.of(), Payloads.KeyringRows.Request.NONE, Component.empty());

    public KeyringMenu(int id, Inventory inventory) {
        super(ModContent.KEYRING_MENU.get(), id);
        this.player = inventory.player;
    }

    /** effects: the rows as last sent (server) or received (client) */
    public Payloads.KeyringRows shown() {
        return shown;
    }

    /** effects: takes the rows the server sent (client) */
    public void accept(Payloads.KeyringRows rows) {
        shown = rows;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        Payloads.KeyringRows now = read(sp);
        if (!now.equals(last)) {
            last = now;
            shown = now;
            // A connection without the channel (a gametest's player, whose connection goes nowhere) is not sent to: the
            // network version keeps every real client on it.
            if (sp.connection.hasChannel(Payloads.KeyringRows.TYPE)) {
                PacketDistributor.sendToPlayer(sp, now);
            }
        }
    }

    /** effects: the rows and the request as the index has them now */
    private static Payloads.KeyringRows read(ServerPlayer sp) {
        RecoveryData data = RecoveryData.get(sp.server);
        List<Payloads.KeyringRows.Row> rows = new ArrayList<>();
        for (RecoveryData.Row r : data.rows(sp)) {
            Component name = com.chunkworks.vanillawheels.KeyFobs.Mark.NONE.equals(r.mark()) ? Component.translatable("vanillawheels.ring.a_vehicle") : r.mark().name();
            rows.add(new Payloads.KeyringRows.Row(r.binding(), name, r.mark().colour(), r.onRing(), r.whereabouts().kind().ordinal(),
                    r.whereabouts().sameDimension(), r.whereabouts().about(), (float) r.whereabouts().share()));
        }
        RecoveryData.Progress p = data.progress(sp);
        Payloads.KeyringRows.Request request = Payloads.KeyringRows.Request.NONE;
        if (p != null) {
            RecallCost q = p.quote();
            request = q == null ? new Payloads.KeyringRows.Request(true, p.binding(), false, 0.0f, 0.0f)
                    : new Payloads.KeyringRows.Request(true, p.binding(), true, 100.0f * q.fuelCost() / p.capacity(),
                            (p.conditionBefore() - q.conditionAfter().remaining()) / 100.0f);
        }
        return new Payloads.KeyringRows(rows, request, data.notice(sp));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }
        RecoveryData data = RecoveryData.get(sp.server);
        if (id == CANCEL) {
            data.cancelByPlayer(sp);
            return true;
        }
        if (id == CONFIRM) {
            data.confirm(sp);
            return true;
        }
        int row = id - RECALL;
        if (row < 0 || row >= shown.rows().size()) {
            return false;
        }
        return data.beginFromRing(sp, shown.rows().get(row).binding());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** effects: valid while the player has their ring */
    @Override
    public boolean stillValid(Player player) {
        return Keyring.ringOf(player) != null;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp) {
            RecoveryData.get(sp.server).cancel(sp.getUUID());
        }
    }
}
