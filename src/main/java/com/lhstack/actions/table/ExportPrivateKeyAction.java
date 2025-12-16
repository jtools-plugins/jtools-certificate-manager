package com.lhstack.actions.table;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ListTableModel;
import com.lhstack.FileChooser;
import com.lhstack.Icons;
import com.lhstack.Item;
import com.lhstack.utils.NotifyUtils;
import com.lhstack.utils.PemUtils;
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
            NotifyUtils.notifyWarning("请先导入证书库或创建空证书库", project);
            return;
        }

        List<Item> items = this.tableView.getSelectedObjects();
        if (items.isEmpty()) {
            NotifyUtils.notifyWarning("请选择要导出私钥的证书", project);
            return;
        }

        if (items.size() > 1) {
            NotifyUtils.notifyWarning("私钥只支持单个导出,请只选择一个证书", project);
            return;
        }

        Item item = items.get(0);
        String password = JOptionPane.showInputDialog("请输入私钥密码(如果没有密码,直接点确认或取消)");
        char[] passwordChars = (password == null || password.isEmpty()) ? new char[0] : password.toCharArray();

        try {
            Key key = keyStore.getKey(item.getName(), passwordChars);
            if (key == null) {
                NotifyUtils.notifyWarning("当前证书条目没有关联的私钥", project);
                return;
            }

            FileChooser.chooseSaveFile("导出私钥", item.getName() + "-key", project, virtualFile -> {
                String extension = Optional.ofNullable(virtualFile.getExtension()).orElse("pem");
                switch (extension.toLowerCase()) {
                    case "pem":
                        PemUtils.pemWriter(key, virtualFile.getPresentableUrl());
                        NotifyUtils.notify("私钥已导出为PEM格式", project);
                        break;
                    case "key":
                    case "der":
                        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(key.getEncoded());
                        Files.write(virtualFile.toNioPath(), pkcs8EncodedKeySpec.getEncoded());
                        NotifyUtils.notify("私钥已导出为PKCS8/DER格式", project);
                        break;
                    default:
                        NotifyUtils.notifyWarning("不支持的导出格式: " + extension, project);
                }
            }, "pem", "key", "der");
        } catch (Throwable err) {
            NotifyUtils.notifyError("私钥导出失败: " + err.getMessage(), project);
        }
    }
}
