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
import org.apache.commons.collections.CollectionUtils;
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
        if (CollectionUtils.isNotEmpty(items)) {
            String saveFilename;
            if (items.size() == 1) {
                Item item = items.get(0);
                saveFilename = item.getName();
            } else {
                saveFilename = "multi-certificate";
            }
            FileChooser.chooseSaveFile("导出证书", saveFilename, project, virtualFile -> {
                //如果没有文件,则创建
                try {
                    String extension = Optional.ofNullable(virtualFile.getExtension()).orElse("");
                    FileUtils.writeByteArrayToFile(new File(virtualFile.getPresentableUrl()), CertificateUtils.export(keyStoreSupplier, passwordSupplier, items, extension));
                    NotifyUtils.notify("导出已选证书成功", project);
                } catch (Throwable err) {
                    FileUtil.delete(new File(virtualFile.getPresentableUrl()));
                    NotifyUtils.notify("证书导出错误: " + err.getMessage(), project);
                }
            }, "pem", "jks", "crt", "cer");

        }
    }
}
