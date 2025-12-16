package com.lhstack;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowser;
import com.lhstack.constant.GlobalConst;
import com.lhstack.utils.NotifyUtils;
import com.lhstack.utils.ProjectUtils;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefMenuModel;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefDownloadHandlerAdapter;
import org.cef.handler.CefKeyboardHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefRequest;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AcmeClientView extends JPanel implements Disposable {

    private static final int MENU_OPEN_NEW_WINDOW = 100;
    private static final int MENU_OPEN_DEVTOOLS = 101;

    private final Project project;
    private final List<JBCefBrowser> browsers = new ArrayList<>();
    private String acmeHtml;

    public AcmeClientView(Project project) throws Exception {
        this.project = project;
        this.init();
    }

    private void init() throws Exception {
        this.setLayout(new BorderLayout());
        loadHtmlContent();
        JBCefBrowser browser = ProjectUtils.getOrCreate(project, GlobalConst.ACME_BROWSER, this::createBrowser);
        this.add(browser.getComponent(), BorderLayout.CENTER);
    }

    private void loadHtmlContent() {
        try (InputStream is = AcmeClientView.class.getClassLoader().getResourceAsStream("acme.html")) {
            if (is != null) {
                acmeHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } else {
                acmeHtml = "<html><body><h1>无法加载ACME客户端页面</h1></body></html>";
                NotifyUtils.notifyWarning("无法加载ACME客户端HTML资源", project);
            }
        } catch (Throwable e) {
            acmeHtml = "<html><body><h1>加载错误: " + e.getMessage() + "</h1></body></html>";
            NotifyUtils.notifyError("加载ACME客户端HTML失败: " + e.getMessage(), project);
        }
    }

    public JBCefBrowser createBrowser() {
        JBCefBrowser jbCefBrowser = JBCefBrowser.createBuilder().setOffScreenRendering(false).build();
        browsers.add(jbCefBrowser);
        CefBrowser cefBrowser = jbCefBrowser.getCefBrowser();
        
        // 加载处理器
        jbCefBrowser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
                browser.executeJavaScript("function scrollToTop() { window.scrollTo(0, 0); }", browser.getURL(), 0);
            }
        }, cefBrowser);
        
        // 下载处理器
        jbCefBrowser.getJBCefClient().addDownloadHandler(new CefDownloadHandlerAdapter() {
            @Override
            public void onBeforeDownload(CefBrowser browser, CefDownloadItem downloadItem, String suggestedName, CefBeforeDownloadCallback callback) {
                callback.Continue(suggestedName, true);
            }
        }, cefBrowser);
        
        jbCefBrowser.loadHTML(acmeHtml);
        
        // 键盘处理器 - F5刷新
        jbCefBrowser.getJBCefClient().addKeyboardHandler(new CefKeyboardHandlerAdapter() {
            @Override
            public boolean onKeyEvent(CefBrowser browser, CefKeyEvent event) {
                if (event.windows_key_code == 116 && event.type == CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN) {
                    browser.reload();
                    browser.executeJavaScript("scrollToTop();", browser.getURL(), 0);
                    return true;
                }
                return false;
            }
        }, cefBrowser);

        // 右键菜单处理器
        jbCefBrowser.getJBCefClient().addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
                model.clear();
                model.addItem(MENU_OPEN_NEW_WINDOW, "在新窗口中打开");
                model.addItem(MENU_OPEN_DEVTOOLS, "打开开发者工具");
            }

            @Override
            public boolean onContextMenuCommand(CefBrowser browser, CefFrame frame, CefContextMenuParams params, int commandId, int eventFlags) {
                if (commandId == MENU_OPEN_NEW_WINDOW) {
                    openInNewWindow();
                } else if (commandId == MENU_OPEN_DEVTOOLS) {
                    jbCefBrowser.openDevtools();
                }
                return true;
            }
        }, cefBrowser);
        
        return jbCefBrowser;
    }

    private void openInNewWindow() {
        SwingUtilities.invokeLater(() -> {
            try {
                JFrame jFrame = new JFrame("ACME客户端");
                jFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                jFrame.setSize(1024, 768);
                jFrame.setLocationRelativeTo(null);
                JBCefBrowser newBrowser = createBrowser();
                jFrame.add(newBrowser.getComponent());
                jFrame.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent e) {
                        browsers.remove(newBrowser);
                        newBrowser.dispose();
                    }
                });
                jFrame.setVisible(true);
            } catch (Throwable e) {
                NotifyUtils.notifyError("打开新窗口失败: " + e.getMessage(), project);
            }
        });
    }

    @Override
    public void dispose() {
        for (JBCefBrowser browser : browsers) {
            try {
                browser.dispose();
            } catch (Throwable ignored) {
            }
        }
        browsers.clear();
    }
}
