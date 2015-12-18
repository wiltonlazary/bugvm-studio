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
package com.android.tools.idea.wizard;

import org.jetbrains.android.AndroidTestCase;

import java.io.File;
import java.util.Collections;

import static com.android.tools.idea.wizard.FormFactorUtils.FormFactor.MOBILE;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.PATH;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.WizardConstants.NEWLY_INSTALLED_API_KEY;

public class NewFormFactorModulePathTest extends AndroidTestCase {

  private NewFormFactorModulePath myPath;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myPath = new NewFormFactorModulePath(MOBILE, new File("/"), getTestRootDisposable());
    ScopedStateStore wizardState = new ScopedStateStore(WIZARD, null, null);
    myPath.myState = new ScopedStateStore(PATH, wizardState, null);
  }

  public void testDeriveValues_addsNewApiWhenAvailable() throws Exception {
    ScopedStateStore.Key<Integer> targetApiLevelKey = FormFactorUtils.getTargetApiLevelKey(MOBILE);
    myPath.myState.put(targetApiLevelKey, 18);
    myPath.myState.put(NEWLY_INSTALLED_API_KEY, 19);

    myPath.deriveValues(Collections.EMPTY_SET);

    assertNotNull(myPath.myState.get(targetApiLevelKey));
    assertEquals(Integer.valueOf(19), myPath.myState.get(targetApiLevelKey));
  }

  public void testDeriveValues_doesNotChangeIfLowerApiInstalled() throws Exception {

    ScopedStateStore.Key<Integer> targetApiLevelKey = FormFactorUtils.getTargetApiLevelKey(MOBILE);
    myPath.myState.put(targetApiLevelKey, 18);
    myPath.myState.put(NEWLY_INSTALLED_API_KEY, 17);

    myPath.deriveValues(Collections.EMPTY_SET);

    assertNotNull(myPath.myState.get(targetApiLevelKey));
    assertEquals(Integer.valueOf(18), myPath.myState.get(targetApiLevelKey));
  }
}