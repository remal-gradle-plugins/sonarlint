package name.remal.gradle_plugins.sonarlint.internal.impl;

import java.util.concurrent.atomic.AtomicBoolean;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalAnalysisContainer;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;

class ExtendedGlobalAnalysisContainer extends GlobalAnalysisContainer {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public ExtendedGlobalAnalysisContainer(
        AnalysisEngineConfiguration analysisGlobalConfig,
        LoadedPlugins loadedPlugins
    ) {
        super(analysisGlobalConfig, loadedPlugins);
    }

    @Override
    public synchronized ExtendedGlobalAnalysisContainer startComponents() {
        if (stopped.get()) {
            throw new IllegalStateException("Already stopped: " + this);
        }

        if (started.compareAndSet(false, true)) {
            super.startComponents();
        }

        return this;
    }

    @Override
    public synchronized ExtendedGlobalAnalysisContainer stopComponents() {
        if (stopped.compareAndSet(false, true)) {
            if (started.get()) {
                super.stopComponents();
            }
        }

        return this;
    }

}
