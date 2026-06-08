package com.github.yunabraska.githubworkflow.run;

import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;

import com.intellij.execution.Executor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.content.Content;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.UIManager;

/**
 * Adds a JUnit-style workflow tree to the Run tool window and routes selected-node output to one detail console.
 */
final class WorkflowRunView implements WorkflowRunProcessHandler.JobConsole {

    private static final int MAX_ATTACH_ATTEMPTS = 20;
    private static final String CONTENT_ID = "github.workflow.jobs";
    private static final String DEFAULT_CONSOLE_TITLE = "Console";

    private final Project project;
    private final @Nullable Executor executor;
    private final WorkflowRunProcessHandler processHandler;
    private final WorkflowNode workflow = new WorkflowNode();
    private final ConcurrentMap<Long, JobNode> jobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, GroupNode> groups = new ConcurrentHashMap<>();
    private final Object attachLock = new Object();
    private final AtomicBoolean attaching = new AtomicBoolean(false);
    private final AtomicInteger attachAttempts = new AtomicInteger(0);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ProcessListener processListener = new ProcessListener() {
        @Override
        public void onTextAvailable(final @NotNull ProcessEvent event, final @NotNull Key outputType) {
            workflow.print(event.getText(), processContentType(outputType));
            scheduleAttach();
        }
    };
    private volatile TreeEntry selectedEntry = workflow;
    private @Nullable ConsoleView detailConsole;
    private @Nullable Content content;
    private @Nullable DefaultTreeModel treeModel;
    private @Nullable DefaultMutableTreeNode rootNode;
    private @Nullable Tree tree;
    private @Nullable JProgressBar progressBar;
    private @Nullable RunnerLayoutUi runnerLayoutUi;
    private @Nullable Timer animationTimer;
    private final AtomicBoolean deleteInProgress = new AtomicBoolean(false);
    private volatile long terminalRunId = -1;
    private volatile String terminalConclusion = "";

    WorkflowRunView(final Project project, final @Nullable Executor executor, final WorkflowRunProcessHandler processHandler) {
        this.project = project;
        this.executor = executor;
        this.processHandler = processHandler;
        this.processHandler.addProcessListener(processListener);
    }

    @Override
    public boolean jobStatus(final WorkflowRun.JobStatus job, final String text) {
        return print(job, text, ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    @Override
    public boolean jobStdout(final WorkflowRun.JobStatus job, final String text) {
        return print(job, text, ConsoleViewContentType.NORMAL_OUTPUT);
    }

    @Override
    public boolean jobLog(final WorkflowRun.JobStatus job, final String text) {
        if (executor == null || job.id() < 0) {
            return false;
        }
        final JobNode node = jobNode(job);
        node.update(job);
        node.printLog(text);
        scheduleAttach();
        return true;
    }

    @Override
    public boolean jobStderr(final WorkflowRun.JobStatus job, final String text) {
        return print(job, text, ConsoleViewContentType.ERROR_OUTPUT);
    }

    @Override
    public void workflowStatus(final String text, final boolean error) {
        workflow.print(text, error ? ConsoleViewContentType.ERROR_OUTPUT : ConsoleViewContentType.SYSTEM_OUTPUT);
        scheduleAttach();
    }

    @Override
    public void runFinished(final long runId, final String conclusion) {
        terminalRunId = runId;
        terminalConclusion = normalizeConclusion(conclusion);
        jobs.values().forEach(job -> job.finish(terminalConclusion));
        processHandler.refreshArtifactAvailability(ignored ->
                ApplicationManager.getApplication().invokeLater(this::updateContent));
        refreshTree();
        ApplicationManager.getApplication().invokeLater(this::stopAnimationTimer);
    }

    @Override
    public void runDeleted(final long runId) {
        ApplicationManager.getApplication().invokeLater(this::closeRunContent);
    }

    @Override
    public void runDeleteFailed(final long runId) {
        if (terminalRunId == runId) {
            deleteInProgress.set(false);
            ApplicationManager.getApplication().invokeLater(this::updateContent);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            processHandler.removeProcessListener(processListener);
            ApplicationManager.getApplication().invokeLater(this::stopAnimationTimer);
        }
    }

    private boolean print(final WorkflowRun.JobStatus job, final String text, final ConsoleViewContentType contentType) {
        if (executor == null || job.id() < 0) {
            return false;
        }
        final JobNode node = jobNode(job);
        node.print(job, text, contentType);
        scheduleAttach();
        return true;
    }

    private Optional<RunContentDescriptor> descriptor() {
        if (project.isDisposed() || executor == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(RunContentManager.getInstance(project).findContentDescriptor(executor, processHandler));
    }

    private JobNode jobNode(final WorkflowRun.JobStatus job) {
        return jobs.computeIfAbsent(job.id(), ignored -> {
            final JobNode node = new JobNode(job);
            ApplicationManager.getApplication().invokeLater(() -> addJobNode(node));
            return node;
        });
    }

    private void scheduleAttach() {
        if (!attaching.compareAndSet(false, true)) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(this::attach);
    }

    private void attach() {
        final Optional<RunContentDescriptor> descriptor = descriptor();
        if (descriptor.isEmpty() || descriptor.get().getRunnerLayoutUi() == null) {
            retry();
            return;
        }

        final RunnerLayoutUi layout = descriptor.get().getRunnerLayoutUi();
        final Content existing = layout.findContent(CONTENT_ID);
        if (existing != null) {
            synchronized (attachLock) {
                content = existing;
            }
            keepOnlyWorkflowContent(layout, existing);
            refreshTree();
            return;
        }

        final ConsoleView createdConsole = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project)
                .getConsole();
        final DefaultMutableTreeNode createdRoot = new DefaultMutableTreeNode(workflow);
        final DefaultTreeModel createdModel = new DefaultTreeModel(createdRoot);
        final Tree createdTree = new Tree(createdModel);
        createdTree.setRootVisible(true);
        createdTree.setShowsRootHandles(true);
        createdTree.setCellRenderer(new JobTreeCellRenderer());
        createdTree.addTreeSelectionListener(event -> selectEntry(event.getPath()));

        final JProgressBar createdProgress = new JProgressBar();
        createdProgress.setBorder(JBUI.Borders.empty());
        createdProgress.setStringPainted(false);
        createdProgress.setPreferredSize(new Dimension(0, JBUI.scale(3)));

        layout.getOptions().setTopRightToolbar(runToolbarActions(), "GitHubWorkflowRunToolbar");

        final JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.add(createdProgress, BorderLayout.CENTER);

        final JPanel detailPanel = new JPanel(new BorderLayout());
        detailPanel.add(progressPanel, BorderLayout.NORTH);
        detailPanel.add(createdConsole.getComponent(), BorderLayout.CENTER);

        final OnePixelSplitter splitter = new OnePixelSplitter(false, 0.32f);
        splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(createdTree));
        splitter.setSecondComponent(detailPanel);

        final Content createdContent = layout.createContent(
                CONTENT_ID,
                splitter,
                GitHubWorkflowBundle.message("workflow.run.jobs.root"),
                workflow.icon(),
                focusTarget(createdConsole)
        );
        createdContent.setDescription(GitHubWorkflowBundle.message("workflow.run.jobs.description"));
        createdContent.setCloseable(false);
        if (createdConsole instanceof Disposable disposable) {
            createdContent.setDisposer(disposable);
        }
        layout.addContent(createdContent);
        keepOnlyWorkflowContent(layout, createdContent);
        synchronized (attachLock) {
            content = createdContent;
            detailConsole = createdConsole;
            rootNode = createdRoot;
            treeModel = createdModel;
            tree = createdTree;
            progressBar = createdProgress;
            runnerLayoutUi = layout;
        }
        refreshTree();
        createdTree.setSelectionPath(new TreePath(createdRoot.getPath()));
        replay(workflow);
        updateAnimationTimer();
    }

    private static JComponent focusTarget(final ConsoleView console) {
        return console.getPreferredFocusableComponent() instanceof JComponent component ? component : console.getComponent();
    }

    private void keepOnlyWorkflowContent(final RunnerLayoutUi layout, final Content workflowContent) {
        for (final Content candidate : layout.getContents()) {
            if (candidate != workflowContent && DEFAULT_CONSOLE_TITLE.equals(candidate.getDisplayName())) {
                layout.removeContent(candidate, false);
            }
        }
        layout.selectAndFocus(workflowContent, false, true);
    }

    private void closeRunContent() {
        if (project.isDisposed() || executor == null) {
            return;
        }
        descriptor().ifPresent(descriptor -> RunContentManager.getInstance(project).removeRunContent(executor, descriptor));
    }

    private void addJobNode(final JobNode node) {
        final DefaultMutableTreeNode root = rootNode;
        final DefaultTreeModel model = treeModel;
        if (root == null || model == null || node.treeNode != null || project.isDisposed()) {
            return;
        }
        final DefaultMutableTreeNode parent = node.groupName().isBlank()
                ? root
                : groupTreeNode(node.groupName(), root, model);
        node.treeNode = new DefaultMutableTreeNode(node);
        model.insertNodeInto(node.treeNode, parent, parent.getChildCount());
        final Tree currentTree = tree;
        if (currentTree != null) {
            currentTree.expandPath(new TreePath(root.getPath()));
            currentTree.expandPath(new TreePath(parent.getPath()));
        }
        refreshTree();
    }

    private DefaultMutableTreeNode groupTreeNode(
            final String groupName,
            final DefaultMutableTreeNode root,
            final DefaultTreeModel model
    ) {
        final GroupNode group = groups.computeIfAbsent(groupName, GroupNode::new);
        if (group.treeNode == null) {
            group.treeNode = new DefaultMutableTreeNode(group);
            model.insertNodeInto(group.treeNode, root, root.getChildCount());
        }
        return group.treeNode;
    }

    private void refreshTree() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            jobs.values().forEach(this::addJobNode);
            final DefaultTreeModel model = treeModel;
            final DefaultMutableTreeNode root = rootNode;
            if (model != null && root != null) {
                model.nodeChanged(root);
                groups.values().stream()
                        .map(group -> group.treeNode)
                        .filter(node -> node != null)
                        .forEach(model::nodeChanged);
                jobs.values().stream()
                        .map(job -> job.treeNode)
                        .filter(node -> node != null)
                        .forEach(model::nodeChanged);
            }
            updateContent();
        });
    }

    private void selectEntry(final TreePath path) {
        final Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode treeNode) || !(treeNode.getUserObject() instanceof TreeEntry entry)) {
            selectedEntry = workflow;
            replay(workflow);
            updateContent();
            return;
        }
        selectedEntry = entry;
        replay(entry);
        updateContent();
    }

    private void replay(final TreeEntry entry) {
        final ConsoleView console = detailConsole;
        if (console == null || project.isDisposed()) {
            return;
        }
        console.clear();
        entry.snapshot().forEach(text -> console.print(text.text(), text.contentType()));
    }

    private void retry() {
        if (project.isDisposed() || attachAttempts.incrementAndGet() >= MAX_ATTACH_ATTEMPTS) {
            jobs.values().forEach(JobNode::clear);
            return;
        }
        attaching.set(false);
        AppExecutorUtil.getAppScheduledExecutorService().schedule(this::scheduleAttach, 250, TimeUnit.MILLISECONDS);
    }

    private void updateContent() {
        final Content current = content;
        final JProgressBar progress = progressBar;
        if (current == null) {
            return;
        }
        final Icon nextIcon = workflow.icon();
        ApplicationManager.getApplication().invokeLater(() -> {
            final Content nextContent = content;
            if (nextContent != null && !project.isDisposed()) {
                nextContent.setIcon(nextIcon);
            }
            if (progress != null && !project.isDisposed()) {
                final int total = jobs.size();
                final int completed = (int) jobs.values().stream().filter(JobNode::completed).count();
                progress.setIndeterminate(total == 0 && !terminal());
                progress.setMaximum(Math.max(1, total));
                progress.setValue(terminal() ? Math.max(1, total) : Math.min(completed, Math.max(1, total)));
                progress.setVisible(total > 0 || !terminal());
                progress.setForeground(progressColor());
            }
            final RunnerLayoutUi layout = runnerLayoutUi;
            if (layout != null && !project.isDisposed()) {
                layout.updateActionsNow();
            }
            updateAnimationTimer();
        });
    }

    private DefaultActionGroup runToolbarActions() {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(new ToolbarAction(
                GitHubWorkflowBundle.message("workflow.run.rerun.all.tooltip"),
                AllIcons.Actions.Rerun,
                this::canRerunAll,
                () -> processHandler.rerunRemoteRun(false)
        ));
        group.add(new ToolbarAction(
                GitHubWorkflowBundle.message("workflow.run.rerun.failed.tooltip"),
                AllIcons.Actions.Restart,
                this::canRerunFailed,
                () -> processHandler.rerunRemoteRun(true)
        ));
        group.addSeparator();
        group.add(new ToolbarAction(
                GitHubWorkflowBundle.message("workflow.run.download.log.tooltip"),
                AllIcons.Nodes.Console,
                this::canDownloadSelectedLog,
                () -> {
                    final TreeEntry entry = selectedEntry;
                    if (entry instanceof JobNode job) {
                        processHandler.downloadJobLog(job.jobId(), job.title());
                    }
                }
        ));
        group.add(new ToolbarAction(
                GitHubWorkflowBundle.message("workflow.run.download.artifacts.tooltip"),
                AllIcons.Actions.Download,
                this::canDownloadArtifacts,
                processHandler::downloadArtifacts
        ));
        group.addSeparator();
        group.add(new ToolbarAction(
                GitHubWorkflowBundle.message("workflow.run.delete.tooltip"),
                AllIcons.General.Delete,
                this::canDeleteRun,
                () -> {
                    if (deleteInProgress.compareAndSet(false, true)) {
                        processHandler.deleteRemoteRun();
                    }
                }
        ));
        return group;
    }

    private boolean canRerunAll() {
        return terminalRunId > 0 && terminal();
    }

    private boolean canRerunFailed() {
        return terminalRunId > 0 && terminal() && jobs.values().stream().anyMatch(JobNode::failed);
    }

    private boolean canDownloadSelectedLog() {
        return terminalRunId > 0
                && selectedEntry instanceof JobNode job
                && job.downloadableLog();
    }

    private boolean canDownloadArtifacts() {
        return terminalRunId > 0 && processHandler.artifactAvailability() == 1;
    }

    private boolean canDeleteRun() {
        return terminalRunId > 0 && terminal() && !deleteInProgress.get();
    }

    private Color progressColor() {
        if (!terminal()) {
            return uiColor("ProgressBar.foreground", new Color(0x4A88C7));
        }
        return successful(terminalConclusion)
                ? uiColor("Actions.Green", new Color(0x59A869))
                : uiColor("Actions.Red", new Color(0xDB5860));
    }

    private static Color uiColor(final String key, final Color fallback) {
        final Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }

    private void updateAnimationTimer() {
        if (shouldAnimate()) {
            startAnimationTimer();
        } else {
            stopAnimationTimer();
        }
    }

    private void startAnimationTimer() {
        if (animationTimer != null) {
            return;
        }
        final Timer timer = new Timer(125, ignored -> {
            if (!shouldAnimate()) {
                stopAnimationTimer();
                return;
            }
            final Content currentContent = content;
            if (currentContent != null) {
                currentContent.setIcon(workflow.icon());
            }
            final Tree currentTree = tree;
            if (currentTree != null) {
                currentTree.repaint();
            }
        });
        timer.setRepeats(true);
        timer.start();
        animationTimer = timer;
    }

    private void stopAnimationTimer() {
        final Timer timer = animationTimer;
        if (timer != null) {
            timer.stop();
            animationTimer = null;
        }
    }

    private boolean shouldAnimate() {
        return !closed.get()
                && !project.isDisposed()
                && !processHandler.isProcessTerminated()
                && !terminal()
                && (jobs.isEmpty() || jobs.values().stream().anyMatch(JobNode::running));
    }

    private static ConsoleViewContentType processContentType(final Key outputType) {
        return ProcessOutputTypes.STDERR.equals(outputType)
                ? ConsoleViewContentType.ERROR_OUTPUT
                : ConsoleViewContentType.SYSTEM_OUTPUT;
    }

    private static ConsoleViewContentType contentType(final LogRenderer.Kind kind) {
        return switch (kind) {
            case SYSTEM -> ConsoleViewContentType.SYSTEM_OUTPUT;
            case WARNING -> ConsoleViewContentType.LOG_WARNING_OUTPUT;
            case ERROR -> ConsoleViewContentType.LOG_ERROR_OUTPUT;
            case NORMAL -> ConsoleViewContentType.NORMAL_OUTPUT;
        };
    }

    private Icon aggregateIcon(final List<JobNode> nodes, final boolean running, final Icon emptyIcon, final boolean cancelledRun) {
        if (running) {
            return AnimatedIcon.Default.INSTANCE;
        }
        if (cancelledRun || nodes.stream().anyMatch(JobNode::cancelled)) {
            return AllIcons.RunConfigurations.TestTerminated;
        }
        if (nodes.stream().anyMatch(JobNode::failed)) {
            return AllIcons.General.Error;
        }
        if (nodes.stream().anyMatch(JobNode::skipped)) {
            return AllIcons.RunConfigurations.TestState.Yellow2;
        }
        return nodes.isEmpty() ? emptyIcon : AllIcons.General.GreenCheckmark;
    }

    private boolean terminal() {
        return !terminalConclusion.isBlank();
    }

    static JobDisplayName splitJobName(final String name) {
        final String normalized = name == null || name.isBlank()
                ? GitHubWorkflowBundle.message("workflow.run.job.fallbackName", -1)
                : name;
        final int separator = normalized.indexOf(" / ");
        if (separator <= 0 || separator + 3 >= normalized.length()) {
            return new JobDisplayName("", normalized);
        }
        return new JobDisplayName(normalized.substring(0, separator), normalized.substring(separator + 3));
    }

    private static String displayBaseName(final WorkflowRun.JobStatus job) {
        return job.name() == null || job.name().isBlank()
                ? GitHubWorkflowBundle.message("workflow.run.job.fallbackName", job.id())
                : job.name();
    }

    private static boolean successful(final String conclusion) {
        return "success".equals(conclusion) || "skipped".equals(conclusion) || "neutral".equals(conclusion);
    }

    private static boolean cancelled(final String conclusion) {
        return "cancelled".equals(conclusion) || "canceled".equals(conclusion);
    }

    private static String normalizeConclusion(final String conclusion) {
        return conclusion == null || conclusion.isBlank()
                ? "failure"
                : conclusion.toLowerCase(Locale.ROOT);
    }

    private static String formatDuration(final long millis) {
        final long seconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(millis));
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    static final class LogRenderer {

        private static final Pattern TIMESTAMP = Pattern.compile("^\\x{FEFF}?\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z\\s+");
        private static final Pattern GITHUB_COMMAND = Pattern.compile("^##\\[([^]]+)](.*)$");
        private static final Pattern WORKFLOW_COMMAND = Pattern.compile("^::([^: ]+)(?: [^:]*)?::(.*)$");
        private static final Pattern ANSI_SGR = Pattern.compile("\\x1B\\[([0-9;]*)m");
        private static final Pattern ANSI_CONTROL = Pattern.compile("\\x1B\\[[0-?]*[ -/]*[@-~]");

        private int lineNumber = 0;
        private boolean printedAny = false;

        static List<Segment> renderOnce(final String text) {
            return new LogRenderer().render(text);
        }

        static String renderPlainOnce(final String text) {
            return new LogRenderer().renderPlain(text);
        }

        List<Segment> render(final String text) {
            if (text == null || text.isEmpty()) {
                return List.of();
            }
            final List<Segment> result = new ArrayList<>();
            int start = 0;
            while (start < text.length()) {
                final int next = nextLineEnd(text, start);
                appendLine(result, text.substring(start, next));
                start = next;
            }
            return List.copyOf(result);
        }

        String renderPlain(final String text) {
            final StringBuilder result = new StringBuilder();
            for (final Segment segment : render(text)) {
                result.append(segment.text());
            }
            return result.toString();
        }

        private void appendLine(final List<Segment> result, final String rawLine) {
            final LineParts parts = splitLine(rawLine);
            final AnsiLine ansiLine = stripAnsi(TIMESTAMP.matcher(parts.text()).replaceFirst(""));
            final String line = ansiLine.text();
            final Matcher githubCommand = GITHUB_COMMAND.matcher(line);
            if (githubCommand.matches()) {
                appendGitHubCommand(result, githubCommand.group(1), githubCommand.group(2), parts.separator());
                return;
            }
            final Matcher workflowCommand = WORKFLOW_COMMAND.matcher(line);
            if (workflowCommand.matches()) {
                appendWorkflowCommand(result, workflowCommand.group(1), workflowCommand.group(2), parts.separator());
                return;
            }
            appendNumbered(result, line, ansiLine.kind() == Kind.NORMAL ? inferredKind(line) : ansiLine.kind(), parts.separator());
        }

        private void appendGitHubCommand(final List<Segment> result, final String command, final String value, final String separator) {
            final String name = commandName(command);
            switch (name) {
                case "group" -> appendBlockHeader(result, value);
                case "endgroup", "/group" -> appendBlockEnd();
                case "command" -> appendNumbered(result, GitHubWorkflowBundle.message("workflow.log.command") + " " + value, Kind.SYSTEM, separator);
                case "warning" -> appendNumbered(result, GitHubWorkflowBundle.message("workflow.log.warning") + " " + value, Kind.WARNING, separator);
                case "error" -> appendNumbered(result, GitHubWorkflowBundle.message("workflow.log.error") + " " + value, Kind.ERROR, separator);
                default -> appendNumbered(result, value.isBlank() ? "[" + name + "]" : value, Kind.SYSTEM, separator);
            }
        }

        private void appendWorkflowCommand(final List<Segment> result, final String command, final String value, final String separator) {
            final String name = commandName(command);
            switch (name) {
                case "warning" -> appendNumbered(result, GitHubWorkflowBundle.message("workflow.log.warning") + " " + value, Kind.WARNING, separator);
                case "error" -> appendNumbered(result, GitHubWorkflowBundle.message("workflow.log.error") + " " + value, Kind.ERROR, separator);
                case "group" -> appendBlockHeader(result, value);
                case "endgroup", "/group" -> appendBlockEnd();
                default -> appendNumbered(result, value, Kind.SYSTEM, separator);
            }
        }

        private void appendBlockHeader(final List<Segment> result, final String title) {
            final String prefix = printedAny ? "\n" : "";
            result.add(new Segment(prefix + "== " + title.strip() + " ==\n", Kind.SYSTEM));
            lineNumber = 0;
            printedAny = true;
        }

        private void appendBlockEnd() {
            lineNumber = 0;
        }

        private void appendNumbered(final List<Segment> result, final String line, final Kind kind, final String separator) {
            printedAny = true;
            if (line.isBlank()) {
                result.add(new Segment(separator, kind));
                return;
            }
            lineNumber++;
            result.add(new Segment(String.format(Locale.ROOT, "%04d | %s%s", lineNumber, line, separator), kind));
        }

        private static AnsiLine stripAnsi(final String line) {
            Kind kind = Kind.NORMAL;
            final Matcher matcher = ANSI_SGR.matcher(line);
            while (matcher.find()) {
                kind = strongest(kind, kindForAnsi(matcher.group(1)));
            }
            return new AnsiLine(ANSI_CONTROL.matcher(line).replaceAll(""), kind);
        }

        private static Kind kindForAnsi(final String value) {
            final String[] codes = value.isBlank() ? new String[]{"0"} : value.split(";");
            Kind result = Kind.NORMAL;
            for (final String code : codes) {
                result = strongest(result, switch (code) {
                    case "31", "91" -> Kind.ERROR;
                    case "33", "93" -> Kind.WARNING;
                    case "34", "35", "36", "90", "94", "95", "96" -> Kind.SYSTEM;
                    default -> Kind.NORMAL;
                });
            }
            return result;
        }

        private static Kind strongest(final Kind left, final Kind right) {
            return weight(right) > weight(left) ? right : left;
        }

        private static int weight(final Kind kind) {
            return switch (kind) {
                case ERROR -> 3;
                case WARNING -> 2;
                case SYSTEM -> 1;
                case NORMAL -> 0;
            };
        }

        private static Kind inferredKind(final String line) {
            final String normalized = line.stripLeading().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("error:") || normalized.startsWith("fatal:")) {
                return Kind.ERROR;
            }
            if (normalized.startsWith("warning:") || normalized.startsWith("npm warn ")) {
                return Kind.WARNING;
            }
            return Kind.NORMAL;
        }

        private static String commandName(final String command) {
            final int space = command.indexOf(' ');
            return (space >= 0 ? command.substring(0, space) : command).toLowerCase(Locale.ROOT);
        }

        private static LineParts splitLine(final String line) {
            if (line.endsWith("\r\n")) {
                return new LineParts(line.substring(0, line.length() - 2), "\r\n");
            }
            if (line.endsWith("\n") || line.endsWith("\r")) {
                return new LineParts(line.substring(0, line.length() - 1), line.substring(line.length() - 1));
            }
            return new LineParts(line, "");
        }

        private static int nextLineEnd(final String text, final int start) {
            int index = start;
            while (index < text.length() && text.charAt(index) != '\n' && text.charAt(index) != '\r') {
                index++;
            }
            if (index >= text.length()) {
                return index;
            }
            if (text.charAt(index) == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                return index + 2;
            }
            return index + 1;
        }

        enum Kind {
            NORMAL,
            SYSTEM,
            WARNING,
            ERROR
        }

        record Segment(String text, Kind kind) {
        }

        private record AnsiLine(String text, Kind kind) {
        }

        private record LineParts(String text, String separator) {
        }
    }

    record JobDisplayName(String group, String name) {
    }

    private record JobState(
            long jobId,
            String groupName,
            String displayName,
            String status,
            String conclusion,
            long firstSeenMillis,
            long startedMillis,
            long completedMillis,
            int warnings,
            int errors
    ) {

        private static JobState from(final WorkflowRun.JobStatus job, final long now) {
            final JobDisplayName name = displayName(job);
            return new JobState(
                    job.id(),
                    name.group(),
                    name.name(),
                    job.status(),
                    normalizeConclusion(job.conclusion()),
                    now,
                    0,
                    0,
                    0,
                    0
            ).withTiming(job, now);
        }

        private JobState update(final WorkflowRun.JobStatus job, final long now) {
            final JobDisplayName name = displayName(job);
            return new JobState(
                    jobId,
                    name.group(),
                    name.name(),
                    job.status(),
                    normalizeConclusion(job.conclusion()),
                    firstSeenMillis,
                    startedMillis,
                    completedMillis,
                    warnings,
                    errors
            ).withTiming(job, now);
        }

        private JobState withDiagnostic(final boolean warning, final boolean error) {
            if (!warning && !error) {
                return this;
            }
            return new JobState(
                    jobId,
                    groupName,
                    displayName,
                    status,
                    conclusion,
                    firstSeenMillis,
                    startedMillis,
                    completedMillis,
                    warnings + (warning ? 1 : 0),
                    errors + (error ? 1 : 0)
            );
        }

        private JobState finish(final String runConclusion, final long now) {
            if (completed()) {
                return this;
            }
            final long firstSeen = firstSeenMillis == 0 ? now : firstSeenMillis;
            final long started = startedMillis == 0 ? firstSeen : startedMillis;
            return new JobState(
                    jobId,
                    groupName,
                    displayName,
                    "completed",
                    runConclusion,
                    firstSeen,
                    started,
                    now,
                    warnings,
                    errors
            );
        }

        private boolean completed() {
            return "completed".equals(status);
        }

        private boolean running() {
            return "in_progress".equals(status);
        }

        private boolean failed() {
            return completed() && !successful(conclusion) && !cancelled();
        }

        private boolean skipped() {
            return completed() && ("skipped".equals(conclusion) || "neutral".equals(conclusion));
        }

        private boolean cancelled() {
            return completed() && WorkflowRunView.cancelled(conclusion);
        }

        private String duration(final long now) {
            final long start = startedMillis > 0 ? startedMillis : firstSeenMillis;
            final long end = completedMillis > 0 ? completedMillis : now;
            if (start <= 0 || end < start) {
                return "";
            }
            return formatDuration(end - start);
        }

        private JobState withTiming(final WorkflowRun.JobStatus job, final long now) {
            final long firstSeen = firstSeenMillis == 0 ? now : firstSeenMillis;
            final long started = "in_progress".equals(job.status()) && startedMillis == 0 ? now : startedMillis;
            final long completed = "completed".equals(job.status()) && completedMillis == 0 ? now : completedMillis;
            return new JobState(
                    jobId,
                    groupName,
                    displayName,
                    status,
                    conclusion,
                    firstSeen,
                    started,
                    completed,
                    warnings,
                    errors
            );
        }

        private static JobDisplayName displayName(final WorkflowRun.JobStatus job) {
            return splitJobName(displayBaseName(job));
        }
    }

    private static final class ToolbarAction extends DumbAwareAction {
        private final String text;
        private final BooleanSupplier visible;
        private final Runnable command;

        private ToolbarAction(
                final String tooltip,
                final Icon icon,
                final BooleanSupplier visible,
                final Runnable command
        ) {
            super(tooltip, tooltip, icon);
            this.text = tooltip;
            this.visible = visible;
            this.command = command;
        }

        @Override
        public void actionPerformed(@NotNull final AnActionEvent event) {
            command.run();
        }

        @Override
        public void update(@NotNull final AnActionEvent event) {
            final Presentation presentation = event.getPresentation();
            final boolean available = visible.getAsBoolean();
            presentation.setText(text, false);
            presentation.setDescription(text);
            presentation.setEnabledAndVisible(available);
        }
    }

    private interface TreeEntry {
        String title();

        String suffix();

        Icon icon();

        List<PrintedText> snapshot();
    }

    private final class WorkflowNode implements TreeEntry {
        private final Object lock = new Object();
        private final List<PrintedText> output = new ArrayList<>();
        private final long startedMillis = System.currentTimeMillis();

        private void print(final String text, final ConsoleViewContentType contentType) {
            final PrintedText printedText = new PrintedText(text, contentType);
            synchronized (lock) {
                output.add(printedText);
            }
            printIfSelected(this, printedText);
            refreshTree();
        }

        @Override
        public String title() {
            return GitHubWorkflowBundle.message("workflow.run.jobs.root");
        }

        @Override
        public String suffix() {
            final int total = jobs.size();
            if (total == 0) {
                return "";
            }
            final long completed = jobs.values().stream().filter(JobNode::completed).count();
            final long failed = jobs.values().stream().filter(JobNode::failed).count();
            final long skipped = jobs.values().stream().filter(JobNode::skipped).count();
            final StringBuilder result = new StringBuilder()
                    .append(completed).append("/").append(total)
                    .append(" ").append(GitHubWorkflowBundle.message("workflow.run.tree.done"))
                    .append("  ").append(formatDuration(System.currentTimeMillis() - startedMillis));
            if (failed > 0) {
                result.append("  ").append(GitHubWorkflowBundle.message("workflow.run.tree.failed")).append(" ").append(failed);
            }
            if (skipped > 0) {
                result.append("  ").append(GitHubWorkflowBundle.message("workflow.run.tree.skipped")).append(" ").append(skipped);
            }
            return result.toString();
        }

        @Override
        public Icon icon() {
            final Icon empty = terminal() && !successful(terminalConclusion)
                    ? AllIcons.General.Error
                    : AllIcons.General.GreenCheckmark;
            return aggregateIcon(List.copyOf(jobs.values()), shouldAnimate(), empty, cancelled(terminalConclusion));
        }

        @Override
        public List<PrintedText> snapshot() {
            synchronized (lock) {
                return List.copyOf(output);
            }
        }

        private boolean completed() {
            return terminal() || (!jobs.isEmpty() && jobs.values().stream().allMatch(JobNode::completed));
        }
    }

    private final class GroupNode implements TreeEntry {
        private final String name;
        private @Nullable DefaultMutableTreeNode treeNode;

        private GroupNode(final String name) {
            this.name = name;
        }

        @Override
        public String title() {
            return name;
        }

        @Override
        public String suffix() {
            final List<JobNode> children = children();
            if (children.isEmpty()) {
                return "";
            }
            final long completed = children.stream().filter(JobNode::completed).count();
            final long warnings = children.stream().mapToLong(JobNode::warnings).sum();
            final long errors = children.stream().mapToLong(JobNode::errors).sum();
            final StringBuilder result = new StringBuilder()
                    .append(completed).append("/").append(children.size())
                    .append(" ").append(GitHubWorkflowBundle.message("workflow.run.tree.done"));
            if (warnings > 0) {
                result.append("  ").append(GitHubWorkflowBundle.message("workflow.run.tree.warn")).append(" ").append(warnings);
            }
            if (errors > 0) {
                result.append("  ").append(GitHubWorkflowBundle.message("workflow.run.tree.err")).append(" ").append(errors);
            }
            return result.toString();
        }

        @Override
        public Icon icon() {
            final List<JobNode> children = children();
            return aggregateIcon(children, children.stream().anyMatch(JobNode::running), AllIcons.RunConfigurations.TestNotRan, false);
        }

        @Override
        public List<PrintedText> snapshot() {
            final List<PrintedText> result = new ArrayList<>();
            children().forEach(job -> {
                result.add(new PrintedText("\n== " + job.title() + " ==\n", ConsoleViewContentType.SYSTEM_OUTPUT));
                result.addAll(job.snapshot());
            });
            return result;
        }

        private List<JobNode> children() {
            return jobs.values().stream()
                    .filter(job -> name.equals(job.groupName()))
                    .sorted(Comparator.comparingLong(JobNode::jobId))
                    .toList();
        }
    }

    private final class JobNode implements TreeEntry {
        private final Object lock = new Object();
        private final List<PrintedText> output = new ArrayList<>();
        private final LogRenderer logRenderer = new LogRenderer();
        private final AtomicReference<JobState> state;
        private @Nullable DefaultMutableTreeNode treeNode;

        private JobNode(final WorkflowRun.JobStatus job) {
            state = new AtomicReference<>(JobState.from(job, System.currentTimeMillis()));
        }

        private long jobId() {
            return state.get().jobId();
        }

        private String groupName() {
            return state.get().groupName();
        }

        private void print(final WorkflowRun.JobStatus job, final String text, final ConsoleViewContentType contentType) {
            update(job);
            append(new PrintedText(text, contentType));
        }

        private void printLog(final String text) {
            logRenderer.render(text).forEach(segment -> append(new PrintedText(segment.text(), contentType(segment.kind()))));
        }

        private void append(final PrintedText text) {
            final boolean warning = text.contentType() == ConsoleViewContentType.LOG_WARNING_OUTPUT;
            final boolean error = text.contentType() == ConsoleViewContentType.LOG_ERROR_OUTPUT
                    || text.contentType() == ConsoleViewContentType.ERROR_OUTPUT;
            synchronized (lock) {
                output.add(text);
            }
            if (warning || error) {
                state.updateAndGet(current -> current.withDiagnostic(warning, error));
            }
            printIfSelected(this, text);
            refreshTree();
        }

        private void update(final WorkflowRun.JobStatus job) {
            state.updateAndGet(current -> current.update(job, System.currentTimeMillis()));
        }

        @Override
        public String title() {
            return state.get().displayName();
        }

        @Override
        public String suffix() {
            final JobState current = state.get();
            final StringBuilder result = new StringBuilder();
            final String duration = current.duration(System.currentTimeMillis());
            if (!duration.isBlank()) {
                result.append(duration);
            }
            if (current.warnings() > 0) {
                appendSuffix(result, GitHubWorkflowBundle.message("workflow.run.tree.warn") + " " + current.warnings());
            }
            if (current.errors() > 0) {
                appendSuffix(result, GitHubWorkflowBundle.message("workflow.run.tree.err") + " " + current.errors());
            }
            return result.toString();
        }

        @Override
        public Icon icon() {
            final JobState current = state.get();
            if (current.completed()) {
                if (current.skipped()) {
                    return AllIcons.RunConfigurations.TestState.Yellow2;
                }
                if (current.cancelled()) {
                    return AllIcons.RunConfigurations.TestTerminated;
                }
                return successful(current.conclusion()) ? AllIcons.General.GreenCheckmark : AllIcons.General.Error;
            }
            return current.running() ? AnimatedIcon.Default.INSTANCE : AllIcons.RunConfigurations.TestNotRan;
        }

        @Override
        public List<PrintedText> snapshot() {
            synchronized (lock) {
                return List.copyOf(output);
            }
        }

        private int warnings() {
            return state.get().warnings();
        }

        private int errors() {
            return state.get().errors();
        }

        private boolean completed() {
            return state.get().completed();
        }

        private boolean running() {
            return state.get().running();
        }

        private boolean failed() {
            return state.get().failed();
        }

        private boolean skipped() {
            return state.get().skipped();
        }

        private boolean downloadableLog() {
            synchronized (lock) {
                return completed() || !output.isEmpty();
            }
        }

        private boolean cancelled() {
            return state.get().cancelled();
        }

        private void finish(final String runConclusion) {
            state.updateAndGet(current -> current.finish(runConclusion, System.currentTimeMillis()));
        }

        private void clear() {
            synchronized (lock) {
                output.clear();
            }
        }
    }

    private void printIfSelected(final TreeEntry entry, final PrintedText text) {
        if (selectedEntry != entry || project.isDisposed()) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            final ConsoleView console = detailConsole;
            if (console != null && selectedEntry == entry) {
                console.print(text.text(), text.contentType());
            }
        });
    }

    private static void appendSuffix(final StringBuilder builder, final String value) {
        if (!builder.isEmpty()) {
            builder.append("  ");
        }
        builder.append(value);
    }

    private static final class JobTreeCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(
                final JTree tree,
                final Object value,
                final boolean selected,
                final boolean expanded,
                final boolean leaf,
                final int row,
                final boolean hasFocus
        ) {
            if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof TreeEntry entry) {
                setIcon(entry.icon());
                append(entry.title(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                final String suffix = entry.suffix();
                if (!suffix.isBlank()) {
                    append("  " + suffix, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            }
        }
    }

    private record PrintedText(String text, ConsoleViewContentType contentType) {
    }
}
