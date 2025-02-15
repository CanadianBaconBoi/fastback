/*
 * FastBack - Fast, incremental Minecraft backups powered by Git.
 * Copyright (C) 2022 pcal.net
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; If not, see <http://www.gnu.org/licenses/>.
 */

package net.pcal.fastback.mod.fabric;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pcal.fastback.logging.UserMessage;
import net.pcal.fastback.mod.MinecraftProvider;

import java.nio.file.Path;

/**
 * @author pcal
 * @since 0.1.0
 */
public class FabricServerProvider extends BaseFabricProvider {

    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    public Path getSavesDir() {
        return null;
    }

    @Override
    public void setHudText(UserMessage userMessage) {
    }

    @Override
    public void setHudTextForPlayer(UserMessage userMessage, ServerPlayerEntity player) {
        player.sendMessage(MinecraftProvider.messageToText(userMessage), true);
    }

    @Override
    public void clearHudText() {

    }

    @Override
    public void setMessageScreenText(UserMessage userMessage) {

    }

    @Override
    public void renderMessageScreen(MatrixStack matrixStack, float tickDelta) {

    }
}
