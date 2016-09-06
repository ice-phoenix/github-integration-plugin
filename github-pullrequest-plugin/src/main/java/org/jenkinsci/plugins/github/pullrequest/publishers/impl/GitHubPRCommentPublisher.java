package org.jenkinsci.plugins.github.pullrequest.publishers.impl;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Api;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.github.pullrequest.GitHubPRMessage;
import org.jenkinsci.plugins.github.pullrequest.GitHubPRTrigger;
import org.jenkinsci.plugins.github.pullrequest.publishers.GitHubPRAbstractPublisher;
import org.jenkinsci.plugins.github.pullrequest.utils.PublisherErrorHandler;
import org.jenkinsci.plugins.github.pullrequest.utils.StatusVerifier;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.jenkinsci.plugins.github.pullrequest.utils.JobHelper.triggerFrom;
import static org.jenkinsci.plugins.github.pullrequest.utils.ObjectsUtil.isNull;

/**
 * Adds specified text to comments after build.
 *
 * @author Alina Karpovich
 * @author Kanstantsin Shautsou
 */
public class GitHubPRCommentPublisher extends GitHubPRAbstractPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubPRCommentPublisher.class);

    private GitHubPRMessage comment = new GitHubPRMessage("Build ${BUILD_NUMBER} ${BUILD_RESULT}");

    /**
     * Constructor with defaults. Only for groovy UI.
     */
    @Restricted(NoExternalUse.class)
    public GitHubPRCommentPublisher() {
        super(null, null);
    }

    @DataBoundConstructor
    public GitHubPRCommentPublisher(GitHubPRMessage comment,
                                    StatusVerifier statusVerifier,
                                    PublisherErrorHandler errorHandler) {
        super(statusVerifier, errorHandler);
        this.comment = comment;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws InterruptedException, IOException {

        if (getStatusVerifier() != null && !getStatusVerifier().isRunAllowed(run)) {
            return;
        }

        final String publishedURL = getTriggerDescriptor().getJenkinsURL();

        if (isEmpty(publishedURL)) return;

        final int pullRequestNumber = getNumber(run);
        final String message = comment.expandAll(run, listener);

        final GitHubPRTrigger trigger = triggerFrom(run.getParent(), GitHubPRTrigger.class);
        if (isNull(trigger)) {
            listener.error("Couldn't get trigger for this run! Skipping...");
            handlePublisherError(run);
            return;
        }

        if (message != null && !message.isEmpty()) {
            try {
                trigger.getRemoteRepo().getPullRequest(pullRequestNumber).comment(message);
            } catch (IOException ex) {
                listener.error("Couldn't add comment to pull request #{}: '{}'", pullRequestNumber, message, ex);
                handlePublisherError(run);
                return;
            }
        }
    }

    public final Api getApi() {
        return new Api(this);
    }

    public GitHubPRMessage getComment() {
        return comment;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptor(GitHubPRCommentPublisher.class);
    }

    @Extension
    public static class DescriptorImpl extends GitHubPRAbstractPublisher.DescriptorImpl {
        @Override
        public String getDisplayName() {
            return "GitHub PR: post comment";
        }
    }
}
