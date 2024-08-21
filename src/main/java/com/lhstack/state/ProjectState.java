package com.lhstack.state;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@State(name = "data", storages = {@Storage("CoolRequestCertificateManager.xml")})
@Service
public final class ProjectState implements PersistentStateComponent<ProjectState.State> {

    private @NotNull ProjectState.State state = new State();

    /**
     * 全局
     *
     * @return
     */
    public static ProjectState getInstance() {
        return ApplicationManager.getApplication().getService(ProjectState.class);
    }

    /**
     * 项目
     *
     * @param project
     * @return
     */
    public static ProjectState getInstance(Project project) {
        return project.getService(ProjectState.class);
//        return new ProjectState();
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

        private String configYaml;

        private String caPem;

        private String caKeyPem;

        private String certificatePem;

        private String certificateKeyPem;

        public String getCaPem() {
            return caPem;
        }

        public State setCaPem(String caPem) {
            this.caPem = caPem;
            return this;
        }

        public String getCaKeyPem() {
            return caKeyPem;
        }

        public State setCaKeyPem(String caKeyPem) {
            this.caKeyPem = caKeyPem;
            return this;
        }

        public String getCertificatePem() {
            return certificatePem;
        }

        public State setCertificatePem(String certificatePem) {
            this.certificatePem = certificatePem;
            return this;
        }

        public String getCertificateKeyPem() {
            return certificateKeyPem;
        }

        public State setCertificateKeyPem(String certificateKeyPem) {
            this.certificateKeyPem = certificateKeyPem;
            return this;
        }

        public String getConfigYaml() {
            return configYaml;
        }

        public void setConfigYaml(String configYaml) {
            this.configYaml = configYaml;
        }
    }
}
