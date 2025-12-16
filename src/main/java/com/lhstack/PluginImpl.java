package com.lhstack;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextArea;
import com.lhstack.tools.plugins.IPlugin;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PluginImpl implements IPlugin {

    private static final Map<String, JPanel> panels = new ConcurrentHashMap<>();

    private static final Map<String, List<Disposable>> disposables = new ConcurrentHashMap<>();

    @Override
    public Icon pluginIcon() {
        return IconLoader.findIcon("logo.svg", PluginImpl.class);
    }

    @Override
    public Icon pluginTabIcon() {
        return IconLoader.findIcon("pluginTab.svg", PluginImpl.class);
    }

    @Override
    public void unInstall() {
        disposables.values().forEach(list -> list.forEach(this::safeDispose));
        disposables.clear();
        panels.clear();
    }

    @Override
    public JComponent createPanel(Project project) {
        return panels.computeIfAbsent(project.getLocationHash(), key -> createViews(project));
    }

    private JPanel createViews(Project project) {
        try {
            JBTabbedPane tabbedPane = new JBTabbedPane();
            List<Disposable> projectDisposables = new ArrayList<>();
            
            CertificateManagerView certificateManagerView = new CertificateManagerView(project);
            projectDisposables.add(certificateManagerView);
            
            CreateSelfCertificateView createSelfCertificateView = new CreateSelfCertificateView(project);
            projectDisposables.add(createSelfCertificateView);
            
            CertificateMonitorView certificateMonitorView = new CertificateMonitorView(project);
            projectDisposables.add(certificateMonitorView);
            
            AcmeClientView acmeClientView = new AcmeClientView(project);
            projectDisposables.add(acmeClientView);
            
            disposables.put(project.getLocationHash(), projectDisposables);
            
            tabbedPane.addTab("证书库管理", certificateManagerView);
            tabbedPane.addTab("自签证书", createSelfCertificateView);
            tabbedPane.addTab("证书监控", certificateMonitorView);
            tabbedPane.addTab("ACME客户端", acmeClientView);
            
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(tabbedPane, BorderLayout.CENTER);
            return panel;
        } catch (Throwable e) {
            JPanel jPanel = new JPanel(new BorderLayout());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            jPanel.add(new JBScrollPane(new JBTextArea(sw.toString())), BorderLayout.CENTER);
            return jPanel;
        }
    }

    private void safeDispose(Disposable disposable) {
        if (disposable != null) {
            try {
                disposable.dispose();
            } catch (Throwable ignored) {
                // 忽略dispose时的异常
            }
        }
    }

    @Override
    public void closeProject(String projectHash) {
        panels.remove(projectHash);
        List<Disposable> projectDisposables = disposables.remove(projectHash);
        if (projectDisposables != null) {
            projectDisposables.forEach(this::safeDispose);
        }
    }

    @Override
    public String pluginName() {
        return "证书管理";
    }

    @Override
    public String pluginDesc() {
        return "这是一个用于管理证书的插件";
    }

    @Override
    public String pluginVersion() {
        return "v2.0.3";
    }
}
