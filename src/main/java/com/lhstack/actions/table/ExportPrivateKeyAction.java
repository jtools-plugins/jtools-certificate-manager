package com.lhstack.actions.table;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ListTableModel;
import com.lhstack.FileChooser;
import com.lhstack.Icons;
import com.lhstack.Item;
import com.lhstack.utils.NotifyUtils;
import com.lhstack.utils.PemUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.nio.file.Files;
import java.security.Key;
import java.security.KeyStore;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class ExportPrivateKeyAction extends AnAction {

    private final TableView<Item> tableView;

    private final ListTableModel<Item> models;

    private final Project project;

    private final Supplier<KeyStore> keyStoreSupplier;
    private final Supplier<char[]> passwordSupplier;

    public ExportPrivateKeyAction(TableView<Item> tableView, ListTableModel<Item> models, Project project, Supplier<KeyStore> keyStoreSupplier, Supplier<char[]> passwordSupplier) {
        super(() -> "导出私钥", Icons.EXPORT);
        this.tableView = tableView;
        this.models = models;
        this.project = project;
        this.keyStoreSupplier = keyStoreSupplier;
        this.passwordSupplier = passwordSupplier;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        KeyStore keyStore = keyStoreSupplier.get();
        if (keyStore == null) {
            Messages.showErrorDialog("请先导入jks证书或者创建空证书", "提示");
            return;
        }
        List<Item> items = this.tableView.getSelectedObjects();
        if (CollectionUtils.isNotEmpty(items)) {
            if (items.size() > 1) {
                Messages.showErrorDialog("私钥文件只支持单个导出", "提示");
                return;
            }
            if (items.isEmpty()) {
                Messages.showErrorDialog("请选择要导出私钥的证书", "提示");
                return;
            }
            Item item = items.get(0);
            String password = JOptionPane.showInputDialog("请输入被导出的私钥密码,没有密码点确认或者取消即可");
            if (StringUtils.isBlank(password)) {
                password = "";
            }
            try {
                Key key = keyStore.getKey(item.getName(), password.toCharArray());
                if (key == null) {
                    Messages.showInfoMessage("当前证书没有私钥", "提示");
                    return;
                }
                FileChooser.chooseSaveFile("导出私钥", item.getName(), project, virtualFile -> {
                    String extension = Optional.ofNullable(virtualFile.getExtension()).orElse("");
                    switch (extension) {
                        case "pem": {
                            PemUtils.pemWriter(key, virtualFile.getPresentableUrl());
                        }
                        break;
                        case "key": {
                            PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(key.getEncoded());
                            Files.write(virtualFile.toNioPath(), pkcs8EncodedKeySpec.getEncoded());
                        }
                        break;
                    }
                }, "pem", "key");
            } catch (Throwable err) {
                NotifyUtils.notify("私钥导出失败,错误信息: " + err.getMessage(), project);
            }
        }
    }
}
