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

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pcal.fastback.logging.UserMessage;

import java.nio.file.Path;

import static net.pcal.fastback.logging.SystemLogger.syslog;
import static net.pcal.fastback.mod.MinecraftProvider.messageToText;

/**
 * @author pcal
 * @since 0.1.0
 */
final class FabricClientProvider extends BaseFabricProvider implements HudRenderCallback {

    // ======================================================================
    // Constants

    private static final long TEXT_TIMEOUT = 10 * 1000;

    // ======================================================================
    // Fields

    private MinecraftClient client = null;
    private Text hudText;
    private long hudTextTime;

    // ====================================================================
    // Public methods

    public void setMinecraftClient(MinecraftClient client) {
        if ((this.client == null) == (client == null)) throw new IllegalStateException();
        this.client = client;
    }

    // ======================================================================
    // MixinGateway implementation

    @Override
    public void renderMessageScreen(MatrixStack matrixStack, float tickDelta) {
        onHudRender(matrixStack, tickDelta);
    }

    // ====================================================================
    // FrameworkProvider implementation

    @Override
    public boolean isClient() {
        return true;
    }

    @Override
    public void setHudText(UserMessage userMessage) {
        if (userMessage == null) {
            clearHudText();
        } else {
            this.hudText = messageToText(userMessage); // so the hud renderer can find it
            this.hudTextTime = System.currentTimeMillis();
        }
    }

    @Override
    public void setHudTextForPlayer(UserMessage userMessage, ServerPlayerEntity player) {

    }

    @Override
    public void clearHudText() {
        this.hudText = null;
        // TODO someday it might be nice to bring back the fading text effect.  But getting to it properly
        // clean up 100% of the time is more than I want to deal with right now.
    }

    @Override
    public void setMessageScreenText(UserMessage userMessage) {
        client.player.sendMessage(messageToText(userMessage), true);
    }

    @Override
    public Path getSavesDir() {
        return FabricLoader.getInstance().getGameDir().resolve("saves");
    }

    // ====================================================================
    // HudRenderCallback implementation
    @Override
    public void onHudRender(MatrixStack matrixStack, float tickDelta) {
        if (this.hudText == null) return;
        if (!this.client.options.showAutosaveIndicator) return;
        if (System.currentTimeMillis() - this.hudTextTime > TEXT_TIMEOUT) {
            // Don't leave it sitting up there forever if we fail to call clearHudText()
            this.hudText = null;
            syslog().debug("hud text timed out.  somebody forgot to clean up");
            return;
        }
        client.player.sendMessage(this.hudText, true);
        //DrawableHelper.drawTextWithShadow(matrixStack, this.client.textRenderer, this.hudText, 2, 2, 1);
    }
}
