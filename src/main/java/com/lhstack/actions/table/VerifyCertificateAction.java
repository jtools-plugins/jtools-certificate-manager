package com.lhstack.actions.table;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ListTableModel;
import com.lhstack.Item;
import com.lhstack.components.TextFieldDialog;
import com.lhstack.utils.NotifyUtils;
import org.jetbrains.annotations.NotNull;

import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 验证证书有效性
 */
public class VerifyCertificateAction extends AnAction {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final TableView<Item> tableView;
    private final ListTableModel<Item> models;
    private final Project project;

    public VerifyCertificateAction(TableView<Item> tableView, ListTableModel<Item> models, Project project) {
        super(() -> "验证证书", com.intellij.icons.AllIcons.Actions.Checked);
        this.tableView = tableView;
        this.models = models;
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<Item> items = this.tableView.getSelectedObjects();
        if (items.isEmpty()) {
            NotifyUtils.notifyWarning("请先选择要验证的证书", project);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("                      证书验证报告\n");
        sb.append("═══════════════════════════════════════════════════════════════\n\n");

        int validCount = 0;
        int expiredCount = 0;
        int notYetValidCount = 0;

        for (Item item : items) {
            sb.append("───────────────────────────────────────────────────────────────\n");
            sb.append(String.format("证书别名: %s\n", item.getName()));

            if (item.getCertificate() instanceof X509Certificate) {
                X509Certificate x509 = (X509Certificate) item.getCertificate();
                Date now = new Date();
                Date notBefore = x509.getNotBefore();
                Date notAfter = x509.getNotAfter();

                sb.append(String.format("生效时间: %s\n", DATE_FORMAT.format(notBefore)));
                sb.append(String.format("过期时间: %s\n", DATE_FORMAT.format(notAfter)));
                sb.append(String.format("当前时间: %s\n", DATE_FORMAT.format(now)));

                try {
                    x509.checkValidity();
                    sb.append("验证结果: ✓ 有效\n");
                    validCount++;

                    // 计算剩余天数
                    long daysRemaining = (notAfter.getTime() - now.getTime()) / (1000 * 60 * 60 * 24);
                    sb.append(String.format("剩余天数: %d 天\n", daysRemaining));

                    if (daysRemaining <= 30) {
                        sb.append("警告: 证书即将在30天内过期!\n");
                    } else if (daysRemaining <= 90) {
                        sb.append("提示: 证书将在90天内过期\n");
                    }
                } catch (java.security.cert.CertificateExpiredException ex) {
                    sb.append("验证结果: ✗ 已过期\n");
                    long daysExpired = (now.getTime() - notAfter.getTime()) / (1000 * 60 * 60 * 24);
                    sb.append(String.format("已过期: %d 天\n", daysExpired));
                    expiredCount++;
                } catch (java.security.cert.CertificateNotYetValidException ex) {
                    sb.append("验证结果: ✗ 尚未生效\n");
                    long daysUntilValid = (notBefore.getTime() - now.getTime()) / (1000 * 60 * 60 * 24);
                    sb.append(String.format("距离生效: %d 天\n", daysUntilValid));
                    notYetValidCount++;
                }

                // 验证签名算法
                sb.append(String.format("签名算法: %s\n", x509.getSigAlgName()));

                // 检查是否为自签名证书
                boolean isSelfSigned = x509.getSubjectX500Principal().equals(x509.getIssuerX500Principal());
                sb.append(String.format("自签名: %s\n", isSelfSigned ? "是" : "否"));
            } else {
                sb.append("验证结果: 无法验证(非X509证书)\n");
            }
            sb.append("\n");
        }

        // 汇总
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("                        验证汇总\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append(String.format("总计: %d 个证书\n", items.size()));
        sb.append(String.format("有效: %d 个\n", validCount));
        sb.append(String.format("已过期: %d 个\n", expiredCount));
        sb.append(String.format("尚未生效: %d 个\n", notYetValidCount));

        new TextFieldDialog("证书验证报告", sb.toString(), project).setVisible(true);
    }
}
