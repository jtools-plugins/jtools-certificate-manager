package com.lhstack.components;

import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class TextFieldDialog extends JDialog {

    private final String content;

    public TextFieldDialog(String title, String text, Project project) {
        this.content = text;
        this.setTitle(title);
        this.setModal(true);
        this.setSize(900, 650);
        this.setLocationRelativeTo(null);
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout());

        LanguageTextField languageTextField = new LanguageTextField(PlainTextLanguage.INSTANCE, project, text, false) {
            @Override
            protected @NotNull EditorEx createEditor() {
                EditorEx editor = super.createEditor();
                EditorSettings settings = editor.getSettings();
                settings.setLineMarkerAreaShown(true);
                settings.setLineNumbersShown(true);
                settings.setUseSoftWraps(true);
                settings.setFoldingOutlineShown(true);
                return editor;
            }
        };
        languageTextField.setEnabled(false);
        mainPanel.add(new JBScrollPane(languageTextField), BorderLayout.CENTER);

        // 底部按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton copyButton = new JButton("复制内容");
        copyButton.addActionListener(e -> copyToClipboard());
        JButton closeButton = new JButton("关闭");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(copyButton);
        buttonPanel.add(closeButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        this.setContentPane(mainPanel);

        // ESC键关闭
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // 窗口关闭时释放资源
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                // 清理资源
            }
        });
    }

    private void copyToClipboard() {
        if (content != null && !content.isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(content), null);
        }
    }
}
