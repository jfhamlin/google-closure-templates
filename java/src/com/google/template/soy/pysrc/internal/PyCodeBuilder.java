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

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.pysrc.SoyPySrcOptions.CodeStyle;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;


/**
 * A class for building Python code.
 *
 * <p> Usage example that demonstrates most of the methods:
 * <pre>
 *   PyCodeBuilder jcb = new PyCodeBuilder(CodeStyle.STRINGBUILDER);
 *   jcb.appendLine("story.title = function(opt_data) {");
 *   jcb.increaseIndent();
 *   jcb.pushOutputVar("output");
 *   jcb.initOutputVarIfNecessary();
 *   jcb.pushOutputVar("temp");
 *   jcb.addToOutputVar(Lists.newArrayList(
 *       new PyExpr("'Snow White and the '", Integer.MAX_VALUE),
 *       new PyExpr("opt_data.numDwarfs", Integer.MAX_VALUE));
 *   jcb.popOutputVar();
 *   jcb.addToOutputVar(Lists.newArrayList(
 *       new PyExpr("temp", Integer.MAX_VALUE),
 *       new PyExpr("' Dwarfs'", Integer.MAX_VALUE));
 *   jcb.indent().append("return ").appendOutputVarName().append(".toString();\n");
 *   jcb.popOutputVar();
 *   jcb.decreaseIndent();
 *   String THE_END = "the end";
 *   jcb.appendLine("}  // ", THE_END);
 * </pre>
 * The above example builds the following Python code:
 * <pre>
 * story.title = function(opt_data) {
 *   var output = new soy.StringBuilder();
 *   var temp = new soy.StringBuilder('Snow White and the ', opt_data.numDwarfs);
 *   output.append(temp, ' Dwarfs');
 *   return output.toString();
 * }  // the end
 * </pre>
 *
 * @author Kai Huang
 */
class PyCodeBuilder {


  /** Used by {@code increaseIndent()} and {@code decreaseIndent()}. */
  private static final String SPACES = "                    ";  // 20 spaces


  /** A buffer to accumulate the generated code. */
  private final StringBuilder code;

  /** The {@code OutputCodeGenerator} to use. */
  private final CodeStyle codeStyle;

  /** The current indent (some even number of spaces). */
  private String indent;

  /** The current stack of output variables. */
  private Deque<Pair<String, Boolean>> outputVars;

  /** The current output variable name. */
  private String currOutputVarName;

  /** Whether the current output variable is initialized. */
  private boolean currOutputVarIsInited;


  /**
   * Constructs a new instance. At the start, the code is empty and the indent is 0 spaces.
   *
   * @param codeStyle The code style to use.
   */
  public PyCodeBuilder(CodeStyle codeStyle) {
    this.codeStyle = codeStyle;
    code = new StringBuilder();
    indent = "";
    outputVars = new ArrayDeque<Pair<String, Boolean>>();
    currOutputVarName = null;
    currOutputVarIsInited = false;
  }


  /**
   * Increases the current indent by two spaces.
   * @throws SoySyntaxException If the new indent depth would be greater than 20.
   */
  public void increaseIndent() throws SoySyntaxException {
    int newIndentDepth = indent.length() + 2;
    if (newIndentDepth > 20) {
      throw new SoySyntaxException("Indent is more than 20 spaces!");
    }
    indent = SPACES.substring(0, newIndentDepth);
  }


  /**
   * Decreases the current indent by two spaces.
   * @throws SoySyntaxException If the new indent depth would be less than 0.
   */
  public void decreaseIndent() throws SoySyntaxException {
    int newIndentDepth = indent.length() - 2;
    if (newIndentDepth < 0) {
      throw new SoySyntaxException("Indent is less than 0 spaces!");
    }
    indent = SPACES.substring(0, newIndentDepth);
  }


  /**
   * Pushes on a new current output variable.
   * @param outputVarName The new output variable name.
   */
  public void pushOutputVar(String outputVarName) {
    outputVars.push(Pair.of(outputVarName, false));
    currOutputVarName = outputVarName;
    currOutputVarIsInited = false;
  }


  /**
   * Pops off the current output variable. The previous output variable again becomes the current.
   */
  public void popOutputVar() {
    outputVars.pop();
    Pair<String, Boolean> topPair = outputVars.peek();  // null if outputVars is now empty
    if (topPair != null) {
      currOutputVarName = topPair.getFirst();
      currOutputVarIsInited = topPair.getSecond();
    } else {
      currOutputVarName = null;
      currOutputVarIsInited = false;
    }
  }


  /**
   * Tells this PyCodeBuilder that the current output variable has already been initialized. This
   * causes {@code initOutputVarIfNecessary} and {@code addToOutputVar} to not add initialization
   * code even on the first use of the variable.
   */
  public void setOutputVarInited() {
    outputVars.pop();
    outputVars.push(Pair.of(currOutputVarName, true));
    currOutputVarIsInited = true;
  }


  /**
   * Appends the current indent to the generated code.
   * @return This PyCodeBuilder (for stringing together operations).
   */
  public PyCodeBuilder indent() {
    code.append(indent);
    return this;
  }


  /**
   * Appends one or more strings to the generated code.
   * @param pyCodeFragments The code string(s) to append.
   * @return This PyCodeBuilder (for stringing together operations).
   */
  public PyCodeBuilder append(String... pyCodeFragments) {
    for (String pyCodeFragment : pyCodeFragments) {
      code.append(pyCodeFragment);
    }
    return this;
  }


  /**
   * Equvalent to pyCodeBuilder.indent().append(pyCodeFragments).append("\n");
   * @param pyCodeFragments The code string(s) to append.
   * @return This PyCodeBuilder (for stringing together operations).
   */
  public PyCodeBuilder appendLine(String... pyCodeFragments) {
    indent();
    append(pyCodeFragments);
    code.append("\n");
    return this;
  }


  /**
   * Appends the name of the current output variable.
   * @return This PyCodeBuilder (for stringing together operations).
   */
  public PyCodeBuilder appendOutputVarName() {
    code.append(currOutputVarName);
    return this;
  }


  /**
   * Appends a full line/statement for initializing the current output variable.
   */
  public void initOutputVarIfNecessary() {

    if (currOutputVarIsInited) {
      // Nothing to do since it's already initialized.
      return;
    }

    if (codeStyle == CodeStyle.STRINGBUILDER) {
      // var output = new soy.StringBuilder();
      appendLine(currOutputVarName, " = pysoy.StringBuilder()");
    } else {
      // var output = '';
      appendLine(currOutputVarName, " = ''");
    }
    setOutputVarInited();
  }


  /**
   * Appends a line/statement with the given concatenation of the given Python expressions saved to the
   * current output variable.
   * @param pyExprs One or more Python expressions to compute output.
   */
  public void addToOutputVar(List<PyExpr> pyExprs) {

    if (codeStyle == CodeStyle.STRINGBUILDER) {
      StringBuilder commaSeparatedPyExprsSb = new StringBuilder();
      boolean isFirst = true;
      for (PyExpr pyExpr : pyExprs) {
        if (isFirst) {
          isFirst = false;
        } else {
          commaSeparatedPyExprsSb.append(", ");
        }
        commaSeparatedPyExprsSb.append(pyExpr.getText());
      }

      if (currOutputVarIsInited) {
        // output.append(AAA, BBB);
        appendLine(currOutputVarName, ".append(", commaSeparatedPyExprsSb.toString(), ")");
      } else {
        // var output = new soy.StringBuilder(AAA, BBB);
        appendLine(currOutputVarName, " = pysoy.StringBuilder(",
                   commaSeparatedPyExprsSb.toString(), ")");
        setOutputVarInited();
      }

    } else {  // CodeStyle.CONCAT
      PyExpr concatenatedPyExprs = PyExprUtils.concatPyExprs(pyExprs);

      if (currOutputVarIsInited) {
        // output += AAA + BBB + CCC;
        appendLine(currOutputVarName, " += ", concatenatedPyExprs.getText());
      } else {
        // var output = AAA + BBB + CCC;
        appendLine("var ", currOutputVarName, " = ", concatenatedPyExprs.getText());
        setOutputVarInited();
      }
    }
  }


  /**
   * @return The generated code.
   */
  public String getCode() {
    return code.toString();
  }

}
