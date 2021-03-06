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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

public class DescriptionCheckerUtil {

  public static PsiDirectory[] getDescriptionsDirs(Module module,
                                                   DescriptionType descriptionType) {
    final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(module.getProject());
    final PsiPackage psiPackage = javaPsiFacade.findPackage(descriptionType.getDescriptionFolder());
    if (psiPackage != null) {
      return psiPackage.getDirectories(GlobalSearchScope.moduleWithDependenciesScope(module));
    }
    return PsiDirectory.EMPTY_ARRAY;
  }

  @Nullable
  public static String getDescriptionDirName(PsiClass aClass) {
    String descriptionDir = "";
    PsiClass each = aClass;
    while (each != null) {
      String name = each.getName();
      if (StringUtil.isEmptyOrSpaces(name)) {
        return null;
      }
      descriptionDir = name + descriptionDir;
      each = each.getContainingClass();
    }
    return descriptionDir;
  }
}
