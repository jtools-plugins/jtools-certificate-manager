package com.lhstack.actions.table;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ListTableModel;
import com.lhstack.Item;
import com.lhstack.utils.NotifyUtils;
import com.lhstack.utils.PemUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;

/**
 * 复制证书PEM内容到剪贴板
 */
public class CopyCertificateAction extends AnAction {

    private final TableView<Item> tableView;
    private final ListTableModel<Item> models;
    private final Project project;

    public CopyCertificateAction(TableView<Item> tableView, ListTableModel<Item> models, Project project) {
        super(() -> "复制证书(PEM)", com.intellij.icons.AllIcons.Actions.Copy);
        this.tableView = tableView;
        this.models = models;
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<Item> items = this.tableView.getSelectedObjects();
        if (items.isEmpty()) {
            NotifyUtils.notifyWarning("请先选择要复制的证书", project);
            return;
        }

        try {
            StringBuilder sb = new StringBuilder();
            for (Item item : items) {
                if (item.getCertificate() != null) {
                    sb.append(PemUtils.toString(item.getCertificate()));
                }
            }
            
            if (sb.length() > 0) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(sb.toString()), null);
                NotifyUtils.notify(String.format("已复制 %d 个证书到剪贴板", items.size()), project);
            }
        } catch (Throwable err) {
            NotifyUtils.notifyError("复制失败: " + err.getMessage(), project);
        }
    }
}
