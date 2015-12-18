/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.java;

import com.android.tools.idea.gradle.IdeaJavaProject;
import com.android.tools.idea.gradle.JavaModel;
import com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer;
import com.android.tools.idea.gradle.dependency.DependencySetupErrors;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.facet.JavaGradleFacetConfiguration;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.model.java.JarLibraryDependency;
import com.android.tools.idea.gradle.model.java.JavaModuleDependency;
import com.google.common.collect.Lists;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.util.Projects.isGradleProjectModule;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.util.io.FileUtil.*;
import static java.util.Collections.singletonList;

public class DependenciesModuleCustomizer extends AbstractDependenciesModuleCustomizer<IdeaJavaProject> {
  private static final DependencyScope DEFAULT_DEPENDENCY_SCOPE = COMPILE;

  @Override
  protected void setUpDependencies(@NotNull Module module,
                                   @NotNull IdeModifiableModelsProvider modelsProvider,
                                   @NotNull IdeaJavaProject javaProject) {

    final ModifiableRootModel moduleModel = modelsProvider.getModifiableRootModel(module);
    List<String> unresolved = Lists.newArrayList();
    for (JavaModuleDependency dependency : javaProject.getJavaModuleDependencies()) {
      updateDependency(module, modelsProvider, dependency);
    }

    for (JarLibraryDependency dependency : javaProject.getJarLibraryDependencies()) {
      if (dependency.isResolved()) {
        updateDependency(module, modelsProvider, dependency);
      }
      else {
        unresolved.add(dependency.getName());
      }
    }

    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(moduleModel.getProject());
    messages.reportUnresolvedDependencies(unresolved, module);

    JavaGradleFacet facet = setAndGetJavaGradleFacet(module);
    File buildFolderPath = javaProject.getBuildFolderPath();
    if (!isGradleProjectModule(module)) {
      JavaModel javaModel = new JavaModel(unresolved, buildFolderPath);
      facet.setJavaModel(javaModel);
    }
    JavaGradleFacetConfiguration facetProperties = facet.getConfiguration();
    facetProperties.BUILD_FOLDER_PATH = buildFolderPath != null ? toSystemIndependentName(buildFolderPath.getPath()) : "";
    facetProperties.BUILDABLE = javaProject.isBuildable();
  }

  private void updateDependency(@NotNull Module module,
                                @NotNull IdeModifiableModelsProvider modelsProvider,
                                @NotNull JavaModuleDependency dependency) {
    DependencySetupErrors setupErrors = getSetupErrors(module.getProject());

    String moduleName = dependency.getModuleName();
    Module found = null;
    for (Module m : modelsProvider.getModules()) {
      if (moduleName.equals(m.getName())) {
        found = m;
      }
    }

    final ModifiableRootModel moduleModel = modelsProvider.getModifiableRootModel(module);
    if (found != null) {
      ModuleOrderEntry orderEntry = moduleModel.addModuleOrderEntry(found);
      orderEntry.setExported(true);
      DependencyScope scope = parseScope(dependency.getScope());
      orderEntry.setScope(scope);
      return;
    }
    setupErrors.addMissingModule(moduleName, module.getName(), null);
  }

  private void updateDependency(@NotNull Module module,
                                @NotNull IdeModifiableModelsProvider modelsProvider,
                                @NotNull JarLibraryDependency dependency) {
    DependencyScope scope = parseScope(dependency.getScope());
    File binaryPath = dependency.getBinaryPath();
    if (binaryPath == null) {
      DependencySetupErrors setupErrors = getSetupErrors(module.getProject());
      setupErrors.addMissingBinaryPath(module.getName());
      return;
    }
    String path = binaryPath.getPath();

    // Gradle API doesn't provide library name at the moment.
    String name = binaryPath.isFile() ? getNameWithoutExtension(binaryPath) : sanitizeFileName(path);
    setUpLibraryDependency(module, modelsProvider, name, scope, singletonList(path), asPaths(dependency.getSourcePath()),
                           asPaths(dependency.getJavadocPath()));
  }

  @NotNull
  private static List<String> asPaths(@Nullable File file) {
    return file == null ? Collections.<String>emptyList() : singletonList(file.getPath());
  }

  @NotNull
  private static DependencyScope parseScope(@Nullable String scope) {
    if (scope == null) {
      return DEFAULT_DEPENDENCY_SCOPE;
    }
    for (DependencyScope dependencyScope : DependencyScope.values()) {
      if (scope.equalsIgnoreCase(dependencyScope.toString())) {
        return dependencyScope;
      }
    }
    return DEFAULT_DEPENDENCY_SCOPE;
  }

  @NotNull
  private static JavaGradleFacet setAndGetJavaGradleFacet(Module module) {
    JavaGradleFacet facet = JavaGradleFacet.getInstance(module);
    if (facet != null) {
      return facet;
    }

    // Module does not have Android-Gradle facet. Create one and add it.
    FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    try {
      facet = facetManager.createFacet(JavaGradleFacet.getFacetType(), JavaGradleFacet.NAME, null);
      model.addFacet(facet);
    }
    finally {
      model.commit();
    }
    return facet;
  }
}
