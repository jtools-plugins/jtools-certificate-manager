package com.lhstack.state;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class ProjectState implements PersistentStateComponent<ProjectState.State> {

    private ProjectState.State state;

    public ProjectState(Project project) {
        state = new State(PropertiesComponent.getInstance(project));
    }

    /**
     * 项目
     *
     * @param project
     * @return
     */
    public static ProjectState getInstance(Project project) {
        return new ProjectState(project);
    }

    @Override
    public @NotNull ProjectState.State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public static class State {

        private static final String KEY_PREFIX = "JTools.Certificate.Manager.";

        private final PropertiesComponent propertiesComponent;

        public State(PropertiesComponent propertiesComponent) {
            this.propertiesComponent = propertiesComponent;
        }

        public String getCaPem() {
            return propertiesComponent.getValue(KEY_PREFIX + "caPem");
        }

        public State setCaPem(String caPem) {
            propertiesComponent.setValue(KEY_PREFIX + "caPem", caPem);
            return this;
        }

        public String getCaKeyPem() {
            return propertiesComponent.getValue(KEY_PREFIX + "caKeyPem");
        }

        public State setCaKeyPem(String caKeyPem) {
            this.propertiesComponent.setValue(KEY_PREFIX + "caKeyPem", caKeyPem);
            return this;
        }

        public String getCertificatePem() {
            return this.propertiesComponent.getValue(KEY_PREFIX + "certificatePem");
        }

        public State setCertificatePem(String certificatePem) {
            this.propertiesComponent.setValue(KEY_PREFIX + "certificatePem", certificatePem);
            return this;
        }

        public String getCertificateKeyPem() {
            return this.propertiesComponent.getValue(KEY_PREFIX + "certificateKeyPem");
        }

        public State setCertificateKeyPem(String certificateKeyPem) {
            this.propertiesComponent.setValue(KEY_PREFIX + "certificateKeyPem", certificateKeyPem);
            return this;
        }

        public String getConfigYaml() {
            return this.propertiesComponent.getValue(KEY_PREFIX + "configYaml");
        }

        public void setConfigYaml(String configYaml) {
            this.propertiesComponent.setValue(KEY_PREFIX + "configYaml", configYaml);
        }
    }
}
