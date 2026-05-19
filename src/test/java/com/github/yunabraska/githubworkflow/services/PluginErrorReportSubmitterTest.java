package com.github.yunabraska.githubworkflow.services;

import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import org.junit.Test;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginErrorReportSubmitterTest {

    @Test
    public void submitEmptyEventArrayFailsExplicitly() {
        final PluginErrorReportSubmitter submitter = new PluginErrorReportSubmitter();
        final AtomicReference<SubmittedReportInfo> reportInfo = new AtomicReference<>();

        final boolean submitted = submitter.submit(new IdeaLoggingEvent[0], "", new JPanel(), reportInfo::set);

        assertThat(submitted).isFalse();
        assertThat(reportInfo.get()).isNotNull();
        assertThat(reportInfo.get().getStatus()).isEqualTo(SubmittedReportInfo.SubmissionStatus.FAILED);
    }
}
