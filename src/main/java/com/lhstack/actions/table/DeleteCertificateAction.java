package com.lhstack.actions.table;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ListTableModel;
import com.lhstack.Icons;
import com.lhstack.Item;
import com.lhstack.utils.NotifyUtils;
import org.jetbrains.annotations.NotNull;

import java.security.KeyStore;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DeleteCertificateAction extends AnAction {

    private final TableView<Item> tableView;
    private final ListTableModel<Item> models;
    private final Project project;
    private final Supplier<KeyStore> keyStoreSupplier;
    private final Runnable refreshAction;

    public DeleteCertificateAction(TableView<Item> tableView, ListTableModel<Item> models, Project project, Supplier<KeyStore> keyStoreSupplier, Runnable refreshAction) {
        super(() -> "删除证书", Icons.DELETE);
        this.tableView = tableView;
        this.models = models;
        this.project = project;
        this.keyStoreSupplier = keyStoreSupplier;
        this.refreshAction = refreshAction;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<Item> items = this.tableView.getSelectedObjects();
        if (items.isEmpty()) {
            NotifyUtils.notifyWarning("请先选择要删除的证书", project);
            return;
        }

        KeyStore keyStore = keyStoreSupplier.get();
        if (keyStore == null) {
            NotifyUtils.notifyWarning("请先导入或创建证书库", project);
            return;
        }

        String certNames = items.stream()
                .map(Item::getName)
                .collect(Collectors.joining(", "));

        int result = Messages.showYesNoDialog(
                project,
                String.format("确定要删除以下 %d 个证书吗?\n\n%s", items.size(), certNames),
                "确认删除",
                "删除",
                "取消",
                Messages.getWarningIcon()
        );

        if (result == Messages.YES) {
            try {
                int deletedCount = 0;
                for (Item item : items) {
                    keyStore.deleteEntry(item.getName());
                    deletedCount++;
                }
                refreshAction.run();
                NotifyUtils.notify(String.format("成功删除 %d 个证书", deletedCount), project);
            } catch (Throwable err) {
                NotifyUtils.notifyError("删除证书失败: " + err.getMessage(), project);
            }
        }
    }
}
