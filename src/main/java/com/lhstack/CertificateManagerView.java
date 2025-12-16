package com.lhstack;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.lhstack.actions.table.CopyCertificateAction;
import com.lhstack.actions.table.DeleteCertificateAction;
import com.lhstack.actions.table.ExportCertificateAction;
import com.lhstack.actions.table.ExportPrivateKeyAction;
import com.lhstack.actions.table.ShowDetailAction;
import com.lhstack.actions.table.VerifyCertificateAction;
import com.lhstack.utils.CertificateUtils;
import com.lhstack.utils.NotifyUtils;
import com.lhstack.utils.PemUtils;
import org.apache.commons.collections.EnumerationUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class CertificateManagerView extends JPanel implements Disposable {

    private final Project project;

    private KeyStore keyStore;

    private ListTableModel<Item> models;

    private TableView<Item> tableView;
    /**
     * 证书虚拟文件,isNew = false时存在
     */
    private VirtualFile certificateVirtualFile;

    /**
     * 是否新建
     */
    private boolean isNew = false;
    //证书密码
    private char[] passwordArray;

    public CertificateManagerView(Project project) {
        this.project = project;
        this.init();
    }

    @Override
    public void dispose() {
        // 清理密码数组
        if (passwordArray != null) {
            Arrays.fill(passwordArray, '\0');
            passwordArray = null;
        }
        keyStore = null;
        certificateVirtualFile = null;
    }

    private JLabel statusLabel;

    private void init() {
        this.setLayout(new BorderLayout());
        
        // 工具栏
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(createImportCertificateAction());
        group.add(createEmptyCertificateAction());
        group.addSeparator();
        group.add(createAddCertificateAction());
        group.add(createAddCertificateChainAction());
        group.addSeparator();
        group.add(createSaveCertificateAction());
        group.add(createReSaveCertificateAction());
        group.addSeparator();
        group.add(createRefreshAction());
        
        SimpleToolWindowPanel panel = new SimpleToolWindowPanel(true);
        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("CertificateManager", group, true);
        actionToolbar.setTargetComponent(panel);
        panel.setToolbar(actionToolbar.getComponent());
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(createMainPanel(), BorderLayout.CENTER);
        mainPanel.add(createStatusBar(), BorderLayout.SOUTH);
        
        panel.setContent(mainPanel);
        this.add(panel, BorderLayout.CENTER);
    }

    /**
     * 创建刷新操作
     */
    private AnAction createRefreshAction() {
        return new AnAction(() -> "刷新", com.intellij.icons.AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (keyStore != null) {
                    try {
                        refreshTable();
                        NotifyUtils.notify("刷新成功", project);
                    } catch (Throwable err) {
                        NotifyUtils.notifyError("刷新失败: " + err.getMessage(), project);
                    }
                }
            }
        };
    }

    /**
     * 创建状态栏
     */
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        
        statusLabel = new JLabel("就绪 - 请导入或创建证书库");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setForeground(Color.GRAY);
        
        statusBar.add(statusLabel, BorderLayout.WEST);
        return statusBar;
    }

    /**
     * 更新状态栏
     */
    private void updateStatusBar() {
        if (keyStore == null) {
            statusLabel.setText("就绪 - 请导入或创建证书库");
            return;
        }
        
        int total = models.getItems().size();
        long expired = models.getItems().stream().filter(Item::isExpired).count();
        long expiringSoon = models.getItems().stream()
                .filter(i -> !i.isExpired() && i.getStatus().contains("即将过期"))
                .count();
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("共 %d 个证书", total));
        if (expired > 0) {
            sb.append(String.format(" | 已过期: %d", expired));
        }
        if (expiringSoon > 0) {
            sb.append(String.format(" | 即将过期: %d", expiringSoon));
        }
        if (certificateVirtualFile != null) {
            sb.append(" | 文件: ").append(certificateVirtualFile.getName());
        } else if (isNew) {
            sb.append(" | 新建证书库(未保存)");
        }
        
        statusLabel.setText(sb.toString());
    }

    /**
     * 添加证书链
     *
     * @return
     */
    private AnAction createAddCertificateChainAction() {
        return new AnAction(() -> "添加证书链", Icons.CERTIFICATE_CHAIN) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                if (keyStore == null) {
                    Messages.showErrorDialog("请先导入或者创建空证书", "错误提示");
                    return;
                }
                String certificateName = JOptionPane.showInputDialog("请设置添加的证书链名称");
                if (StringUtils.isEmpty(certificateName)) {
                    NotifyUtils.notifyWarning("证书名字不能为空", project);
                    return;
                }
                try {
                    Certificate[] certificateChain = keyStore.getCertificateChain(certificateName);
                    if (certificateChain != null && certificateChain.length > 0) {
                        int result = JOptionPane.showConfirmDialog(null, "相同名字的证书已经存在,点击是,覆盖已有证书,点击取消,不做任何修改", "警告", JOptionPane.OK_CANCEL_OPTION);
                        if (result == JOptionPane.CANCEL_OPTION) {
                            return;
                        }
                    }
                } catch (Throwable err) {
                    NotifyUtils.notifyError("添加证书链错误,异常信息: " + err.getMessage(), project);
                    return;
                }
                FileChooser.chooseSingleFile("请选择私钥文件", project).ifPresent(keyFile -> {
                    try {
                        byte[] bytes = Files.readAllBytes(keyFile.toNioPath());
                        PrivateKey privateKey = parsePrivateKey(bytes);
                        if (privateKey == null) {
                            NotifyUtils.notifyError("证书导入失败,私钥为空或格式不正确", project);
                            return;
                        }
                        PrivateKey finalPrivateKey = privateKey;
                        FileChooser.chooseSingleFile("请选择私钥对应的证书文件", project).ifPresent(file -> {
                            Certificate certificate;
                            try {
                                byte[] certificateBytes = Files.readAllBytes(file.toNioPath());
                                certificate = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certificateBytes));
                            } catch (Throwable err) {
                                NotifyUtils.notifyError(String.format("证书导入失败,文件名称: %s,错误信息: %s", file.getName(), err.getMessage()), project);
                                return;
                            }
                            String password = JOptionPane.showInputDialog("请输入私钥存储密码,不输入则为空密码");
                            char[] entryPassword = StringUtils.isNotBlank(password) ? password.toCharArray() : null;
                            try {
                                keyStore.setKeyEntry(certificateName, finalPrivateKey, entryPassword, new Certificate[]{certificate});
                                refreshTable();
                                NotifyUtils.notify("证书链添加成功", project);
                            } catch (Throwable e) {
                                NotifyUtils.notifyError("证书导入失败,错误信息: " + e.getMessage(), project);
                            }
                        });
                    } catch (Throwable err) {
                        NotifyUtils.notifyError("证书导入失败,错误信息: " + err.getMessage(), project);
                    }
                });
            }
        };
    }

    /**
     * 解析私钥
     */
    private PrivateKey parsePrivateKey(byte[] bytes) {
        try {
            PrivateKey privateKey = PemUtils.readPrivateKey(new String(bytes, StandardCharsets.UTF_8));
            if (privateKey != null) {
                return privateKey;
            }
        } catch (Throwable ignored) {
        }
        try {
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            PrivateKeyInfo privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(PrivateKeyFactory.createKey(bytes));
            return converter.getPrivateKey(privateKeyInfo);
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 另存为证书
     *
     * @return
     */
    private AnAction createReSaveCertificateAction() {
        return new AnAction(() -> "另存为", Icons.RESAVE) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (keyStore == null) {
                    Messages.showErrorDialog("请先导入或者创建证书", "提示");
                    return;
                }
                FileChooser.chooseSaveFile("另存为证书", "newCertificate", project, virtualFile -> {
                    String password = JOptionPane.showInputDialog("请输入证书密码,不输入或者点取消则默认无密码");
                    char[] newPassword = StringUtils.isNotBlank(password) ? password.toCharArray() : null;
                    try (FileOutputStream fos = new FileOutputStream(virtualFile.getPresentableUrl())) {
                        keyStore.store(fos, newPassword);
                        NotifyUtils.notify("另存为证书成功", project);
                    }
                }, "jks", "p12", "pfx");
            }
        };
    }

    /**
     * 保存证书
     *
     * @return
     */
    private AnAction createSaveCertificateAction() {
        return new AnAction(() -> "保存", Icons.SAVE) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (keyStore == null) {
                    Messages.showErrorDialog("请先导入或者创建证书", "提示");
                    return;
                }
                //不是新建
                if (!isNew && certificateVirtualFile != null) {
                    try (FileOutputStream fos = new FileOutputStream(certificateVirtualFile.getPresentableUrl())) {
                        keyStore.store(fos, passwordArray);
                        NotifyUtils.notify("证书保存成功", project);
                    } catch (Throwable err) {
                        NotifyUtils.notifyError("保存证书错误: " + err.getMessage(), project);
                    }
                } else {
                    FileChooser.chooseSaveFile("保存证书", "newCertificate", project, virtualFile -> {
                        String password = JOptionPane.showInputDialog("请输入证书密码,不输入或者点取消则默认无密码");
                        passwordArray = StringUtils.isNotBlank(password) ? password.toCharArray() : null;
                        try (FileOutputStream fos = new FileOutputStream(virtualFile.getPresentableUrl())) {
                            keyStore.store(fos, passwordArray);
                            certificateVirtualFile = virtualFile;
                            isNew = false;
                            NotifyUtils.notify("证书保存成功", project);
                        }
                    }, "jks", "p12", "pfx");
                }
            }
        };
    }

    /**
     * 添加证书
     *
     * @return
     */
    private AnAction createAddCertificateAction() {
        return new AnAction(() -> "添加证书", Icons.ADD) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (keyStore == null) {
                    Messages.showErrorDialog("请先导入或者创建空证书", "错误提示");
                    return;
                }
                FileChooser.chooseSingleFile("请选择需要添加的证书", project).ifPresent(virtualFile -> {
                    try {
                        Certificate certificate = CertificateUtils.load(virtualFile);
                        String certificateName = JOptionPane.showInputDialog("请设置添加的证书名称");
                        if (StringUtils.isEmpty(certificateName)) {
                            NotifyUtils.notifyWarning("证书名字不能为空", project);
                            return;
                        }
                        Certificate existCertificate = keyStore.getCertificate(certificateName);
                        if (existCertificate != null) {
                            int result = JOptionPane.showConfirmDialog(null, "相同名字的证书已经存在,点击是,覆盖已有证书,点击取消,不做任何修改", "警告", JOptionPane.OK_CANCEL_OPTION);
                            if (result != JOptionPane.OK_OPTION) {
                                return;
                            }
                        }
                        keyStore.setCertificateEntry(certificateName, certificate);
                        refreshTable();
                        NotifyUtils.notify("添加证书成功", project);
                    } catch (Throwable err) {
                        NotifyUtils.notifyError("添加证书错误: " + err.getMessage(), project);
                    }
                });
            }
        };
    }

    @SuppressWarnings("unchecked")
    private void refreshTable() throws Throwable {
        List<String> list = EnumerationUtils.toList(keyStore.aliases());
        list.sort(Comparator.naturalOrder());
        List<Item> items = new ArrayList<>();
        for (String alias : list) {
            Certificate certificate = keyStore.getCertificate(alias);
            if (certificate != null) {
                Item item = new Item()
                        .setName(alias)
                        .setCertificate(certificate)
                        .setType(certificate.getType())
                        .setPublicKey(certificate.getPublicKey(), StandardCharsets.UTF_8);
                items.add(item);
            }
        }
        models.setItems(items);
        models.fireTableDataChanged();
        updateStatusBar();
    }

    /**
     * 创建空证书
     *
     * @return
     */
    private AnAction createEmptyCertificateAction() {
        return new AnAction(() -> "创建空证书", Icons.EMPTY_CERTIFICATE) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (keyStore != null) {
                    int result = JOptionPane.showConfirmDialog(null, "点击创建空证书会覆盖已导入的证书,是否确认创建空证书", "警告", JOptionPane.OK_CANCEL_OPTION);
                    if (result == JOptionPane.OK_OPTION) {
                        loadCertificate(null);
                        NotifyUtils.notify("创建空证书成功", project);
                    }
                } else {
                    loadCertificate(null);
                    NotifyUtils.notify("创建空证书成功", project);
                }
            }
        };
    }

    /**
     * 导入证书
     *
     * @return
     */
    private AnAction createImportCertificateAction() {
        return new AnAction(() -> "导入证书", Icons.IMPORT) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (keyStore != null) {
                    int result = JOptionPane.showConfirmDialog(null, "点击导入会覆盖已导入的证书,是否确认导入", "警告", JOptionPane.OK_CANCEL_OPTION);
                    if (result == JOptionPane.OK_OPTION) {
                        FileChooser.chooseSingleFile("选择证书", project).ifPresent(virtualFile -> {
                            certificateVirtualFile = virtualFile;
                            CertificateManagerView.this.loadCertificate(virtualFile);
                        });
                    }
                } else {
                    FileChooser.chooseSingleFile("选择证书", project).ifPresent(virtualFile -> {
                        certificateVirtualFile = virtualFile;
                        CertificateManagerView.this.loadCertificate(virtualFile);
                    });
                }
            }

        };
    }

    /**
     * 加载证书
     *
     * @param virtualFile
     */
    @SuppressWarnings("unchecked")
    private void loadCertificate(VirtualFile virtualFile) {
        try {
            String extension = virtualFile != null ? virtualFile.getExtension() : null;
            String keyStoreType = getKeyStoreType(extension);
            this.keyStore = KeyStore.getInstance(keyStoreType);
            
            if (virtualFile == null) {
                isNew = true;
                this.keyStore.load(null, null);
                this.models.setItems(new ArrayList<>());
            } else {
                isNew = false;
                String password = JOptionPane.showInputDialog(null, "证书密码,如果有,请输入,如果没有,请点确认或者取消", "证书密码", JOptionPane.PLAIN_MESSAGE);
                char[] newPasswordArray = (password == null || password.isEmpty()) ? new char[0] : password.toCharArray();
                // 清理旧密码
                if (this.passwordArray != null) {
                    Arrays.fill(this.passwordArray, '\0');
                }
                this.passwordArray = newPasswordArray;
                this.keyStore.load(virtualFile.getInputStream(), this.passwordArray);
                List<String> list = EnumerationUtils.toList(this.keyStore.aliases());
                list.sort(Comparator.naturalOrder());
                List<Item> items = new ArrayList<>();
                for (String alias : list) {
                    Certificate certificate = this.keyStore.getCertificate(alias);
                    if (certificate != null) {
                        Item item = new Item()
                                .setName(alias)
                                .setType(certificate.getType())
                                .setPublicKey(certificate.getPublicKey(), StandardCharsets.UTF_8)
                                .setCertificate(certificate);
                        items.add(item);
                    }
                }
                this.models.setItems(items);
            }
            updateStatusBar();
            NotifyUtils.notify("加载证书成功", project);
        } catch (Throwable e) {
            this.keyStore = null;
            updateStatusBar();
            NotifyUtils.notifyError("加载证书错误: " + e.getMessage(), project);
        }
    }

    /**
     * 根据文件扩展名获取KeyStore类型
     */
    private String getKeyStoreType(String extension) {
        if (extension == null) {
            return KeyStore.getDefaultType();
        }
        switch (extension.toLowerCase()) {
            case "p12":
            case "pfx":
                return "PKCS12";
            case "jks":
            default:
                return KeyStore.getDefaultType();
        }
    }

    private JComponent createMainPanel() {
        this.models = new ListTableModel<>(
                ItemColumn.create("证书名称", Item::getName),
                ItemColumn.create("证书类型", Item::getType),
                ItemColumn.create("加密算法", Item::getAlgorithm),
                ItemColumn.create("过期时间", Item::getNotAfterFormatted),
                ItemColumn.create("状态", Item::getStatus)
        );
        this.tableView = new TableView<>(this.models) {
            public TableCellRenderer getCellRenderer(int row, int column) {
                if (!(this.getModel() instanceof ListTableModel)) {
                    return super.getCellRenderer(row, column);
                } else {
                    ColumnInfo<Item, ?> columnInfo = this.getListTableModel().getColumnInfos()[this.convertColumnIndexToModel(column)];
                    Item item = this.getRow(row);
                    TableCellRenderer renderer = columnInfo.getCustomizedRenderer(item, columnInfo.getRenderer(item));
                    return renderer == null ? createStatusAwareRenderer(super.getCellRenderer(row, column)) : renderer;
                }
            }

            private TableCellRenderer createStatusAwareRenderer(TableCellRenderer cellRenderer) {
                return (table, value, isSelected, hasFocus, row, column) -> {
                    Component component = cellRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    if (component instanceof JLabel) {
                        JLabel label = (JLabel) component;
                        label.setHorizontalAlignment(JLabel.CENTER);
                        
                        // 根据状态设置颜色
                        if (!isSelected && row < models.getItems().size()) {
                            Item item = models.getItems().get(row);
                            if (item.isExpired()) {
                                label.setForeground(new Color(203, 29, 29)); // 红色 - 已过期
                            } else if (item.getStatus().contains("即将过期")) {
                                label.setForeground(new Color(255, 136, 0)); // 橙色 - 即将过期
                            }
                        }
                    }
                    return component;
                };
            }
        };
        
        // 设置表格行高
        this.tableView.setRowHeight(28);
        
        // 设置表头样式
        JTableHeader tableHeader = this.tableView.getTableHeader();
        tableHeader.setReorderingAllowed(false);
        TableCellRenderer defaultRenderer = tableHeader.getDefaultRenderer();
        tableHeader.setDefaultRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JComponent component = (JComponent) defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (component instanceof JLabel) {
                JLabel label = (JLabel) component;
                label.setHorizontalAlignment(JLabel.CENTER);
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            }
            return component;
        });

        // 设置列宽
        this.tableView.getColumnModel().getColumn(0).setPreferredWidth(150); // 证书名称
        this.tableView.getColumnModel().getColumn(1).setPreferredWidth(80);  // 证书类型
        this.tableView.getColumnModel().getColumn(2).setPreferredWidth(80);  // 加密算法
        this.tableView.getColumnModel().getColumn(3).setPreferredWidth(150); // 过期时间
        this.tableView.getColumnModel().getColumn(4).setPreferredWidth(100); // 状态

        // 右键菜单
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new ShowDetailAction(tableView, models, project));
        group.add(new VerifyCertificateAction(tableView, models, project));
        group.addSeparator();
        group.add(new CopyCertificateAction(tableView, models, project));
        group.add(new ExportCertificateAction(tableView, models, project, () -> this.keyStore, () -> passwordArray));
        group.add(new ExportPrivateKeyAction(tableView, models, project, () -> this.keyStore, () -> passwordArray));
        group.addSeparator();
        group.add(new DeleteCertificateAction(tableView, models, project, () -> this.keyStore, () -> {
            try {
                refreshTable();
            } catch (Throwable e) {
                NotifyUtils.notifyError("删除证书失败: " + e.getMessage(), project);
            }
        }));
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("证书操作", group);
        this.tableView.setComponentPopupMenu(popupMenu.getComponent());
        
        // 双击查看详情
        this.tableView.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && !tableView.getSelectedObjects().isEmpty()) {
                    new ShowDetailAction(tableView, models, project).actionPerformed(null);
                }
            }
        });
        
        return new JBScrollPane(tableView);
    }

}
