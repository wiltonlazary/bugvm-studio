/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.BuildVariantsToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.fest.assertions.Assertions.assertThat;
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class BuildVariantsTest extends GuiTestCase {
  private static final String MODULE_NAME = "app";

  @Test @IdeGuiTest
  public void testSwitchVariantWithFlavor() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("Flavoredlib");

    BuildVariantsToolWindowFixture buildVariants = projectFrame.getBuildVariantsWindow();
    buildVariants.selectVariantForModule(MODULE_NAME, "flavor1Release");

    String generatedSourceDirPath = MODULE_NAME + "/build/generated/source/";

    Collection<String> sourceFolders = projectFrame.getSourceFolderRelativePaths(MODULE_NAME, SOURCE);
    assertThat(sourceFolders).contains(generatedSourceDirPath + "r/flavor1/release",
                                       generatedSourceDirPath + "aidl/flavor1/release",
                                       generatedSourceDirPath + "buildConfig/flavor1/release",
                                       generatedSourceDirPath + "rs/flavor1/release",
                                       MODULE_NAME + "/src/flavor1Release/aidl",
                                       MODULE_NAME + "/src/flavor1Release/java",
                                       MODULE_NAME + "/src/flavor1Release/jni",
                                       MODULE_NAME + "/src/flavor1Release/rs");

    Module appModule = projectFrame.getModule(MODULE_NAME);
    AndroidFacet androidFacet = AndroidFacet.getInstance(appModule);
    assertNotNull(androidFacet);

    JpsAndroidModuleProperties androidFacetProperties = androidFacet.getProperties();
    assertEquals("assembleFlavor1Release", androidFacetProperties.ASSEMBLE_TASK_NAME);
    // 'release' variant does not have the _android_test_ artifact.
    assertEquals("", androidFacetProperties.ASSEMBLE_TEST_TASK_NAME);

    buildVariants.selectVariantForModule(MODULE_NAME, "flavor1Debug");

    sourceFolders = projectFrame.getSourceFolderRelativePaths(MODULE_NAME, SOURCE);
    assertThat(sourceFolders).contains(generatedSourceDirPath + "r/flavor1/debug", generatedSourceDirPath + "aidl/flavor1/debug",
                                       generatedSourceDirPath + "buildConfig/flavor1/debug", generatedSourceDirPath + "rs/flavor1/debug",
                                       MODULE_NAME + "/src/flavor1Debug/aidl", MODULE_NAME + "/src/flavor1Debug/java",
                                       MODULE_NAME + "/src/flavor1Debug/jni", MODULE_NAME + "/src/flavor1Debug/rs");

    assertEquals("assembleFlavor1Debug", androidFacetProperties.ASSEMBLE_TASK_NAME);
    // Verifies that https://code.google.com/p/android/issues/detail?id=83077 is not a bug.
    assertEquals("assembleFlavor1DebugAndroidTest", androidFacetProperties.ASSEMBLE_TEST_TASK_NAME);
  }

  @Test @IdeGuiTest
  public void switchingTestArtifacts() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("SimpleApplicationWithUnitTests");
    BuildVariantsToolWindowFixture buildVariants = projectFrame.getBuildVariantsWindow();
    buildVariants.activate();

    String androidTestSrc = MODULE_NAME + "/src/androidTest/java";
    String unitTestSrc = MODULE_NAME + "/src/test/java";

    Collection<String> testSourceFolders = projectFrame.getSourceFolderRelativePaths(MODULE_NAME, TEST_SOURCE);
    assertThat(testSourceFolders).contains(androidTestSrc).excludes(unitTestSrc);

    buildVariants.selectTestArtifact("Unit Tests");

    testSourceFolders = projectFrame.getSourceFolderRelativePaths(MODULE_NAME, TEST_SOURCE);
    assertThat(testSourceFolders).contains(unitTestSrc).excludes(androidTestSrc);
  }
}
