/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.android;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.google.common.base.Strings;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.util.Collection;

import static com.android.tools.idea.gradle.util.Facets.removeAllFacetsOfType;
import static com.intellij.openapi.util.io.FileUtilRt.getRelativePath;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

/**
 * Adds the Android facet to modules imported from {@link AndroidProject}s.
 */
public class AndroidFacetModuleCustomizer implements ModuleCustomizer<IdeaAndroidProject> {

  // It is safe to use "/" instead of File.separator. JpsAndroidModule uses it.
  private static final String SEPARATOR = "/";

  @Override
  public void customizeModule(@NotNull Project project,
                              @NotNull Module module,
                              @NotNull IdeModifiableModelsProvider modelsProvider,
                              @Nullable IdeaAndroidProject androidProject) {
    if (androidProject == null) {
      removeAllFacetsOfType(AndroidFacet.ID, modelsProvider.getModifiableFacetModel(module));
    }
    else {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        configureFacet(facet, androidProject);
      }
      else {
        // Module does not have Android facet. Create one and add it.
        ModifiableFacetModel model = modelsProvider.getModifiableFacetModel(module);
        final AndroidFacetType facetType = AndroidFacet.getFacetType();
        facet = facetType.createFacet(module, AndroidFacet.NAME, facetType.createDefaultConfiguration(), null);
        model.addFacet(facet);
        configureFacet(facet, androidProject);
      }
    }
  }

  private static void configureFacet(@NotNull AndroidFacet facet, @NotNull IdeaAndroidProject ideaAndroidProject) {
    JpsAndroidModuleProperties facetState = facet.getProperties();
    facetState.ALLOW_USER_CONFIGURATION = false;

    AndroidProject delegate = ideaAndroidProject.getDelegate();
    facetState.LIBRARY_PROJECT = delegate.isLibrary();

    SourceProvider sourceProvider = delegate.getDefaultConfig().getSourceProvider();

    syncSelectedVariantAndTestArtifact(facetState, ideaAndroidProject);

    // This code needs to be modified soon. Read the TODO in getRelativePath
    File moduleDirPath = ideaAndroidProject.getRootDirPath();
    File manifestFile = sourceProvider.getManifestFile();
    facetState.MANIFEST_FILE_RELATIVE_PATH = relativePath(moduleDirPath, manifestFile);

    Collection<File> resDirs = sourceProvider.getResDirectories();
    facetState.RES_FOLDER_RELATIVE_PATH = relativePath(moduleDirPath, resDirs);

    Collection<File> assetsDirs = sourceProvider.getAssetsDirectories();
    facetState.ASSETS_FOLDER_RELATIVE_PATH = relativePath(moduleDirPath, assetsDirs);

    facet.setIdeaAndroidProject(ideaAndroidProject);
    facet.syncSelectedVariantAndTestArtifact();
  }

  private static void syncSelectedVariantAndTestArtifact(@NotNull JpsAndroidModuleProperties facetState,
                                                         @NotNull IdeaAndroidProject ideaAndroidProject) {
    String variantStoredInFacet = facetState.SELECTED_BUILD_VARIANT;
    if (!Strings.isNullOrEmpty(variantStoredInFacet) && ideaAndroidProject.getVariantNames().contains(variantStoredInFacet)) {
      ideaAndroidProject.setSelectedVariantName(variantStoredInFacet);
    }

    String testArtifactStoredInFacet = facetState.SELECTED_TEST_ARTIFACT;
    if (!Strings.isNullOrEmpty(testArtifactStoredInFacet)) {
      ideaAndroidProject.setSelectedTestArtifactName(testArtifactStoredInFacet);
    }
  }

  // We are only getting the relative path of the first file in the collection, because JpsAndroidModuleProperties only accepts one path.
  // TODO(alruiz): Change JpsAndroidModuleProperties (and callers) to use multiple paths.
  @NotNull
  private static String relativePath(@NotNull File basePath, @NotNull Collection<File> dirs) {
    return relativePath(basePath, getFirstItem(dirs));
  }

  @NotNull
  private static String relativePath(@NotNull File basePath, @Nullable File file) {
    String relativePath = null;
    if (file != null) {
      relativePath = getRelativePath(basePath, file);
    }
    if (relativePath != null && !relativePath.startsWith(SEPARATOR)) {
      return SEPARATOR + toSystemIndependentName(relativePath);
    }
    return "";
  }
}
