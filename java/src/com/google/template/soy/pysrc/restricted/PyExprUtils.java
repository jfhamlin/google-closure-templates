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

package com.google.template.soy.pysrc.restricted;

import com.google.template.soy.exprtree.Operator;

import java.util.List;


/**
 * Common utilities for dealing with Python expressions.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
 */
public class PyExprUtils {

  private PyExprUtils() {}


  /**
   * Builds one Python expression that computes the concatenation of the given Python expressions. The '+'
   * operator is used for concatenation. Operands will be protected with an extra pair of
   * parentheses if and only if needed.
   *
   * @param pyExprs The Python expressions to concatentate.
   * @return One Python expression that computes the concatenation of the given Python expressions.
   */
  public static PyExpr concatPyExprs(List<PyExpr> pyExprs) {

    if (pyExprs.size() == 0) {
      return new PyExpr("''", Integer.MAX_VALUE);
    }

    if (pyExprs.size() == 1) {
      return pyExprs.get(0);
    }

    int plusOpPrec = Operator.PLUS.getPrecedence();
    StringBuilder resultSb = new StringBuilder();

    boolean isFirst = true;
    for (PyExpr pyExpr : pyExprs) {

      // The first operand needs protection only if it's strictly lower precedence. The non-first
      // operands need protection when they're lower or equal precedence. (This is true for all
      // left-associative operators.)
      boolean needsProtection = isFirst ? pyExpr.getPrecedence() < plusOpPrec
                                        : pyExpr.getPrecedence() <= plusOpPrec;

      if (isFirst) {
        isFirst = false;
      } else {
        resultSb.append(" + ");
      }

      if (needsProtection) {
        resultSb.append("(").append(pyExpr.getText()).append(")");
      } else {
        resultSb.append(pyExpr.getText());
      }
    }

    return new PyExpr(resultSb.toString(), plusOpPrec);
  }

}
