package com.lhstack;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.lhstack.actions.table.DeleteCertificateAction;
import com.lhstack.actions.table.ExportCertificateAction;
import com.lhstack.actions.table.ExportPrivateKeyAction;
import com.lhstack.actions.table.ShowDetailAction;
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
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class CertificateManagerView extends JPanel {


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

    private void init() {
        this.setLayout(new BorderLayout());
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(createImportCertificateAction());
        group.add(createEmptyCertificateAction());
        group.add(createAddCertificateAction());
        group.add(createAddCertificateChainAction());
        group.add(createSaveCertificateAction());
        group.add(createReSaveCertificateAction());
        SimpleToolWindowPanel panel = new SimpleToolWindowPanel(true);
        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("CertificateManager", group, true);
        panel.setToolbar(actionToolbar.getComponent());
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(createMainPanel(), BorderLayout.CENTER);
        panel.setContent(mainPanel);
        this.add(panel, BorderLayout.CENTER);
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
                    throw new RuntimeException("证书名字不能为空");
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
                    NotifyUtils.notify("添加证书链错误,异常信息: " + err.getMessage(), project);
                    return;
                }
                FileChooser.chooseSingleFile("请选择私钥文件", project).ifPresent(keyFile -> {
                    try {
                        byte[] bytes = Files.readAllBytes(keyFile.toNioPath());
                        PrivateKey privateKey = null;
                        try {
                            privateKey = PemUtils.readPrivateKey(new String(bytes, StandardCharsets.UTF_8));
                            if (privateKey == null) {
                                JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
                                PrivateKeyInfo privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(PrivateKeyFactory.createKey(bytes));
                                privateKey = converter.getPrivateKey(privateKeyInfo);
                            }
                        } catch (Throwable e) {
                            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
                            PrivateKeyInfo privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(PrivateKeyFactory.createKey(bytes));
                            privateKey = converter.getPrivateKey(privateKeyInfo);
                        }
                        if (privateKey == null) {
                            NotifyUtils.notify("证书导入失败,私钥为空", project);
                            return;
                        }
                        PrivateKey finalPrivateKey = privateKey;
                        FileChooser.chooseSingleFile("请选择私钥对应的证书文件", project).ifPresent(file -> {
                            Certificate certificate;
                            try {
                                byte[] certificateBytes = Files.readAllBytes(file.toNioPath());
                                certificate = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certificateBytes));
                            } catch (Throwable err) {
                                NotifyUtils.notify(String.format("证书导入失败,文件名称: %s,错误信息: %s", file.getName(), err.getMessage()), project);
                                return;
                            }
                            String password = JOptionPane.showInputDialog("请输入私钥存储密码,不输入则为空密码");
                            char[] passwordArray = null;
                            if (StringUtils.isNotBlank(password)) {
                                passwordArray = password.toCharArray();
                            }
                            try {
                                keyStore.setKeyEntry(certificateName, finalPrivateKey, passwordArray, new Certificate[]{certificate});
                                refreshTable();
                            } catch (Throwable e) {
                                NotifyUtils.notify("证书导入失败,错误信息: " + e.getMessage(), project);
                            }
                        });
                    } catch (Throwable err) {
                        NotifyUtils.notify("证书导入失败,错误信息: " + err.getMessage(), project);
                    }
                });
            }
        };
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
                //不是新建
                if (!isNew) {
                    FileChooser.chooseSaveFile("另存为证书", "newCertificate", project, virtualFile -> {
                        String password = JOptionPane.showInputDialog("请输入证书密码,不输入或者点取消则默认无密码");
                        char[] newPassword;
                        if (StringUtils.isNotBlank(password)) {
                            newPassword = password.toCharArray();
                        } else {
                            newPassword = null;
                        }
                        String presentableUrl = virtualFile.getPresentableUrl();
                        FileOutputStream fos = new FileOutputStream(presentableUrl);
                        keyStore.store(fos, newPassword);
                        fos.close();
                        NotifyUtils.notify("另存为证书成功", project);
                    }, "jks");
                } else {
                    FileChooser.chooseSaveFile("另存为证书", "newCertificate", project, virtualFile -> {
                        String password = JOptionPane.showInputDialog("请输入证书密码,不输入或者点取消则默认无密码");
                        char[] newPassword;
                        if (StringUtils.isNotBlank(password)) {
                            newPassword = password.toCharArray();
                        } else {
                            newPassword = null;
                        }
                        String presentableUrl = virtualFile.getPresentableUrl();
                        FileOutputStream fos = new FileOutputStream(presentableUrl);
                        keyStore.store(fos, newPassword);
                        fos.close();
                        NotifyUtils.notify("另存为证书成功", project);
                    }, "jks");
                }
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
                //不是新建
                if (!isNew) {
                    try (FileOutputStream fos = new FileOutputStream(certificateVirtualFile.getPresentableUrl())) {
                        keyStore.store(fos, passwordArray);
                        NotifyUtils.notify("证书保存成功", project);
                    } catch (Throwable err) {
                        Messages.showErrorDialog(err.getMessage(), "保存证书错误");
                    }
                } else {
                    FileChooser.chooseSaveFile("保存证书", "newCertificate", project, virtualFile -> {
                        String password = JOptionPane.showInputDialog("请输入证书密码,不输入或者点取消则默认无密码");
                        if (StringUtils.isNotBlank(password)) {
                            passwordArray = password.toCharArray();
                        } else {
                            passwordArray = null;
                        }
                        String presentableUrl = virtualFile.getPresentableUrl();
                        FileOutputStream fos = new FileOutputStream(presentableUrl);
                        keyStore.store(fos, passwordArray);
                        fos.close();
                        certificateVirtualFile = virtualFile;
                        isNew = false;
                        NotifyUtils.notify("证书保存成功", project);
                    }, "jks");
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
                } else {
                    FileChooser.chooseSingleFile("请选择需要添加的证书", project).ifPresent(virtualFile -> {
                        try {
                            Certificate certificate = CertificateUtils.load(virtualFile);
                            String certificateName = JOptionPane.showInputDialog("请设置添加的证书名称");
                            if (StringUtils.isEmpty(certificateName)) {
                                throw new RuntimeException("证书名字不能为空");
                            }
                            Certificate existCertificate = keyStore.getCertificate(certificateName);
                            if (existCertificate != null) {
                                int result = JOptionPane.showConfirmDialog(null, "相同名字的证书已经存在,点击是,覆盖已有证书,点击取消,不做任何修改", "警告", JOptionPane.OK_CANCEL_OPTION);
                                if (result == JOptionPane.OK_OPTION) {
                                    keyStore.setCertificateEntry(certificateName, certificate);
                                    refreshTable();
                                }
                            } else {
                                keyStore.setCertificateEntry(certificateName, certificate);
                                refreshTable();
                                NotifyUtils.notify("添加证书成功", project);
                            }
                        } catch (Throwable err) {
                            NotifyUtils.notify("添加证书错误: " + err.getMessage(), project);
                        }
                    });
                }
            }
        };
    }

    private void refreshTable() throws Throwable {
        List<String> list = EnumerationUtils.toList(keyStore.aliases());
        list.sort(Comparator.comparing(Function.identity()));
        models.setItems(new ArrayList<>());
        for (String alias : list) {
            Certificate certificate = keyStore.getCertificate(alias);
            Item item = new Item()
                    .setName(alias)
                    .setCertificate(certificate)
                    .setType(certificate.getType())
                    .setPublicKey(certificate.getPublicKey(), StandardCharsets.UTF_8);
            models.addRow(item);
        }
        models.fireTableDataChanged();
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
    private void loadCertificate(VirtualFile virtualFile) {
        try {
            this.keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            if (virtualFile == null) {
                isNew = true;
                this.keyStore.load(null, null);
                this.models.setItems(new ArrayList<>());
            } else {
                isNew = false;
                String password = JOptionPane.showInputDialog(null, "证书密码,如果有,请输入,如果没有,请点确认或者取消", "证书密码", JOptionPane.PLAIN_MESSAGE);
                char[] passwordArray = password == null || StringUtils.isEmpty(password) ? new char[0] : password.toCharArray();
                this.passwordArray = passwordArray;
                this.keyStore.load(virtualFile.getInputStream(), passwordArray);
                List<String> list = EnumerationUtils.toList(this.keyStore.aliases());
                list.sort(Comparator.comparing(Function.identity()));
                this.models.setItems(new ArrayList<>());
                for (String alias : list) {
                    Certificate certificate = this.keyStore.getCertificate(alias);
                    Item item = new Item()
                            .setName(alias)
                            .setType(certificate.getType())
                            .setPublicKey(certificate.getPublicKey(), StandardCharsets.UTF_8)
                            .setCertificate(certificate);
                    this.models.addRow(item);
                }
            }
            NotifyUtils.notify("加载证书成功", project);
        } catch (Throwable e) {
            this.keyStore = null;
            NotifyUtils.notify("加载证书错误: " + e.getMessage(), project);
        }
    }

    private JComponent createMainPanel() {
        this.models = new ListTableModel<>(
                ItemColumn.create("证书名称", Item::getName),
                ItemColumn.create("证书类型", Item::getType),
                ItemColumn.create("加密算法", Item::getAlgorithm)
        );
        this.tableView = new TableView<>(this.models) {
            public TableCellRenderer getCellRenderer(int row, int column) {
                if (!(this.getModel() instanceof ListTableModel)) {
                    return super.getCellRenderer(row, column);
                } else {
                    ColumnInfo<Item, ?> columnInfo = this.getListTableModel().getColumnInfos()[this.convertColumnIndexToModel(column)];
                    Item item = this.getRow(row);
                    TableCellRenderer renderer = columnInfo.getCustomizedRenderer(item, columnInfo.getRenderer(item));
                    return renderer == null ? wrapperRender(super.getCellRenderer(row, column)) : renderer;
                }
            }

            private TableCellRenderer wrapperRender(TableCellRenderer cellRenderer) {

                return new TableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                        Component component = cellRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                        if (component instanceof JLabel) {
                            ((JLabel) component).setHorizontalAlignment(JLabel.CENTER);
                        }
                        return component;
                    }
                };
            }
        };
        JTableHeader tableHeader = this.tableView.getTableHeader();
        TableCellRenderer defaultRenderer = tableHeader.getDefaultRenderer();
        tableHeader.setDefaultRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JComponent component = (JComponent) defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (component instanceof JLabel) {
                ((JLabel) component).setHorizontalAlignment(JLabel.CENTER);
            }
            return component;
        });

//        this.tableView.setCellSelectionEnabled(false);
//        this.tableView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new ShowDetailAction(tableView, models, project));
        group.add(new DeleteCertificateAction(tableView, models, project, () -> this.keyStore, () -> {
            try {
                refreshTable();
            } catch (Throwable e) {
                NotifyUtils.notify("删除证书失败,错误信息: " + e.getMessage(), project);
            }
        }));
        group.add(new ExportCertificateAction(tableView, models, project, () -> this.keyStore, () -> passwordArray));
        group.add(new ExportPrivateKeyAction(tableView, models, project, () -> this.keyStore, () -> passwordArray));
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("操作", group);
//        ListPopup listPopup = JBPopupFactory.getInstance().createActionGroupPopup("操作", group, DataContext.EMPTY_CONTEXT, JBPopupFactory.ActionSelectionAid.MNEMONICS, true);
        this.tableView.setComponentPopupMenu(popupMenu.getComponent());
        return new JBScrollPane(tableView);
    }

}
