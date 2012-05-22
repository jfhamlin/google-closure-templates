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

package com.google.template.soy.pysrc.restricted;

import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.Operator.Associativity;
import com.google.template.soy.exprtree.Operator.Operand;
import com.google.template.soy.exprtree.Operator.Spacer;
import com.google.template.soy.exprtree.Operator.SyntaxElement;
import com.google.template.soy.exprtree.Operator.Token;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;


/**
 * Utilities for building code for the Python Source backend.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
 */
public class SoyPyCodeUtils {

  private SoyPyCodeUtils() {}


  /**
   * Generates a Python expression for the given operator and operands assuming that the JS expression
   * for the operator uses the same syntax format as the Soy operator.
   * @param op The operator.
   * @param operandPyExprs The operands.
   * @return The generated Python expression.
   */
  public static PyExpr genPyExprUsingSoySyntax(Operator op, List<PyExpr> operandPyExprs) {
    return genPyExprUsingSoySyntaxWithNewToken(op, operandPyExprs, null);
  }


  /**
   * Generates a Python expression for the given operator and operands assuming that the JS expression
   * for the operator uses the same syntax format as the Soy operator, with the exception that the
   * Python operator uses a different token (e.g. "!" instead of "not").
   * @param op The operator.
   * @param operandPyExprs The operands.
   * @param newToken The equivalent Python operator's token.
   * @return The generated Python expression.
   */
  public static PyExpr genPyExprUsingSoySyntaxWithNewToken(
      Operator op, List<PyExpr> operandPyExprs, String newToken) {

    int opPrec = op.getPrecedence();
    boolean isLeftAssociative = op.getAssociativity() == Associativity.LEFT;

    List<String> exprList = new ArrayList<String>();
    boolean swapFirstWithFifth = false;

    // Iterate through the operator's syntax elements.
    List<SyntaxElement> syntax = op.getSyntax();
    for (int i = 0, n = syntax.size(); i < n; i++) {
      SyntaxElement syntaxEl = syntax.get(i);

      if (syntaxEl instanceof Operand) {
        // Retrieve the operand's subexpression.
        int operandIndex = ((Operand) syntaxEl).getIndex();
        PyExpr operandPyExpr = operandPyExprs.get(operandIndex);
        // If left (right) associative, first (last) operand doesn't need protection if it's an
        // operator of equal precedence to this one.
        boolean needsProtection;
        if (i == (isLeftAssociative ? 0 : n-1)) {
          needsProtection = operandPyExpr.getPrecedence() < opPrec;
        } else {
          needsProtection = operandPyExpr.getPrecedence() <= opPrec;
        }
        // Append the operand's subexpression to the expression we're building (if necessary,
        // protected using parentheses).
        String subexpr = needsProtection ? "(" + operandPyExpr.getText() + ")"
                                         : operandPyExpr.getText();
        exprList.add(subexpr);

      } else if (syntaxEl instanceof Token) {
        // If a newToken is supplied, then use it, else use the token defined by Soy syntax.
        if (newToken != null) {
          exprList.add(newToken);
        } else if (((Token) syntaxEl).getValue() == "?") {
          swapFirstWithFifth = true;
          exprList.add("if");
        } else if (((Token) syntaxEl).getValue() == ":") {
          exprList.add("else");
        } else {
          exprList.add(((Token) syntaxEl).getValue());
        }

      } else if (syntaxEl instanceof Spacer) {
        // Spacer is just one space.
        exprList.add(" ");

      } else {
        throw new AssertionError();
      }
    }
    if (swapFirstWithFifth) {
      Collections.swap(exprList, 0, 4);
    }
    StringBuilder exprSb = new StringBuilder();
    for (String s : exprList)
    {
      exprSb.append(s);
    }

    return new PyExpr(exprSb.toString(), opPrec);
  }

}
