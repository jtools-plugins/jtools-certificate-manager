package com.lhstack.actions.table;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ListTableModel;
import com.lhstack.FileChooser;
import com.lhstack.Icons;
import com.lhstack.Item;
import com.lhstack.utils.CertificateUtils;
import com.lhstack.utils.NotifyUtils;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.security.KeyStore;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class ExportCertificateAction extends AnAction {

    private final TableView<Item> tableView;
    private final ListTableModel<Item> models;
    private final Project project;
    private final Supplier<KeyStore> keyStoreSupplier;
    private final Supplier<char[]> passwordSupplier;

    public ExportCertificateAction(TableView<Item> tableView, ListTableModel<Item> models, Project project, Supplier<KeyStore> keyStoreSupplier, Supplier<char[]> passwordSupplier) {
        super(() -> "导出证书", Icons.EXPORT);
        this.tableView = tableView;
        this.models = models;
        this.project = project;
        this.keyStoreSupplier = keyStoreSupplier;
        this.passwordSupplier = passwordSupplier;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<Item> items = this.tableView.getSelectedObjects();
        if (items.isEmpty()) {
            NotifyUtils.notifyWarning("请先选择要导出的证书", project);
            return;
        }

        KeyStore keyStore = keyStoreSupplier.get();
        if (keyStore == null) {
            NotifyUtils.notifyWarning("请先导入或创建证书库", project);
            return;
        }

        String saveFilename = items.size() == 1 ? items.get(0).getName() : "certificates";

        FileChooser.chooseSaveFile("导出证书", saveFilename, project, virtualFile -> {
            try {
                String extension = Optional.ofNullable(virtualFile.getExtension()).orElse("jks");
                byte[] data = CertificateUtils.export(keyStoreSupplier, passwordSupplier, items, extension);
                FileUtils.writeByteArrayToFile(new File(virtualFile.getPresentableUrl()), data);
                NotifyUtils.notify(String.format("成功导出 %d 个证书", items.size()), project);
            } catch (Throwable err) {
                FileUtil.delete(new File(virtualFile.getPresentableUrl()));
                NotifyUtils.notifyError("证书导出错误: " + err.getMessage(), project);
            }
        }, "pem", "jks", "p12", "crt", "cer", "der", "p7b");
    }
}
