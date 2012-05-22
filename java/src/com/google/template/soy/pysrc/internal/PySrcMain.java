/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.pysrc.internal;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.InsertMsgsVisitor;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.sharedpasses.AssertSyntaxVersionV2Visitor;
import com.google.template.soy.sharedpasses.CheckSoyDocVisitor;
import com.google.template.soy.sharedpasses.RemoveHtmlCommentsVisitor;
import com.google.template.soy.soytree.SoyFileSetNode;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;


/**
 * Main entry point for the Python Src backend (output target).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class PySrcMain {


  /** The scope object that manages the API call scope. */
  private final GuiceSimpleScope apiCallScope;

  /** Provider for getting an instance of GenPyCodeVisitor. */
  private final Provider<GenPyCodeVisitor> genPyCodeVisitorProvider;

  /**
   * @param apiCallScope The scope object that manages the API call scope.
   * @param genPyCodeVisitorProvider Provider for getting an instance of GenPyCodeVisitor.
   */
  @Inject
  PySrcMain(@ApiCall GuiceSimpleScope apiCallScope,
            Provider<GenPyCodeVisitor> genPyCodeVisitorProvider) {
    this.apiCallScope = apiCallScope;
    this.genPyCodeVisitorProvider = genPyCodeVisitorProvider;
  }


  /**
   * Generates Python source code given a Soy parse tree, an options object, and an optional bundle of
   * translated messages.
   *
   * @param soyTree The Soy parse tree to generate Python source code for.
   * @param pySrcOptions The compilation options relevant to this backend.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @return A list of strings where each string represents the Python source code that belongs in one
   *     Python file. The generated Python files correspond one-to-one to the original Soy source files.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public List<String> genPySrc(
      SoyFileSetNode soyTree, SoyPySrcOptions pySrcOptions, @Nullable SoyMsgBundle msgBundle)
      throws SoySyntaxException {

    if (pySrcOptions.shouldAllowDeprecatedSyntax()) {
      // Passes that we only want to run if allowing V1 syntax.
      (new RemoveHtmlCommentsVisitor()).exec(soyTree);
      (new CheckSoyDocVisitor(false)).exec(soyTree);

    } else {
      // Passes that we only want to run if enforcing V2 syntax.
      (new AssertSyntaxVersionV2Visitor()).exec(soyTree);
      (new CheckSoyDocVisitor(true)).exec(soyTree);
    }

    apiCallScope.enter();
    try {
      // Seed the scoped parameters.
      apiCallScope.seed(SoyPySrcOptions.class, pySrcOptions);
      ApiCallScopeUtils.seedSharedParams(
          apiCallScope, msgBundle, pySrcOptions.getBidiGlobalDir());

      // Replace MsgNodes.
      if (pySrcOptions.shouldGenerateGoogMsgDefs()) {
        Preconditions.checkState(
            pySrcOptions.getBidiGlobalDir() != 0,
            "If enabling shouldGenerateGoogMsgDefs, must also set bidiGlobalDir.");
      } else {
        (new InsertMsgsVisitor(msgBundle)).exec(soyTree);
      }

      // Do the code generation.
      return genPyCodeVisitorProvider.get().exec(soyTree);

    } finally {
      apiCallScope.exit();
    }
  }


  /**
   * Generates Python source files given a Soy parse tree, an options object, an optional bundle of
   * translated messages, and information on where to put the output files.
   *
   * @param soyTree The Soy parse tree to generate Python source code for.
   * @param pySrcOptions The compilation options relevant to this backend.
   * @param locale The current locale that we're generating Python for, or null if not applicable.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param outputPathFormat The format string defining how to build the output file path
   *     corresponding to an input file path.
   * @param inputPathsPrefix The input path prefix, or empty string if none.
   * @throws SoySyntaxException If a syntax error is found.
   * @throws IOException If there is an error in opening/writing an output Python file.
   */
  public void genPyFiles(
      SoyFileSetNode soyTree, SoyPySrcOptions pySrcOptions, @Nullable String locale,
      @Nullable SoyMsgBundle msgBundle, String outputPathFormat, String inputPathsPrefix)
      throws SoySyntaxException, IOException {

    List<String> pyFileContents = genPySrc(soyTree, pySrcOptions, msgBundle);

    int numFiles = soyTree.numChildren();
    if (numFiles != pyFileContents.size()) {
      throw new AssertionError();
    }

    for (int i = 0; i < numFiles; i++) {
      String inputFilePath = soyTree.getChild(i).getFilePath();
      String outputFilePath =
          PySrcUtils.buildFilePath(outputPathFormat, locale, inputFilePath, inputPathsPrefix);

      BaseUtils.ensureDirsExistInPath(outputFilePath);
      Files.write(pyFileContents.get(i), new File(outputFilePath), Charsets.UTF_8);
    }
  }

}
