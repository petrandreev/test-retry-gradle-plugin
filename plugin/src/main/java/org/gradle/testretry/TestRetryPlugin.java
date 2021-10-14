/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.testretry;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testretry.internal.config.DefaultTestRetryTaskExtension;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import static org.gradle.testretry.internal.config.TestTaskConfigurer.configureTestTask;

public class TestRetryPlugin implements Plugin<Object> {

    private final ObjectFactory objectFactory;
    private final ProviderFactory providerFactory;

    @Inject
    TestRetryPlugin(ObjectFactory objectFactory, ProviderFactory providerFactory) {
        this.objectFactory = objectFactory;
        this.providerFactory = providerFactory;
    }

    @Override
    public void apply(@NotNull Object target) {
        if (target instanceof Settings) {
            applySettingsPlugin((Settings) target);
        } else if (target instanceof Project) {
            Project project = (Project) target;
            if (pluginAlreadyApplied(project)) {
                return;
            }
            project.getTasks()
                .withType(Test.class)
                .configureEach(task -> configureTestTask(task, objectFactory, providerFactory));
        }
    }

    private void applySettingsPlugin(Settings settings) {
        TestRetryTaskExtension testRetryTaskExtension = objectFactory.newInstance(DefaultTestRetryTaskExtension.class);
        settings.getExtensions().add(TestRetryTaskExtension.class, TestRetryTaskExtension.NAME, testRetryTaskExtension);
        settings.getGradle().beforeProject(project -> {
            project.getPluginManager().apply("org.gradle.test-retry");
            project.getTasks().withType(Test.class).configureEach(test -> {
                TestRetryTaskExtension retryExtension = test.getExtensions().getByType(TestRetryTaskExtension.class);
                retryExtension.getMaxRetries().convention(testRetryTaskExtension.getMaxRetries());
                retryExtension.getMaxFailures().convention(testRetryTaskExtension.getMaxFailures());
                retryExtension.getFailOnPassedAfterRetry().convention(testRetryTaskExtension.getFailOnPassedAfterRetry());
                retryExtension.getFilter().getExcludeAnnotationClasses()
                    .convention(testRetryTaskExtension.getFilter().getExcludeAnnotationClasses());
                retryExtension.getFilter().getIncludeAnnotationClasses()
                    .convention(testRetryTaskExtension.getFilter().getIncludeAnnotationClasses());
                retryExtension.getFilter().getExcludeClasses()
                    .convention(testRetryTaskExtension.getFilter().getExcludeClasses());
                retryExtension.getFilter().getIncludeClasses()
                    .convention(testRetryTaskExtension.getFilter().getIncludeClasses());
            });
        });
    }

    private static boolean pluginAlreadyApplied(Project project) {
        return project.getPlugins().stream().anyMatch(plugin -> plugin.getClass().getName().equals(TestRetryPlugin.class.getName()));
    }
}
