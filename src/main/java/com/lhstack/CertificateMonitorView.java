package com.lhstack;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.lhstack.components.TextFieldDialog;
import com.lhstack.state.MonitorState;
import com.lhstack.utils.NotifyUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.*;
import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;

/**
 * 证书监控视图 - 监控域名SSL证书有效期
 */
public class CertificateMonitorView extends JPanel implements Disposable {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final int CONNECTION_TIMEOUT = 10000;

    private final Project project;
    private final MonitorState.State state;
    private ListTableModel<MonitorItem> models;
    private TableView<MonitorItem> tableView;
    private JLabel statusLabel;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledTask;

    public CertificateMonitorView(Project project) {
        this.project = project;
        this.state = MonitorState.getInstance(project).getState();
        this.init();
        this.loadSavedDomains();
        this.startAutoRefresh();
    }

    private void init() {
        this.setLayout(new BorderLayout());

        // 工具栏
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(createAddDomainAction());
        group.add(createBatchAddAction());
        group.add(createRemoveDomainAction());
        group.addSeparator();
        group.add(createRefreshAction());
        group.add(createRefreshAllAction());
        group.addSeparator();
        group.add(createExportReportAction());

        SimpleToolWindowPanel panel = new SimpleToolWindowPanel(true);
        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("CertificateMonitor", group, true);
        actionToolbar.setTargetComponent(panel);
        panel.setToolbar(actionToolbar.getComponent());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(createMainPanel(), BorderLayout.CENTER);
        mainPanel.add(createStatusBar(), BorderLayout.SOUTH);

        panel.setContent(mainPanel);
        this.add(panel, BorderLayout.CENTER);
    }

    private JComponent createMainPanel() {
        this.models = new ListTableModel<>(
                new ColumnInfo<MonitorItem, String>("域名") {
                    @Override
                    public @Nullable String valueOf(MonitorItem item) {
                        return item.getDomain();
                    }
                },
                new ColumnInfo<MonitorItem, String>("端口") {
                    @Override
                    public @Nullable String valueOf(MonitorItem item) {
                        return String.valueOf(item.getPort());
                    }
                },
                new ColumnInfo<MonitorItem, String>("颁发者") {
                    @Override
                    public @Nullable String valueOf(MonitorItem item) {
                        return item.getIssuer();
                    }
                },
                new ColumnInfo<MonitorItem, String>("过期时间") {
                    @Override
                    public @Nullable String valueOf(MonitorItem item) {
                        return item.getNotAfterFormatted();
                    }
                },
                new ColumnInfo<MonitorItem, String>("剩余天数") {
                    @Override
                    public @Nullable String valueOf(MonitorItem item) {
                        return item.getDaysRemainingText();
                    }
                },
                new ColumnInfo<MonitorItem, String>("状态") {
                    @Override
                    public @Nullable String valueOf(MonitorItem item) {
                        return item.getStatus();
                    }
                }
        );

        this.tableView = new TableView<>(this.models) {
            @Override
            public TableCellRenderer getCellRenderer(int row, int column) {
                TableCellRenderer defaultRenderer = super.getCellRenderer(row, column);
                return (table, value, isSelected, hasFocus, r, c) -> {
                    Component component = defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, r, c);
                    if (component instanceof JLabel) {
                        JLabel label = (JLabel) component;
                        label.setHorizontalAlignment(JLabel.CENTER);

                        if (!isSelected && r < models.getItems().size()) {
                            MonitorItem item = models.getItems().get(r);
                            if (item.isError()) {
                                label.setForeground(Color.GRAY);
                            } else if (item.isExpired()) {
                                label.setForeground(new Color(203, 29, 29));
                            } else if (item.getDaysRemaining() <= 7) {
                                label.setForeground(new Color(203, 29, 29));
                            } else if (item.getDaysRemaining() <= 30) {
                                label.setForeground(new Color(255, 136, 0));
                            }
                        }
                    }
                    return component;
                };
            }
        };

        this.tableView.setRowHeight(28);

        JTableHeader tableHeader = this.tableView.getTableHeader();
        tableHeader.setReorderingAllowed(false);
        TableCellRenderer defaultRenderer = tableHeader.getDefaultRenderer();
        tableHeader.setDefaultRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JComponent component = (JComponent) defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (component instanceof JLabel) {
                ((JLabel) component).setHorizontalAlignment(JLabel.CENTER);
                ((JLabel) component).setFont(component.getFont().deriveFont(Font.BOLD));
            }
            return component;
        });

        // 设置列宽
        this.tableView.getColumnModel().getColumn(0).setPreferredWidth(180);
        this.tableView.getColumnModel().getColumn(1).setPreferredWidth(60);
        this.tableView.getColumnModel().getColumn(2).setPreferredWidth(150);
        this.tableView.getColumnModel().getColumn(3).setPreferredWidth(150);
        this.tableView.getColumnModel().getColumn(4).setPreferredWidth(80);
        this.tableView.getColumnModel().getColumn(5).setPreferredWidth(100);

        // 右键菜单
        DefaultActionGroup popupGroup = new DefaultActionGroup();
        popupGroup.add(createViewDetailAction());
        popupGroup.add(createRefreshAction());
        popupGroup.addSeparator();
        popupGroup.add(createRemoveDomainAction());
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("MonitorPopup", popupGroup);
        this.tableView.setComponentPopupMenu(popupMenu.getComponent());

        // 双击查看详情
        this.tableView.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && !tableView.getSelectedObjects().isEmpty()) {
                    showCertificateDetail(tableView.getSelectedObjects().get(0));
                }
            }
        });

        return new JBScrollPane(tableView);
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        statusLabel = new JLabel("就绪 - 添加域名开始监控");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setForeground(Color.GRAY);

        statusBar.add(statusLabel, BorderLayout.WEST);
        return statusBar;
    }

    private void updateStatusBar() {
        int total = models.getItems().size();
        long expired = models.getItems().stream().filter(MonitorItem::isExpired).count();
        long expiringSoon = models.getItems().stream()
                .filter(i -> !i.isExpired() && !i.isError() && i.getDaysRemaining() <= 30)
                .count();
        long errors = models.getItems().stream().filter(MonitorItem::isError).count();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("共监控 %d 个域名", total));
        if (expired > 0) {
            sb.append(String.format(" | 已过期: %d", expired));
        }
        if (expiringSoon > 0) {
            sb.append(String.format(" | 即将过期: %d", expiringSoon));
        }
        if (errors > 0) {
            sb.append(String.format(" | 检查失败: %d", errors));
        }

        statusLabel.setText(sb.toString());
    }

    private AnAction createAddDomainAction() {
        return new AnAction(() -> "添加域名", Icons.ADD) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                String input = Messages.showInputDialog(
                        project,
                        "请输入要监控的域名(可包含端口,如: example.com:8443)",
                        "添加监控域名",
                        Messages.getQuestionIcon()
                );

                if (StringUtils.isBlank(input)) {
                    return;
                }

                addDomain(input.trim());
            }
        };
    }

    private AnAction createBatchAddAction() {
        return new AnAction(() -> "批量添加", com.intellij.icons.AllIcons.General.Add) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                String input = Messages.showMultilineInputDialog(
                        project,
                        "请输入要监控的域名列表(每行一个,可包含端口):\n例如:\nexample.com\ngoogle.com:443\nlocalhost:8443",
                        "批量添加监控域名",
                        "",
                        Messages.getQuestionIcon(),
                        null
                );

                if (StringUtils.isBlank(input)) {
                    return;
                }

                String[] lines = input.split("\n");
                int addedCount = 0;
                for (String line : lines) {
                    String domain = line.trim();
                    if (StringUtils.isNotBlank(domain) && !domain.startsWith("#")) {
                        if (addDomain(domain)) {
                            addedCount++;
                        }
                    }
                }

                if (addedCount > 0) {
                    NotifyUtils.notify(String.format("成功添加 %d 个域名", addedCount), project);
                }
            }
        };
    }

    /**
     * 添加域名到监控列表
     * @return 是否添加成功
     */
    private boolean addDomain(String input) {
        String domain;
        int port = DEFAULT_HTTPS_PORT;

        if (input.contains(":")) {
            String[] parts = input.split(":");
            domain = parts[0].trim();
            try {
                port = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ex) {
                NotifyUtils.notifyError("端口格式错误: " + input, project);
                return false;
            }
        } else {
            domain = input.trim();
        }

        // 检查是否已存在
        int finalPort = port;
        boolean exists = models.getItems().stream()
                .anyMatch(item -> item.getDomain().equalsIgnoreCase(domain) && item.getPort() == finalPort);
        if (exists) {
            return false;
        }

        MonitorItem item = new MonitorItem(domain, port);
        models.addRow(item);
        saveDomains();
        updateStatusBar();

        // 立即检查
        checkCertificate(item);
        return true;
    }

    private AnAction createRemoveDomainAction() {
        return new AnAction(() -> "移除域名", Icons.DELETE) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                List<MonitorItem> selected = tableView.getSelectedObjects();
                if (selected.isEmpty()) {
                    NotifyUtils.notifyWarning("请先选择要移除的域名", project);
                    return;
                }

                int result = Messages.showYesNoDialog(
                        project,
                        String.format("确定要移除选中的 %d 个域名吗?", selected.size()),
                        "确认移除",
                        Messages.getWarningIcon()
                );

                if (result == Messages.YES) {
                    List<MonitorItem> items = new ArrayList<>(models.getItems());
                    items.removeAll(selected);
                    models.setItems(items);
                    saveDomains();
                    updateStatusBar();
                    NotifyUtils.notify("已移除选中的域名", project);
                }
            }
        };
    }

    private AnAction createRefreshAction() {
        return new AnAction(() -> "刷新选中", com.intellij.icons.AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                List<MonitorItem> selected = tableView.getSelectedObjects();
                if (selected.isEmpty()) {
                    NotifyUtils.notifyWarning("请先选择要刷新的域名", project);
                    return;
                }

                for (MonitorItem item : selected) {
                    checkCertificate(item);
                }
            }
        };
    }

    private AnAction createRefreshAllAction() {
        return new AnAction(() -> "刷新全部", com.intellij.icons.AllIcons.Actions.Rerun) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (models.getItems().isEmpty()) {
                    NotifyUtils.notifyWarning("监控列表为空", project);
                    return;
                }

                statusLabel.setText("正在检查所有域名...");
                for (MonitorItem item : models.getItems()) {
                    checkCertificate(item);
                }
            }
        };
    }

    private AnAction createViewDetailAction() {
        return new AnAction(() -> "查看详情", Icons.DETAIL) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                List<MonitorItem> selected = tableView.getSelectedObjects();
                if (!selected.isEmpty()) {
                    showCertificateDetail(selected.get(0));
                }
            }
        };
    }

    private AnAction createExportReportAction() {
        return new AnAction(() -> "导出报告", Icons.EXPORT) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (models.getItems().isEmpty()) {
                    NotifyUtils.notifyWarning("监控列表为空", project);
                    return;
                }

                // 弹出选择导出格式
                String[] options = {"文本报告", "CSV格式", "取消"};
                int choice = JOptionPane.showOptionDialog(
                        null,
                        "请选择导出格式",
                        "导出报告",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[0]
                );

                if (choice == 0) {
                    exportTextReport();
                } else if (choice == 1) {
                    exportCsvReport();
                }
            }
        };
    }

    private void exportTextReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SSL证书监控报告 ===\n");
        sb.append("生成时间: ").append(DATE_FORMAT.format(new Date())).append("\n\n");

        for (MonitorItem item : models.getItems()) {
            sb.append("─────────────────────────────────────\n");
            sb.append("域名: ").append(item.getDomain()).append(":").append(item.getPort()).append("\n");
            sb.append("状态: ").append(item.getStatus()).append("\n");
            if (!item.isError()) {
                sb.append("颁发者: ").append(item.getIssuer()).append("\n");
                sb.append("主题: ").append(item.getSubject()).append("\n");
                sb.append("过期时间: ").append(item.getNotAfterFormatted()).append("\n");
                sb.append("剩余天数: ").append(item.getDaysRemainingText()).append("\n");
            } else {
                sb.append("错误信息: ").append(item.getErrorMessage()).append("\n");
            }
            sb.append("\n");
        }

        new TextFieldDialog("SSL证书监控报告", sb.toString(), project).setVisible(true);
    }

    private void exportCsvReport() {
        FileChooser.chooseSaveFile("导出CSV报告", "certificate-monitor-report.csv", project, virtualFile -> {
            StringBuilder sb = new StringBuilder();
            // CSV 头
            sb.append("域名,端口,状态,颁发者,过期时间,剩余天数,错误信息\n");

            for (MonitorItem item : models.getItems()) {
                sb.append(escapeCsv(item.getDomain())).append(",");
                sb.append(item.getPort()).append(",");
                sb.append(escapeCsv(item.getStatus())).append(",");
                sb.append(escapeCsv(item.getIssuer())).append(",");
                sb.append(escapeCsv(item.getNotAfterFormatted())).append(",");
                sb.append(escapeCsv(item.getDaysRemainingText())).append(",");
                sb.append(escapeCsv(item.getErrorMessage() != null ? item.getErrorMessage() : "")).append("\n");
            }

            java.nio.file.Files.write(virtualFile.toNioPath(), 
                    sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            NotifyUtils.notify("CSV报告导出成功", project);
        }, "csv");
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void showCertificateDetail(MonitorItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("                    证书详细信息\n");
        sb.append("═══════════════════════════════════════════════════════════════\n\n");

        sb.append("域名: ").append(item.getDomain()).append(":").append(item.getPort()).append("\n");
        sb.append("状态: ").append(item.getStatus()).append("\n\n");

        if (item.isError()) {
            sb.append("错误信息: ").append(item.getErrorMessage()).append("\n");
        } else {
            sb.append("【证书信息】\n");
            sb.append("───────────────────────────────────────────────────────────────\n");
            sb.append("主题: ").append(item.getSubject()).append("\n");
            sb.append("颁发者: ").append(item.getIssuer()).append("\n");
            sb.append("序列号: ").append(item.getSerialNumber()).append("\n");
            sb.append("签名算法: ").append(item.getSigAlgName()).append("\n");
            sb.append("\n【有效期】\n");
            sb.append("───────────────────────────────────────────────────────────────\n");
            sb.append("生效时间: ").append(item.getNotBeforeFormatted()).append("\n");
            sb.append("过期时间: ").append(item.getNotAfterFormatted()).append("\n");
            sb.append("剩余天数: ").append(item.getDaysRemainingText()).append("\n");
            sb.append("\n最后检查: ").append(item.getLastCheckFormatted()).append("\n");
        }

        new TextFieldDialog("证书详情 - " + item.getDomain(), sb.toString(), project).setVisible(true);
    }

    private void checkCertificate(MonitorItem item) {
        CompletableFuture.runAsync(() -> {
            try {
                item.setStatus("检查中...");
                ApplicationManager.getApplication().invokeLater(() -> models.fireTableDataChanged());

                X509Certificate cert = fetchCertificate(item.getDomain(), item.getPort());
                item.updateFromCertificate(cert);
                item.setLastCheck(new Date());
                item.setErrorMessage(null);

            } catch (Throwable e) {
                item.setError(true);
                item.setErrorMessage(e.getMessage());
                item.setStatus("检查失败");
            }

            ApplicationManager.getApplication().invokeLater(() -> {
                models.fireTableDataChanged();
                updateStatusBar();
                saveDomains();
            });
        });
    }

    private X509Certificate fetchCertificate(String host, int port) throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());

        SSLSocketFactory factory = sc.getSocketFactory();
        try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
            socket.setSoTimeout(CONNECTION_TIMEOUT);
            socket.startHandshake();

            Certificate[] certs = socket.getSession().getPeerCertificates();
            if (certs.length > 0 && certs[0] instanceof X509Certificate) {
                return (X509Certificate) certs[0];
            }
        }

        throw new RuntimeException("无法获取证书");
    }

    private void loadSavedDomains() {
        String domainsJson = state.getMonitorDomains();
        if (StringUtils.isNotBlank(domainsJson)) {
            try {
                String[] domains = domainsJson.split(";");
                for (String domainPort : domains) {
                    if (StringUtils.isBlank(domainPort)) continue;
                    String[] parts = domainPort.split(":");
                    String domain = parts[0];
                    int port = parts.length > 1 ? Integer.parseInt(parts[1]) : DEFAULT_HTTPS_PORT;
                    models.addRow(new MonitorItem(domain, port));
                }
                updateStatusBar();

                // 延迟检查所有域名
                scheduler.schedule(() -> {
                    for (MonitorItem item : models.getItems()) {
                        checkCertificate(item);
                    }
                }, 2, TimeUnit.SECONDS);
            } catch (Throwable ignored) {
            }
        }
    }

    private void saveDomains() {
        StringBuilder sb = new StringBuilder();
        for (MonitorItem item : models.getItems()) {
            if (sb.length() > 0) sb.append(";");
            sb.append(item.getDomain()).append(":").append(item.getPort());
        }
        state.setMonitorDomains(sb.toString());
    }

    private void startAutoRefresh() {
        // 每小时自动刷新一次
        scheduledTask = scheduler.scheduleAtFixedRate(() -> {
            if (!models.getItems().isEmpty()) {
                for (MonitorItem item : models.getItems()) {
                    checkCertificate(item);
                }
            }
        }, 1, 1, TimeUnit.HOURS);
    }

    @Override
    public void dispose() {
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
        }
        scheduler.shutdownNow();
    }

    /**
     * 监控项
     */
    public static class MonitorItem {
        private String domain;
        private int port;
        private String subject;
        private String issuer;
        private String serialNumber;
        private String sigAlgName;
        private Date notBefore;
        private Date notAfter;
        private Date lastCheck;
        private String status = "待检查";
        private boolean error = false;
        private String errorMessage;

        public MonitorItem(String domain, int port) {
            this.domain = domain;
            this.port = port;
        }

        public void updateFromCertificate(X509Certificate cert) {
            this.subject = cert.getSubjectX500Principal().getName();
            this.issuer = cert.getIssuerX500Principal().getName();
            this.serialNumber = cert.getSerialNumber().toString(16).toUpperCase();
            this.sigAlgName = cert.getSigAlgName();
            this.notBefore = cert.getNotBefore();
            this.notAfter = cert.getNotAfter();
            this.error = false;

            try {
                cert.checkValidity();
                long days = getDaysRemaining();
                if (days <= 7) {
                    this.status = "即将过期";
                } else if (days <= 30) {
                    this.status = "注意";
                } else {
                    this.status = "正常";
                }
            } catch (Exception e) {
                this.status = "已过期";
            }
        }

        public String getDomain() { return domain; }
        public int getPort() { return port; }
        public String getSubject() { return subject != null ? subject : "-"; }
        public String getIssuer() { return issuer != null ? extractCN(issuer) : "-"; }
        public String getSerialNumber() { return serialNumber != null ? serialNumber : "-"; }
        public String getSigAlgName() { return sigAlgName != null ? sigAlgName : "-"; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public boolean isError() { return error; }
        public void setError(boolean error) { this.error = error; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public void setLastCheck(Date lastCheck) { this.lastCheck = lastCheck; }

        public boolean isExpired() {
            return notAfter != null && notAfter.before(new Date());
        }

        public long getDaysRemaining() {
            if (notAfter == null) return -1;
            return (notAfter.getTime() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
        }

        public String getDaysRemainingText() {
            if (error || notAfter == null) return "-";
            long days = getDaysRemaining();
            if (days < 0) return "已过期";
            return days + " 天";
        }

        public String getNotAfterFormatted() {
            return notAfter != null ? DATE_FORMAT.format(notAfter) : "-";
        }

        public String getNotBeforeFormatted() {
            return notBefore != null ? DATE_FORMAT.format(notBefore) : "-";
        }

        public String getLastCheckFormatted() {
            return lastCheck != null ? DATE_FORMAT.format(lastCheck) : "-";
        }

        private String extractCN(String dn) {
            if (dn == null) return "-";
            for (String part : dn.split(",")) {
                String trimmed = part.trim();
                if (trimmed.toUpperCase().startsWith("CN=")) {
                    return trimmed.substring(3);
                }
            }
            return dn.length() > 30 ? dn.substring(0, 27) + "..." : dn;
        }
    }
}
