package com.lhstack.state;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 证书监控状态持久化
 */
public final class MonitorState implements PersistentStateComponent<MonitorState.State> {

    private MonitorState.State state;

    public MonitorState(Project project) {
        state = new State(PropertiesComponent.getInstance(project));
    }

    public static MonitorState getInstance(Project project) {
        return new MonitorState(project);
    }

    @Override
    public @NotNull MonitorState.State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public static class State {

        private static final String KEY_PREFIX = "JTools.Certificate.Monitor.";

        private final PropertiesComponent propertiesComponent;

        public State(PropertiesComponent propertiesComponent) {
            this.propertiesComponent = propertiesComponent;
        }

        /**
         * 获取监控的域名列表 (格式: domain1:port1;domain2:port2)
         */
        public String getMonitorDomains() {
            return propertiesComponent.getValue(KEY_PREFIX + "domains");
        }

        /**
         * 设置监控的域名列表
         */
        public void setMonitorDomains(String domains) {
            propertiesComponent.setValue(KEY_PREFIX + "domains", domains);
        }

        /**
         * 获取自动刷新间隔(分钟)
         */
        public int getRefreshInterval() {
            return propertiesComponent.getInt(KEY_PREFIX + "refreshInterval", 60);
        }

        /**
         * 设置自动刷新间隔(分钟)
         */
        public void setRefreshInterval(int interval) {
            propertiesComponent.setValue(KEY_PREFIX + "refreshInterval", interval, 60);
        }

        /**
         * 获取过期提醒天数
         */
        public int getExpiryWarningDays() {
            return propertiesComponent.getInt(KEY_PREFIX + "expiryWarningDays", 30);
        }

        /**
         * 设置过期提醒天数
         */
        public void setExpiryWarningDays(int days) {
            propertiesComponent.setValue(KEY_PREFIX + "expiryWarningDays", days, 30);
        }
    }
}
