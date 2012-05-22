/*
 * Copyright 2009 Google Inc.
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
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.internal.GenPyExprsVisitor.GenPyExprsVisitorFactory;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;

import java.util.Deque;
import java.util.List;
import java.util.Map;


/**
 * Utilities for generating Python code for calls.
 *
 * @author Kai Huang
 */
class GenCallCodeUtils {


  /** The options for generating Python source code. */
  private final SoyPySrcOptions pySrcOptions;

  /** Instance of PyExprTranslator to use. */
  private final PyExprTranslator pyExprTranslator;

  /** The IsComputableAsPyExprsVisitor used by this instance. */
  private final IsComputableAsPyExprsVisitor isComputableAsPyExprsVisitor;

  /** Factory for creating an instance of GenPyExprsVisitor. */
  private final GenPyExprsVisitorFactory genPyExprsVisitorFactory;


  /**
   * @param pySrcOptions The options for generating Python source code.
   * @param pyExprTranslator Instance of PyExprTranslator to use.
   * @param isComputableAsPyExprsVisitor The IsComputableAsPyExprsVisitor to be used.
   * @param genPyExprsVisitorFactory Factory for creating an instance of GenPyExprsVisitor.
   */
  @Inject
  GenCallCodeUtils(SoyPySrcOptions pySrcOptions, PyExprTranslator pyExprTranslator,
                   IsComputableAsPyExprsVisitor isComputableAsPyExprsVisitor,
                   GenPyExprsVisitorFactory genPyExprsVisitorFactory) {
    this.pySrcOptions = pySrcOptions;
    this.pyExprTranslator = pyExprTranslator;
    this.isComputableAsPyExprsVisitor = isComputableAsPyExprsVisitor;
    this.genPyExprsVisitorFactory = genPyExprsVisitorFactory;
  }


  /**
   * Generates the Python expression for a given call (the version that doesn't pass a StringBuilder).
   *
   * <p> Important: If there are CallParamContentNode children whose contents are not computable as
   * Python expressions, then this function assumes that, elsewhere, code has been generated to define
   * their respective 'param<n>' temporary variables.
   *
   * <p> Here are five example calls:
   * <pre>
   *   {call some.func data="all" /}
   *   {call some.func data="$boo.foo" /}
   *   {call some.func}
   *     {param goo = $moo /}
   *   {/call}
   *   {call some.func data="$boo"}
   *     {param goo}Blah{/param}
   *   {/call}
   *   {call some.func}
   *     {param goo}
   *       {for $i in range(3)}{$i}{/for}
   *     {/param}
   *   {/call}
   * </pre>
   * Their respective generated calls might be the following:
   * <pre>
   *   some.func(opt_data)
   *   some.func(opt_data.boo.foo)
   *   some.func({goo: opt_data.moo})
   *   some.func(pysoy.augment_data(opt_data.boo, {goo: 'Blah'}))
   *   some.func({goo: param65})
   * </pre>
   * Note that in the last case, the param content is not computable as Python expressions, so we assume
   * that code has been generated to define the temporary variable 'param<n>'.
   *
   * @param callNode The call to generate code for.
   * @param localVarTranslations The current stack of replacement Python expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   * @return The Python expression for the call (the version that doesn't pass a StringBuilder).
   */
  public PyExpr genCallExpr(CallNode callNode, Deque<Map<String, PyExpr>> localVarTranslations) {

    PyExpr objToPass = genObjToPass(callNode, localVarTranslations);
    return new PyExpr(
        callNode.getCalleeName() + "(" + objToPass.getText() + ")", Integer.MAX_VALUE);
  }


  /**
   * Generates the Python expression for the object to pass in a given call.
   *
   * <p> Important: If there are CallParamContentNode children whose contents are not computable as
   * Python expressions, then this function assumes that, elsewhere, code has been generated to define
   * their respective 'param<n>' temporary variables.
   *
   * <p> Here are five example calls:
   * <pre>
   *   {call some.func data="all" /}
   *   {call some.func data="$boo.foo" /}
   *   {call some.func}
   *     {param goo = $moo /}
   *   {/call}
   *   {call some.func data="$boo"}
   *     {param goo}Blah{/param}
   *   {/call}
   *   {call some.func}
   *     {param goo}
   *       {for $i in range(3)}{$i}{/for}
   *     {/param}
   *   {/call}
   * </pre>
   * Their respective objects to pass might be the following:
   * <pre>
   *   opt_data
   *   opt_data.boo.foo
   *   {'goo': opt_data.moo}
   *   pysoy.augment_data(opt_data.boo, {'goo': 'Blah'})
   *   {'goo': param65}
   * </pre>
   * Note that in the last case, the param content is not computable as Python expressions, so we assume
   * that code has been generated to define the temporary variable 'param<n>'.
   *
   * @param callNode The call to generate code for.
   * @param localVarTranslations The current stack of replacement Python expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   * @return The Python expression for the object to pass in the call.
   */
  public PyExpr genObjToPass(CallNode callNode, Deque<Map<String, PyExpr>> localVarTranslations) {

    // ------ Generate the expression for the original data to pass ------
    PyExpr dataToPass;
    if (callNode.isPassingAllData()) {
      dataToPass = new PyExpr("opt_data", Integer.MAX_VALUE);
    } else if (callNode.isPassingData()) {
      dataToPass = pyExprTranslator.translateToPyExpr(
          callNode.getDataRef(), callNode.getDataRefText(), localVarTranslations);
    } else {
      dataToPass = new PyExpr("None", Integer.MAX_VALUE);
    }

    // ------ Case 1: No additional params ------
    if (callNode.numChildren() == 0) {
      return dataToPass;
    }

    // ------ Build an object literal containing the additional params ------
    StringBuilder paramsObjSb = new StringBuilder();
    paramsObjSb.append("{");

    boolean isFirst = true;
    for (CallParamNode child : callNode.getChildren()) {

      if (isFirst) {
        isFirst = false;
      } else {
        paramsObjSb.append(", ");
      }

      String key = "'" + child.getKey() + "'";
      paramsObjSb.append(key).append(": ");

      if (child instanceof CallParamValueNode) {
        CallParamValueNode cpvn = (CallParamValueNode) child;
        PyExpr valuePyExpr = pyExprTranslator.translateToPyExpr(
            cpvn.getValueExpr(), cpvn.getValueExprText(), localVarTranslations);
        paramsObjSb.append(valuePyExpr.getText());

      } else {
        CallParamContentNode cpcn = (CallParamContentNode) child;

        if (isComputableAsPyExprsVisitor.exec(cpcn)) {
          List<PyExpr> cpcnPyExprs =
              genPyExprsVisitorFactory.create(localVarTranslations).exec(cpcn);
          PyExpr valuePyExpr = PyExprUtils.concatPyExprs(cpcnPyExprs);
          paramsObjSb.append(valuePyExpr.getText());

        } else {
          // This is a param with content that cannot be represented as Python expressions, so we assume
          // that code has been generated to define the temporary variable 'param<n>'.
          if (pySrcOptions.getCodeStyle() == SoyPySrcOptions.CodeStyle.STRINGBUILDER) {
            paramsObjSb.append("str(").append("param").append(cpcn.getId()).append(")");
          } else {
            paramsObjSb.append("param").append(cpcn.getId());
          }
        }
      }
    }

    paramsObjSb.append("}");

    // ------ Cases 2 and 3: Additional params with and without original data to pass ------
    if (callNode.isPassingData()) {
      return new PyExpr(
          "pysoy.augment_data(" + dataToPass.getText() + ", " + paramsObjSb.toString() + ")",
          Integer.MAX_VALUE);
    } else {
      return new PyExpr(paramsObjSb.toString(), Integer.MAX_VALUE);
    }
  }

}
