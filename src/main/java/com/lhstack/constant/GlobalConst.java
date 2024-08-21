package com.lhstack.constant;

import com.intellij.openapi.util.Key;
import com.intellij.ui.jcef.JBCefBrowser;

public interface GlobalConst {

    /**
     * 缓存acme浏览器
     */
    Key<JBCefBrowser> ACME_BROWSER = Key.create("ACME_BROWSER");

}
