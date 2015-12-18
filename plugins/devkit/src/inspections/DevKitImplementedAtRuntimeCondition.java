/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.inheritance.ImplementedAtRuntimeCondition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class DevKitImplementedAtRuntimeCondition extends ImplementedAtRuntimeCondition {
  @Override
  public boolean isImplementedAtRuntime(@NotNull PsiClass psiClass) {
    return DevKitImplicitUsageProvider.isDomElementClass(psiClass) ||
           InheritanceUtil.isInheritor(psiClass, "com.intellij.jam.JamElement");
  }
}
