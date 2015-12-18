/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author peter
 */
class MakeInferredAnnotationExplicitTest extends LightCodeInsightFixtureTestCase {

  public void "test contract and notNull"() {
    myFixture.configureByText 'a.java', '''
class Foo {
    static String f<caret>oo() {
        return "s";
    }
}
'''
    myFixture.launchAction(myFixture.findSingleIntention("Insert '@Contract(pure = true) @NotNull'"))
    myFixture.checkResult '''import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

class Foo {
    @NotNull
    @Contract(pure = true)
    static String f<caret>oo() {
        return "s";
    }
}
'''

  }
  
  public void "test custom notNull"() {
    myFixture.addClass("package foo; public @interface MyNotNull {}")
    NullableNotNullManager.getInstance(project).notNulls = ['foo.MyNotNull']
    NullableNotNullManager.getInstance(project).defaultNotNull = 'foo.MyNotNull'
    
    myFixture.configureByText 'a.java', '''
class Foo {
    static String f<caret>oo() {
        unknown();
        return "s";
    }
}
'''
    myFixture.launchAction(myFixture.findSingleIntention("Insert '@MyNotNull'"))
    myFixture.checkResult '''import foo.MyNotNull;

class Foo {
    @MyNotNull
    static String f<caret>oo() {
        unknown();
        return "s";
    }
}
'''
  }

  @Override
  protected void tearDown() throws Exception {
    NullableNotNullManager.getInstance(project).notNulls = NullableNotNullManager.DEFAULT_NOT_NULLS
    NullableNotNullManager.getInstance(project).defaultNotNull = AnnotationUtil.NOT_NULL

    super.tearDown()
  }
}