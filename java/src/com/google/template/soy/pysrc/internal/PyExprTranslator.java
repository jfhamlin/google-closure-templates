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

import com.google.inject.Inject;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.internal.TranslateToPyExprVisitor.TranslateToPyExprVisitorFactory;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.internal.ImpureFunction;

import java.util.Deque;
import java.util.Map;


/**
 * Translator of Soy expressions to their equivalent Python expressions.
 *
 * @author Kai Huang
 */
class PyExprTranslator {


  /** Map of all SoyPySrcFunctions (name to function). */
  private final Map<String, SoyPySrcFunction> soyPySrcFunctionsMap;

  /** The options for generating Python source code. */
  private final SoyPySrcOptions pySrcOptions;

  /** Factory for creating an instance of TranslateToPyExprVisitor. */
  private final TranslateToPyExprVisitorFactory translateToPyExprVisitorFactory;


  /**
   * @param soyPySrcFunctionsMap Map of all SoyPySrcFunctions (name to function).
   * @param pySrcOptions The options for generating Python source code.
   * @param translateToPyExprVisitorFactory Factory for creating an instance of
   *     TranslateToPyExprVisitor.
   */
  @Inject PyExprTranslator(
      Map<String, SoyPySrcFunction> soyPySrcFunctionsMap, SoyPySrcOptions pySrcOptions,
      TranslateToPyExprVisitorFactory translateToPyExprVisitorFactory) {
    this.soyPySrcFunctionsMap = soyPySrcFunctionsMap;
    this.pySrcOptions = pySrcOptions;
    this.translateToPyExprVisitorFactory = translateToPyExprVisitorFactory;
  }


  /**
   * Translates a Soy expression to the equivalent Python expression. Detects whether an expression
   * is Soy V2 or V1 syntax and performs the translation accordingly. Takes both an ExprNode and
   * the expression text as a string because Soy V1 code will not necessarily be parsable as an
   * ExprNode.
   *
   * @param expr The Soy expression to translate.
   * @param exprText The expression text.
   * @param localVarTranslations The current stack of replacement Python expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   * @return The built Python expression.
   */
  public PyExpr translateToPyExpr(
      ExprNode expr, String exprText, Deque<Map<String, PyExpr>> localVarTranslations) {

    if (expr != null &&
        (! pySrcOptions.shouldAllowDeprecatedSyntax() ||
         (new CheckAllFunctionsSupportedVisitor(soyPySrcFunctionsMap)).exec(expr))) {
      // V2 expression.
      return translateToPyExprVisitorFactory.create(localVarTranslations).exec(expr);
    } else {
      // V1 expression.
      return V1PyExprTranslator.translateToPyExpr(exprText, localVarTranslations);
    }
  }


  /**
   * Private helper class to check whether all functions in an expression are supported
   * (implemented by an available SoyPySrcFunction).
   */
  private static class CheckAllFunctionsSupportedVisitor extends AbstractExprNodeVisitor<Boolean> {

    /** Map of all SoyPySrcFunctions (name to function). */
    private final Map<String, SoyPySrcFunction> soyPySrcFunctionsMap;

    /** Whether all functions in the expression are supported. */
    private boolean areAllFunctionsSupported;

    public CheckAllFunctionsSupportedVisitor(Map<String, SoyPySrcFunction> soyPySrcFunctionsMap) {
      this.soyPySrcFunctionsMap = soyPySrcFunctionsMap;
    }

    @Override protected void setup() {
      areAllFunctionsSupported = true;
    }

    @Override protected Boolean getResult() {
      return areAllFunctionsSupported;
    }

    @Override protected void visitInternal(FunctionNode node) {

      String fnName = node.getFunctionName();
      if (ImpureFunction.forFunctionName(fnName) == null &&
          ! soyPySrcFunctionsMap.containsKey(fnName)) {
        areAllFunctionsSupported = false;
        return;  // already found an unsupported function, so don't keep looking
      }

      visitChildren(node);
    }

    @Override protected void visitInternal(ExprNode node) {
      // Nothing to do for other nodes.
    }

    @Override protected void visitInternal(ParentExprNode node) {
      if (!areAllFunctionsSupported) {
        return;  // already found an unsupported function, so don't keep looking
      }
      visitChildren(node);
    }
  }

}
