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

package net.pcal.fastback.tasks;

import net.pcal.fastback.ModContext;
import net.pcal.fastback.logging.IncrementalProgressMonitor;
import net.pcal.fastback.logging.Logger;
import net.pcal.fastback.logging.LoggingProgressMonitor;
import net.pcal.fastback.utils.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.storage.pack.PackConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;

import static java.util.Objects.requireNonNull;
import static net.pcal.fastback.logging.Message.localized;
import static net.pcal.fastback.utils.FileUtils.getDirDisplaySize;

/**
 * Runs git garbage collection.
 *
 * @author pcal
 * @since 0.1.5
 */
public class GcTask extends Task {

    private final ModContext ctx;
    private final Logger log;
    private final Git git;


    public GcTask(final Git git,
                  final ModContext ctx,
                  final Logger log) {
        this.git = requireNonNull(git);
        this.ctx = requireNonNull(ctx);
        this.log = requireNonNull(log);
    }

    public void run() {
        this.setStarted();
        final ProgressMonitor pm =
                new IncrementalProgressMonitor(new LoggingProgressMonitor(log), 100);
        try {
            log.notify(localized("fastback.notify.gc-start"));
            log.info("Stats before gc:");
            log.info("" + git.gc().getStatistics());
            //
            // reflogs aren't very useful in our case and cause old snapshots to get retained
            // longer than people expect.
            //
            final File gitDir = git.getRepository().getDirectory();
            log.notify(localized("fastback.notify.gc-size-before", getDirDisplaySize(gitDir)));
            if (ctx.isReflogDeletionEnabled()) {
                final Path reflogsDir = gitDir.toPath().resolve("logs");
                log.info("Deleting reflogs " + reflogsDir);
                FileUtils.rmdir(reflogsDir);
            }
            final GC gc = new GC(((FileRepository) git.getRepository()));
            gc.setExpireAgeMillis(0);
            gc.setPackExpireAgeMillis(0);
            gc.setAuto(false);
            PackConfig pc = new PackConfig();
            pc.setCompressionLevel(0);
            pc.setDeltaCompress(false);
            gc.setPackConfig(pc);
            gc.gc();
            log.notify(localized("fastback.notify.gc-done"));
            log.info("Stats after gc:");
            log.info("" + git.gc().getStatistics());
            log.notify(localized("fastback.notify.gc-size-after", getDirDisplaySize(gitDir)));
        } catch (IOException | GitAPIException | ParseException e) {
            this.setFailed();
            log.internalError("Failed to gc", e);
            return;
        }
        this.setCompleted();
    }
}
