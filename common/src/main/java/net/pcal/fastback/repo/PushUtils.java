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

package net.pcal.fastback.repo;

import com.google.common.collect.ListMultimap;
import net.pcal.fastback.config.GitConfig;
import net.pcal.fastback.logging.UserLogger;
import net.pcal.fastback.utils.ProcessException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.ContentMergeStrategy;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static net.pcal.fastback.config.FastbackConfigKey.IS_NATIVE_GIT_ENABLED;
import static net.pcal.fastback.config.FastbackConfigKey.IS_REMOTE_TEMP_BRANCH_CLEANUP_ENABLED;
import static net.pcal.fastback.config.FastbackConfigKey.IS_SMART_PUSH_ENABLED;
import static net.pcal.fastback.config.FastbackConfigKey.IS_TEMP_BRANCH_CLEANUP_ENABLED;
import static net.pcal.fastback.config.FastbackConfigKey.IS_TRACKING_BRANCH_CLEANUP_ENABLED;
import static net.pcal.fastback.config.FastbackConfigKey.IS_UUID_CHECK_ENABLED;
import static net.pcal.fastback.config.FastbackConfigKey.REMOTE_NAME;
import static net.pcal.fastback.config.OtherConfigKey.REMOTE_PUSH_URL;
import static net.pcal.fastback.logging.SystemLogger.syslog;
import static net.pcal.fastback.logging.UserMessage.UserMessageStyle.ERROR;
import static net.pcal.fastback.logging.UserMessage.UserMessageStyle.JGIT;
import static net.pcal.fastback.logging.UserMessage.UserMessageStyle.NATIVE_GIT;
import static net.pcal.fastback.logging.UserMessage.styledLocalized;
import static net.pcal.fastback.logging.UserMessage.styledRaw;
import static net.pcal.fastback.mod.Mod.mod;
import static net.pcal.fastback.utils.ProcessUtils.doExec;

/**
 * Utils for pushing changes to a remote.
 *
 * @author pcal
 * @since 0.13.0
 */
abstract class PushUtils {

    static boolean isTempBranch(String branchName) {
        return branchName.startsWith("temp/");
    }

    // TODO stop throwing IOE
    static void doPush(SnapshotId sid, RepoImpl repo, UserLogger ulog) throws IOException, ProcessException {
        try {
            final GitConfig conf = repo.getConfig();
            final String pushUrl = conf.getString(REMOTE_PUSH_URL);
            if (pushUrl == null) {
                syslog().warn("Skipping remote backup because no remote url has been configured.");
                return;
            }
            final Git jgit = repo.getJGit();
            final Collection<Ref> remoteBranchRefs = jgit.lsRemote().setTransportConfigCallback(mod().getTransportConfigCallback()).setHeads(true).setTags(false).
                    setRemote(conf.getString(REMOTE_NAME)).call();
            final ListMultimap<WorldId, SnapshotId> snapshotsPerWorld =
                    SnapshotIdUtils.getSnapshotsPerWorld(remoteBranchRefs, repo.getSidCodec());
            if (conf.getBoolean(IS_UUID_CHECK_ENABLED)) {
                boolean uuidCheckResult;
                try {
                    uuidCheckResult = doWorldIdCheck(repo, snapshotsPerWorld.keySet());
                } catch (final IOException e) {
                    syslog().error("Unexpected exception thrown during id check", e);
                    uuidCheckResult = false;
                }
                if (!uuidCheckResult) {
                    final URIish remoteUri = getRemoteUri(repo.getJGit(), repo.getConfig().getString(REMOTE_NAME));
                    ulog.message(styledLocalized("fastback.chat.push-id-mismatch", ERROR, remoteUri));
                    syslog().error("Failing remote backup due to failed id check");
                    throw new IOException();
                }
            }
            syslog().debug("Pushing to " + pushUrl);

            PreflightUtils.doPreflight(repo);
            if (conf.getBoolean(IS_NATIVE_GIT_ENABLED)) {
                native_doPush(repo, sid.getBranchName(), ulog);
            } else if (conf.getBoolean(IS_SMART_PUSH_ENABLED)) {
                final WorldId uuid = repo.getWorldId();
                jgit_doSmartPush(repo, snapshotsPerWorld.get(uuid), sid.getBranchName(), conf, ulog);
            } else {
                jgit_doPush(jgit, sid.getBranchName(), conf, ulog);
            }
            syslog().info("Remote backup complete.");
        } catch (GitAPIException e) {
            throw new IOException(e);
        }
    }

    private static void native_doPush(final Repo repo, final String branchNameToPush, final UserLogger log) throws ProcessException {
        syslog().debug("Start native_push");
        log.update(styledLocalized("fastback.chat.push-started", NATIVE_GIT));
        final File worktree = repo.getWorkTree();
        final GitConfig conf = repo.getConfig();
        String remoteName = conf.getString(REMOTE_NAME);
        final String[] fetch = {mod().getGitExecutable().getAbsolutePath(), "-C", worktree.getAbsolutePath(), "fetch", "--all"};
        final String[] push = {mod().getGitExecutable().getAbsolutePath(), "-C", worktree.getAbsolutePath(), "-c", "push.autosetupremote=false", "push", "--progress", "--set-upstream", remoteName, branchNameToPush};
        final Map<String, String> env = Map.of("GIT_LFS_FORCE_PROGRESS", "1");
        final Consumer<String> outputConsumer = line->log.update(styledRaw(line, NATIVE_GIT));
        doExec(fetch, env, outputConsumer, outputConsumer);
        doExec(push, env, outputConsumer, outputConsumer);
        syslog().debug("End native_push");
    }


    private static void jgit_doPush(final Git jgit, final String branchNameToPush, final GitConfig conf, final UserLogger ulog) throws GitAPIException {
        final ProgressMonitor pm = new JGitIncrementalProgressMonitor(new JGitPushProgressMonitor(ulog), 100);
        final String remoteName = conf.getString(REMOTE_NAME);
        syslog().info("Doing simple push of " + branchNameToPush);
        jgit.push().setTransportConfigCallback(mod().getTransportConfigCallback()).setProgressMonitor(pm).setRemote(remoteName).
                setRefSpecs(new RefSpec(branchNameToPush + ":" + branchNameToPush)).call();
    }

    /**
     * This is a probably-failed attempt at an optimization.  It is no longer the default behavior.
     *
     * The idea was to try to minimize re-pushing unchanged blobs by establishing a common merge history between the
     * branch being pushed and one we already know is upstream Again, all snapshot branches are orphan branches, and
     * git can't bother checking every single blob in unrelated branches when receiving a push.
     *
     * But it does do some of this when there is a related history, so the idea here is to create a temporary merge
     * commit between a branch the server has and the new one it doesn't have, and to then let it figure out that most
     * of the blobs in those commits are already present on the server.   Unfortunately, I think I may misunderstand
     * how git behaves here - I think the deduplication is only at commit granularity, so this actually isn't doing
     * anything except adding a lot of moving parts and problems like this one:
     *
     * https://github.com/pcal43/fastback/issues/267
     *
     * So, this is no longer enabled by default.  And really, if they're backing up a big world where this matters,
     * you should just be using native git.
     */
    private static void jgit_doSmartPush(final RepoImpl repo, List<SnapshotId> remoteSnapshots, final String branchNameToPush, final GitConfig conf, final UserLogger ulog) throws IOException {
        ulog.update(styledLocalized("fastback.chat.push-started", JGIT));
        try {
            final Git jgit = repo.getJGit();
            final String remoteName = conf.getString(REMOTE_NAME);
            final WorldId worldUuid = repo.getWorldId();
            final SnapshotId latestCommonSnapshot;
            if (remoteSnapshots.isEmpty()) {
                syslog().warn("** This appears to be the first time this world has been pushed.");
                syslog().warn("** If the world is large, this may take some time.");
                jgit_doPush(jgit, branchNameToPush, conf, ulog);
                return;
            } else {
                final Collection<Ref> localBranchRefs = jgit.branchList().call();
                final ListMultimap<WorldId, SnapshotId> localSnapshotsPerWorld =
                        SnapshotIdUtils.getSnapshotsPerWorld(localBranchRefs, repo.getSidCodec());
                final List<SnapshotId> localSnapshots = localSnapshotsPerWorld.get(worldUuid);
                remoteSnapshots.retainAll(localSnapshots);
                if (remoteSnapshots.isEmpty()) {
                    syslog().warn("No common snapshots found between local and remote.");
                    syslog().warn("Doing a full push.  This may take some time.");
                    jgit_doPush(jgit, branchNameToPush, conf, ulog);
                    return;
                } else {
                    Collections.sort(remoteSnapshots);
                    latestCommonSnapshot = remoteSnapshots.get(remoteSnapshots.size() - 1);
                    syslog().debug("Using existing snapshot " + latestCommonSnapshot + " for common history");
                }
            }
            // ok, we have a common snapshot that we can use to create a fake merge history.
            final String tempBranchName = "temp/" + branchNameToPush;
            syslog().debug("Creating out temp branch " + tempBranchName);
            jgit.checkout().setCreateBranch(true).setName(tempBranchName).call();
            final ObjectId branchId = jgit.getRepository().resolve(latestCommonSnapshot.getBranchName());
            syslog().debug("Merging " + latestCommonSnapshot.getBranchName());
            jgit.merge().setContentMergeStrategy(ContentMergeStrategy.OURS).
                    include(branchId).setMessage("Merge " + branchId + " into " + tempBranchName).call();
            syslog().debug("Checking out " + branchNameToPush);
            jgit.checkout().setName(branchNameToPush).call();
            syslog().debug("Pushing temp branch " + tempBranchName);
            final ProgressMonitor pm = new JGitIncrementalProgressMonitor(new JGitPushProgressMonitor(ulog), 100);
            final Iterable<PushResult> pushResult = jgit.push().setTransportConfigCallback(mod().getTransportConfigCallback()).setProgressMonitor(pm).setRemote(remoteName).
                    setRefSpecs(new RefSpec(tempBranchName + ":" + tempBranchName),
                            new RefSpec(branchNameToPush + ":" + branchNameToPush)).call();
            syslog().debug("Cleaning up branches...");
            if (conf.getBoolean(IS_TRACKING_BRANCH_CLEANUP_ENABLED)) {
                for (final PushResult pr : pushResult) {
                    for (final TrackingRefUpdate f : pr.getTrackingRefUpdates()) {
                        final String PREFIX = "refs/remotes/";
                        if (f.getLocalName().startsWith(PREFIX)) {
                            final String trackingBranchName = f.getLocalName().substring(PREFIX.length());
                            syslog().debug("Cleaning up tracking branch " + trackingBranchName);
                            jgit.branchDelete().setForce(true).setBranchNames(trackingBranchName).call();
                        } else {
                            syslog().warn("Ignoring unrecognized TrackingRefUpdate " + f.getLocalName());
                        }
                    }
                }
            }
            if (conf.getBoolean(IS_TEMP_BRANCH_CLEANUP_ENABLED)) {
                syslog().debug("Deleting local temp branch " + tempBranchName);
                jgit.branchDelete().setForce(true).setBranchNames(tempBranchName).call();
            }
            if (conf.getBoolean(IS_REMOTE_TEMP_BRANCH_CLEANUP_ENABLED)) {
                final String remoteTempBranch = "refs/heads/" + tempBranchName;
                syslog().debug("Deleting remote temp branch " + remoteTempBranch);
                final RefSpec deleteRemoteBranchSpec = new RefSpec().setSource(null).setDestination(remoteTempBranch);
                jgit.push().setTransportConfigCallback(mod().getTransportConfigCallback()).setProgressMonitor(pm).setRemote(remoteName).setRefSpecs(deleteRemoteBranchSpec).call();
            }
            syslog().info("Push complete");
        } catch (GitAPIException e) {
            throw new IOException(e);
        }
    }

    private static boolean doWorldIdCheck(RepoImpl repo, Set<WorldId> remoteWorldUuids) throws IOException {
        final WorldId localUuid = repo.getWorldId();
        if (remoteWorldUuids.size() > 2) {
            syslog().warn("Remote has more than one world-id.  This is unusual. " + remoteWorldUuids);
        }
        if (remoteWorldUuids.isEmpty()) {
            syslog().debug("Remote does not have any previously-backed up worlds.");
        } else {
            if (!remoteWorldUuids.contains(localUuid)) {
                syslog().debug("local: " + localUuid + ", remote: " + remoteWorldUuids);
                return false;
            }
        }
        syslog().debug("world-id check passed.");
        return true;
    }

    private static URIish getRemoteUri(Git jgit, String remoteName) throws IOException {
        requireNonNull(jgit);
        requireNonNull(remoteName);
        final List<RemoteConfig> remotes;
        try {
            remotes = jgit.remoteList().call();
        } catch (GitAPIException e) {
            throw new IOException(e);
        }
        for (final RemoteConfig remote : remotes) {
            syslog().debug("getRemoteUri " + remote);
            if (remote.getName().equals(remoteName)) {
                return remote.getPushURIs() != null && !remote.getURIs().isEmpty() ? remote.getURIs().get(0) : null;
            }
        }
        return null;
    }

    private static class JGitPushProgressMonitor extends JGitPercentageProgressMonitor {

        private final UserLogger ulog;

        public JGitPushProgressMonitor(UserLogger ulog) {
            this.ulog = requireNonNull(ulog);
        }

        @Override
        public void progressStart(String task) {
            syslog().debug(task);
        }

        @Override
        public void progressUpdate(String task, int percentage) {
            final String msg = task + " " + percentage + "%";
            syslog().debug(msg);
            ulog.update(styledRaw(msg, JGIT));
        }

        @Override
        public void progressDone(String task) {
            final String msg = "Done " + task; // FIXME i18n
            syslog().debug(msg);
            ulog.update(styledRaw(msg, JGIT));
        }

        @Override
        public void showDuration(boolean enabled) {
        }
    }
}
