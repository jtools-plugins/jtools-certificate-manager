package com.lhstack.actions.table;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ListTableModel;
import com.lhstack.Icons;
import com.lhstack.Item;
import com.lhstack.components.TextFieldDialog;
import com.lhstack.utils.NotifyUtils;
import org.jetbrains.annotations.NotNull;

import java.security.cert.X509Certificate;
import java.util.List;

public class ShowDetailAction extends AnAction {

    private final TableView<Item> tableView;
    private final ListTableModel<Item> models;
    private final Project project;

    public ShowDetailAction(TableView<Item> tableView, ListTableModel<Item> models, Project project) {
        super(() -> "查看详情", Icons.DETAIL);
        this.tableView = tableView;
        this.models = models;
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<Item> items = this.tableView.getSelectedObjects();
        if (items.isEmpty()) {
            NotifyUtils.notifyWarning("请先选择要查看的证书", project);
            return;
        }

        Item firstItem = items.get(0);
        String title = String.format("证书详情 - %s", firstItem.getName());
        StringBuilder sb = new StringBuilder();

        for (Item item : items) {
            sb.append("═══════════════════════════════════════════════════════════════\n");
            sb.append(String.format("证书别名: %s\n", item.getName()));
            sb.append(String.format("证书类型: %s\n", item.getType()));
            sb.append(String.format("加密算法: %s\n", item.getAlgorithm()));
            sb.append(String.format("证书状态: %s\n", item.getStatus()));

            if (item.getCertificate() instanceof X509Certificate) {
                X509Certificate x509 = (X509Certificate) item.getCertificate();
                sb.append("───────────────────────────────────────────────────────────────\n");
                sb.append(String.format("主题(Subject): %s\n", x509.getSubjectX500Principal().getName()));
                sb.append(String.format("颁发者(Issuer): %s\n", x509.getIssuerX500Principal().getName()));
                sb.append(String.format("序列号: %s\n", x509.getSerialNumber().toString(16).toUpperCase()));
                sb.append(String.format("签名算法: %s\n", x509.getSigAlgName()));
                sb.append(String.format("版本: V%d\n", x509.getVersion()));
                sb.append(String.format("生效时间: %s\n", item.getNotBeforeFormatted()));
                sb.append(String.format("过期时间: %s\n", item.getNotAfterFormatted()));

                // 公钥信息
                sb.append("───────────────────────────────────────────────────────────────\n");
                sb.append(String.format("公钥算法: %s\n", x509.getPublicKey().getAlgorithm()));
                sb.append(String.format("公钥格式: %s\n", x509.getPublicKey().getFormat()));
            }

            sb.append("───────────────────────────────────────────────────────────────\n");
            sb.append("完整证书信息:\n");
            sb.append(item.getCertificate().toString());
            sb.append("\n\n");
        }

        new TextFieldDialog(title, sb.toString(), project).setVisible(true);
    }
}
