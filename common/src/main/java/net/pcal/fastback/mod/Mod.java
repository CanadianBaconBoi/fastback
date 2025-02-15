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

package net.pcal.fastback.mod;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pcal.fastback.logging.UserMessage;
import org.eclipse.jgit.api.TransportConfigCallback;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Singleton that provides various mod-wide services.
 *
 * @author pcal
 * @since 0.1.0
 */
public interface Mod {
    UUID EMPTYUUID = new UUID(0L, 0L);

    static Mod mod() {
        return Singleton.INSTANCE;
    }

    /**
     * @return path to where snapshots should be restored.
     */
    Path getDefaultRestoresDir() throws IOException;

    /**
     * @return the version of the fastback mod.
     */
    String getModVersion();

    /**
     * Enables or disables world saving.
     */
    void setWorldSaveEnabled(boolean enabled);

    /**
     * Save the world.
     */
    void saveWorld();

    /**
     * If we're clientside and the user is looking at a MessageScreen, set the title.
     */
    void setMessageScreenText(UserMessage message);

    /**
     * Send a chat message to user.
     */
    void sendChat(UserMessage message, ServerCommandSource scs);

    /**
     * If on a dedicated server, broadcast a message to the chat window of all connected users.
     */
    void sendBroadcast(UserMessage message);

    /**
     * Set magical floating text.  You MUST call clearHudText
     */
    void setHudText(UserMessage message);

    /**
     * Set magical floating text for player.  You MUST call clearHudText
     */
    void setHudTextForPlayer(UserMessage message, ServerPlayerEntity player);

    /**
     * Remove the magical floating text.
     */
    void clearHudText();

    /**
     * @return path to the save directory of the currently-loaded world (aka the git worktree).
     */
    Path getWorldDirectory();

    /**
     * @return name of the currently-loaded world.
     */
    String getWorldName();

    /**
     * @return paths to backup when mods-backup enabled.
     */
    Collection<Path> getModsBackupPaths();

    /**
     * Add extra properties that will be stored in .fastback/backup.properties.
     */
    void addBackupProperties(Map<String, String> props);

    /**
     * @return file pointing to git executable
     */
    File getGitExecutable();

    /**
     * @return file pointing to git-lfs executable
     */
    File getGitLfsExecutable();

    /**
     * @return JGit transport config callback
     */
    TransportConfigCallback getTransportConfigCallback();

    class Singleton {
        private static Mod INSTANCE = null;

        public static void register(Mod mod) {
            requireNonNull(mod);
            if (INSTANCE != null) throw new IllegalStateException();
            Singleton.INSTANCE = mod;
        }
    }
}
