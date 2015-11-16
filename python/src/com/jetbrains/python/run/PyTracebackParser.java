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
package com.jetbrains.python.run;

import com.jetbrains.python.traceBackParsers.LinkInTrace;
import com.jetbrains.python.traceBackParsers.TraceBackParser;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds links in default python traceback
 *
 * @author Ilya.Kazakevich
 */
public class PyTracebackParser extends TraceBackParser {


  public PyTracebackParser() {
    super(Pattern.compile("File \"([^\"]+)\", line (\\d+)"));
  }

  @NotNull
  @Override
  protected LinkInTrace findLinkInTrace(@NotNull final String line, @NotNull final Matcher matchedMatcher) {
    final String fileName = matchedMatcher.group(1).replace('\\', '/');
    final int lineNumber = Integer.parseInt(matchedMatcher.group(2));
    final int startPos = line.indexOf('\"') + 1;
    final int endPos = line.indexOf('\"', startPos);
    return new LinkInTrace(fileName, lineNumber, startPos, endPos);
  }
}
