package com.lhstack.actions.self;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.lhstack.Icons;
import com.lhstack.utils.NotifyUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.function.Supplier;

/**
 * 复制内容到剪贴板的Action
 */
public class CopyToClipboardAction extends AnAction {

    private final Supplier<String> contentSupplier;
    private final Project project;
    private final String contentName;

    public CopyToClipboardAction(String contentName, Supplier<String> contentSupplier, Project project) {
        super(() -> "复制到剪贴板", com.intellij.icons.AllIcons.Actions.Copy);
        this.contentSupplier = contentSupplier;
        this.project = project;
        this.contentName = contentName;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        String content = contentSupplier.get();
        if (StringUtils.isBlank(content)) {
            NotifyUtils.notifyWarning(contentName + "内容为空,请先生成或导入", project);
            return;
        }
        
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(content), null);
            NotifyUtils.notify(contentName + "已复制到剪贴板", project);
        } catch (Throwable e) {
            NotifyUtils.notifyError("复制失败: " + e.getMessage(), project);
        }
    }
}
