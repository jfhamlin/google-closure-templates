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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.SoyPySrcOptions.CodeStyle;
import com.google.template.soy.pysrc.internal.GenPyExprsVisitor.GenPyExprsVisitorFactory;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachIfemptyNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.SoyCommandNode;
import com.google.template.soy.soytree.SoytreeUtils;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Visitor for generating full Python code (i.e. statements) for parse tree nodes.
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 * <p> {@link #exec} should be called on a full parse tree. Python source code will be generated for
 * all the Soy files. The return value is a list of strings, each string being the content of one
 * generated Python file (corresponding to one Soy file).
 *
 * @author Kai Huang
 */
class GenPyCodeVisitor extends AbstractSoyNodeVisitor<List<String>> {


  /** Regex pattern to look for dots in a template name. */
  private static final Pattern DOT = Pattern.compile("\\.");

  /** Regex pattern for an integer. */
  private static final Pattern INTEGER = Pattern.compile("-?\\d+");

  /** Regex pattern for an underscore-number suffix. */
  private static final Pattern UNDERSCORE_NUMBER_SUFFIX = Pattern.compile("_[0-9]+$");


  /** The options for generating Python source code. */
  private final SoyPySrcOptions pySrcOptions;

  /** Instance of PyExprTranslator to use. */
  private final PyExprTranslator pyExprTranslator;

  /** Instance of GenCallCodeUtils to use. */
  private final GenCallCodeUtils genCallCodeUtils;

  /** The IsComputableAsPyExprsVisitor used by this instance. */
  private final IsComputableAsPyExprsVisitor isComputableAsPyExprsVisitor;

  /** The CanInitOutputVarVisitor used by this instance. */
  private final CanInitOutputVarVisitor canInitOutputVarVisitor;

  /** Factory for creating an instance of GenPyExprsVisitor. */
  private final GenPyExprsVisitorFactory genPyExprsVisitorFactory;

  /** The contents of the generated Python files. */
  private List<String> pyFilesContents;

  /** The GenPyExprsVisitor used by this instance. */
  @VisibleForTesting protected GenPyExprsVisitor genPyExprsVisitor;

  /** The PyCodeBuilder to build the current Python file being generated (during a run). */
  @VisibleForTesting protected PyCodeBuilder pyCodeBuilder;

  /** The current stack of replacement Python expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  @VisibleForTesting protected Deque<Map<String, PyExpr>> localVarTranslations;


  /**
   * @param pySrcOptions The options for generating Python source code.
   * @param pyExprTranslator Instance of PyExprTranslator to use.
   * @param genCallCodeUtils Instance of GenCallCodeUtils to use.
   * @param isComputableAsPyExprsVisitor The IsComputableAsPyExprsVisitor to be used.
   * @param canInitOutputVarVisitor The CanInitOutputVarVisitor to be used.
   * @param genPyExprsVisitorFactory Factory for creating an instance of GenPyExprsVisitor.
   */
  @Inject
  GenPyCodeVisitor(SoyPySrcOptions pySrcOptions, PyExprTranslator pyExprTranslator,
                   GenCallCodeUtils genCallCodeUtils,
                   IsComputableAsPyExprsVisitor isComputableAsPyExprsVisitor,
                   CanInitOutputVarVisitor canInitOutputVarVisitor,
                   GenPyExprsVisitorFactory genPyExprsVisitorFactory) {
    this.pySrcOptions = pySrcOptions;
    this.pyExprTranslator = pyExprTranslator;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsPyExprsVisitor = isComputableAsPyExprsVisitor;
    this.canInitOutputVarVisitor = canInitOutputVarVisitor;
    this.genPyExprsVisitorFactory = genPyExprsVisitorFactory;
  }


  @Override protected void setup() {
    pyFilesContents = Lists.newArrayList();
    pyCodeBuilder = null;
    localVarTranslations = null;
  }


  @VisibleForTesting
  @Override protected void visit(SoyNode node) {
    super.visit(node);
  }


  @Override protected List<String> getResult() {
    return pyFilesContents;
  }


  @Override protected void visitChildren(ParentSoyNode<? extends SoyNode> node) {

    // If the block is empty or if the first child cannot initilize the output var, we must
    // initialize the output var.
    if (node.numChildren() == 0 || !canInitOutputVarVisitor.exec(node.getChild(0))) {
      pyCodeBuilder.initOutputVarIfNecessary();
    }

    List<PyExpr> consecChildrenPyExprs = Lists.newArrayList();

    for (SoyNode child : node.getChildren()) {

      if (isComputableAsPyExprsVisitor.exec(child)) {
        consecChildrenPyExprs.addAll(genPyExprsVisitor.exec(child));

      } else {
        // We've reached a child that is not computable as Python expressions.

        // First add the PyExprs from preceding consecutive siblings that are computable as Python
        // expressions (if any).
        if (consecChildrenPyExprs.size() > 0) {
          pyCodeBuilder.addToOutputVar(consecChildrenPyExprs);
          consecChildrenPyExprs.clear();
        }

        // Now append the code for this child.
        visit(child);
      }
    }

    // Add the PyExprs from the last few children (if any).
    if (consecChildrenPyExprs.size() > 0) {
      pyCodeBuilder.addToOutputVar(consecChildrenPyExprs);
      consecChildrenPyExprs.clear();
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(SoyFileSetNode node) {

    for (SoyFileNode soyFile : node.getChildren()) {
      try {
        visit(soyFile);
      } catch (SoySyntaxException sse) {
        throw sse.setFilePath(soyFile.getFilePath());
      }
    }
  }


  /**
   * Example:
   * <pre>
   * # This file was automatically generated from my-templates.soy.
   * # Please don't edit this file by hand.
   *
   * if (typeof boo == 'undefined') { var boo = {}; }
   * if (typeof boo.foo == 'undefined') { boo.foo = {}; }
   *
   * ...
   * </pre>
   */
  @Override protected void visitInternal(SoyFileNode node) {

    pyCodeBuilder = new PyCodeBuilder(pySrcOptions.getCodeStyle());

    pyCodeBuilder.appendLine("# This file was automatically generated from ",
                             node.getFileName(), ".");
    pyCodeBuilder.appendLine("# Please don't edit this file by hand.");

    pyCodeBuilder.appendLine("def ___():");
    pyCodeBuilder.increaseIndent();
    pyCodeBuilder.appendLine("import pysoy");
    pyCodeBuilder.appendLine("import math");
    pyCodeBuilder.appendLine();
    // Add code to define Python modules.
    addCodeToDefinePyNamespaces(node);

    // Add code for each template.
    for (TemplateNode template : node.getChildren()) {
      pyCodeBuilder.appendLine().appendLine();
      try {
        visit(template);
      } catch (SoySyntaxException sse) {
        throw sse.setTemplateName(template.getTemplateName());
      }
    }

    pyCodeBuilder.decreaseIndent();
    pyCodeBuilder.appendLine("___()");
    pyCodeBuilder.appendLine("del globals()['___']");

    pyFilesContents.add(pyCodeBuilder.getCode());
    pyCodeBuilder = null;
  }


  /**
   * Helper for visitInternal(SoyFileNode) to add code to import required Python modules.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToDefinePyNamespaces(SoyFileNode soyFile) {

    SortedSet<String> pyNamespaces = Sets.newTreeSet();
    for (TemplateNode template : soyFile.getChildren()) {
      String templateName = template.getTemplateName();
      Matcher dotMatcher = DOT.matcher(templateName);
      while (dotMatcher.find()) {
        pyNamespaces.add(templateName.substring(0, dotMatcher.start()));
      }
    }

    for (String pyNamespace : pyNamespaces) {
      boolean hasDot = pyNamespace.indexOf('.') >= 0;
      // If this is a top level namespace and the option to declare top level
      // namespaces is turned off, skip declaring it.
      if (pySrcOptions.shouldDeclareTopLevelNamespaces() || hasDot) {
        pyCodeBuilder.appendLine("pysoy.create_module('", pyNamespace, "')");
      }
      if (!hasDot) {
          pyCodeBuilder.appendLine("import ", pyNamespace, " # import top-level namespace");
      }
    }
  }


  /**
   * Helper for visitInternal(SoyFileNode) to add code to provide/require Soy namespaces.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToProvideRequireSoyNamespaces(SoyFileNode soyFile) {

    pyCodeBuilder.appendLine("goog.provide('", soyFile.getNamespace(), "');");

    pyCodeBuilder.appendLine();

    pyCodeBuilder.appendLine("goog.require('soy');");
    if (pySrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER) {
      pyCodeBuilder.appendLine("goog.require('soy.StringBuilder');");
    }
    String prevCalleeNamespace = null;
    for (String calleeNotInFile : (new FindCalleesNotInFileVisitor()).exec(soyFile)) {
      int lastDotIndex = calleeNotInFile.lastIndexOf('.');
      if (lastDotIndex == -1) {
        throw SoytreeUtils.createSoySyntaxExceptionWithMetaInfo(
            "When using the option to provide/require Soy namespaces, found a called template \"" +
            calleeNotInFile + "\" that does not reside in a namespace.", null, soyFile);
      }
      String calleeNamespace = calleeNotInFile.substring(0, lastDotIndex);
      if (calleeNamespace.length() > 0 && !calleeNamespace.equals(prevCalleeNamespace)) {
        pyCodeBuilder.appendLine("goog.require('", calleeNamespace, "');");
        prevCalleeNamespace = calleeNamespace;
      }
    }
  }


  /**
   * Helper for visitInternal(SoyFileNode) to add code to provide/require template Python functions.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToProvideRequirePyFunctions(SoyFileNode soyFile) {

    SortedSet<String> templateNames = Sets.newTreeSet();
    for (TemplateNode template : soyFile.getChildren()) {
      if (template.isOverride()) {
        continue;  // generated function name already provided
      }
      templateNames.add(template.getTemplateName());
    }
    for (String templateName : templateNames) {
      pyCodeBuilder.appendLine("goog.provide('", templateName, "');");
    }

    pyCodeBuilder.appendLine();

    pyCodeBuilder.appendLine("goog.require('soy');");
    if (pySrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER) {
      pyCodeBuilder.appendLine("goog.require('soy.StringBuilder');");
    }
    for (String calleeNotInFile : (new FindCalleesNotInFileVisitor()).exec(soyFile)) {
      pyCodeBuilder.appendLine("goog.require('", calleeNotInFile, "');");
    }
  }


  /**
   * Example:
   * <pre>
   * def myfunc(opt_data, opt_sb):
   *   output = opt_sb or pysoy.StringBuilder();
   *   ...
   *   ...
   *   if not opt_sb:
   *     return unicode(output)
   * </pre>
   */
  @Override protected void visitInternal(TemplateNode node) {

    boolean isCodeStyleStringbuilder = pySrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER;

    localVarTranslations = new ArrayDeque<Map<String, PyExpr>>();
    genPyExprsVisitor = genPyExprsVisitorFactory.create(localVarTranslations);

    String fullTemplateName = node.getTemplateName();
    int lastDotIndex = fullTemplateName.lastIndexOf(".");
    String parentName = fullTemplateName.substring(0, lastDotIndex);
    String templateName = fullTemplateName.substring(lastDotIndex + 1, fullTemplateName.length());
    pyCodeBuilder.appendLine("@pysoy.add_to_module('", parentName, "')");

    if (isCodeStyleStringbuilder) {
      pyCodeBuilder.appendLine("def ", templateName, "(opt_data = None, opt_sb = None):");
    } else {
      pyCodeBuilder.appendLine("def ", templateName, "(opt_data = None):");
    }
    pyCodeBuilder.increaseIndent();

    if (pySrcOptions.shouldGeneratePydoc()) {
      pyCodeBuilder.appendLine("'''");
      pyCodeBuilder.appendLine("param opt_data -- A dict of the data provided to the template.");
      if (isCodeStyleStringbuilder) {
        pyCodeBuilder.appendLine("param opt_sb -- A soy.StringBuilder used to compose the output.");
        pyCodeBuilder.appendLine();
        pyCodeBuilder.appendLine("return A string of the template output if opt_sb is None, ",
                                 "else None.");
      } else {
        pyCodeBuilder.appendLine();
        pyCodeBuilder.appendLine("return A string of the template output.");
      }
      pyCodeBuilder.appendLine("'''");
    }

    pyCodeBuilder.appendLine("opt_data = pysoy.soy_dict(opt_data)");

    localVarTranslations.push(Maps.<String, PyExpr>newHashMap());

    if (!isCodeStyleStringbuilder && isComputableAsPyExprsVisitor.exec(node)) {
      // Case 1: The code style is 'concat' and the whole template body can be represented as Python
      // expressions. We specially handle this case because we don't want to generate the variable
      // 'output' at all. We simply concatenate the Python expressions and return the result.

      List<PyExpr> templateBodyPyExprs = genPyExprsVisitor.exec(node);
      PyExpr templateBodyPyExpr = PyExprUtils.concatPyExprs(templateBodyPyExprs);
      pyCodeBuilder.appendLine("return ", templateBodyPyExpr.getText(), ";");

    } else {
      // Case 2: Normal case.

      pyCodeBuilder.pushOutputVar("output");
      if (isCodeStyleStringbuilder) {
        pyCodeBuilder.appendLine("output = opt_sb or pysoy.StringBuilder()");
        pyCodeBuilder.setOutputVarInited();
      }

      visitChildren(node);

      if (isCodeStyleStringbuilder) {
        pyCodeBuilder.appendLine("if not opt_sb:");
        pyCodeBuilder.increaseIndent();
        pyCodeBuilder.appendLine("return unicode(output)");
        pyCodeBuilder.decreaseIndent();
      } else {
        pyCodeBuilder.appendLine("return output;");
      }
      pyCodeBuilder.popOutputVar();
    }

    localVarTranslations.pop();
    pyCodeBuilder.decreaseIndent();
  }


  /**
   * Example:
   * <pre>{@literal
   *   <a href="http://www.google.com/search?hl=en
   *     {for $i in range(3)}
   *       &amp;param{$i}={$i}
   *     {/foreach}
   *   ">
   * }</pre>
   * might generate
   * <pre>{@literal
   *   var htmlTag84 = (new soy.StringBuilder()).append('<a href="');
   *   for (var i80 = 1; i80 &lt; 3; i80++) {
   *     htmlTag84.append('&amp;param', i80, '=', i80);
   *   }
   *   htmlTag84.append('">');
   * }</pre>
   */
  @Override protected void visitInternal(MsgHtmlTagNode node) {

    // This node should only be visited when it's not computable as Python expressions, because this
    // method just generates the code to define the temporary 'htmlTag<n>' variable.
    if (isComputableAsPyExprsVisitor.exec(node)) {
      throw new AssertionError(
          "Should only define 'htmlTag<n>' when not computable as Python expressions.");
    }

    pyCodeBuilder.pushOutputVar("htmlTag" + node.getId());
    visitChildren(node);
    pyCodeBuilder.popOutputVar();
  }


  @Override protected void visitInternal(PrintNode node) {
    pyCodeBuilder.addToOutputVar(genPyExprsVisitor.exec(node));
  }


  /**
   * Example:
   * <pre>
   *   {if $boo.foo &gt; 0}
   *     ...
   *   {/if}
   * </pre>
   * might generate
   * <pre>
   *   if opt_data.boo.foo &gt; 0:
   *     ...
   * </pre>
   */
  @Override protected void visitInternal(IfNode node) {

    if (isComputableAsPyExprsVisitor.exec(node)) {
      pyCodeBuilder.addToOutputVar(genPyExprsVisitor.exec(node));
      return;
    }

    // ------ Not computable as Python expressions, so generate full code. ------

    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;

        PyExpr condPyExpr = pyExprTranslator.translateToPyExpr(
            icn.getExpr(), icn.getExprText(), localVarTranslations);

        if (icn.getCommandName().equals("if")) {
          pyCodeBuilder.appendLine("if ", condPyExpr.getText(), ":");
        } else {  // "elseif" block
          pyCodeBuilder.appendLine("elsif ", condPyExpr.getText(), ":");
        }

        pyCodeBuilder.increaseIndent();
        visit(icn);
        pyCodeBuilder.decreaseIndent();

      } else if (child instanceof IfElseNode) {
        IfElseNode ien = (IfElseNode) child;

        pyCodeBuilder.appendLine("else:");

        pyCodeBuilder.increaseIndent();
        visit(ien);
        pyCodeBuilder.decreaseIndent();

      } else {
        throw new AssertionError();
      }
    }
  }


  /**
   * Example:
   * <pre>
   *   {switch $boo}
   *     {case 0}
   *       ...
   *     {case 1, 2}
   *       ...
   *     {default}
   *       ...
   *   {/switch}
   * </pre>
   * might generate
   * <pre>
   *   switchVar1 = opt_data.boo
   *   if switchVar1 == 0:
   *       ...
   *   elif switchVar1 == 1 or switchVar1 == 2:
   *       ...
   *   else:
   *       ...
   * </pre>
   */
  @Override protected void visitInternal(SwitchNode node) {

    PyExpr switchValuePyExpr = pyExprTranslator.translateToPyExpr(
        node.getExpr(), node.getExprText(), localVarTranslations);

    String nodeId = node.getId();
    String switchVarName = "switchVar" + nodeId;

    pyCodeBuilder.appendLine(switchVarName, " = ", switchValuePyExpr.getText());

    SwitchDefaultNode defaultNode = null;
    SwitchCaseNode firstCaseNode = null;

    for (SoyNode child : node.getChildren()) {

      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode scn = (SwitchCaseNode) child;
        if (firstCaseNode == null)
          firstCaseNode = scn;

        String caseCond = (firstCaseNode == scn) ? "if " : "elif ";
        for (ExprNode caseExpr : scn.getExprList()) {
          PyExpr casePyExpr =
              pyExprTranslator.translateToPyExpr(caseExpr, null, localVarTranslations);
          if (caseExpr != scn.getExprList().get(0))
            caseCond += " or ";
          caseCond += switchVarName + " == " + casePyExpr.getText();
        }
        pyCodeBuilder.appendLine(caseCond, ":");

        pyCodeBuilder.increaseIndent();
        visit(scn);
        pyCodeBuilder.decreaseIndent();

      } else if (child instanceof SwitchDefaultNode) {
        defaultNode = (SwitchDefaultNode) child;
      } else {
        throw new AssertionError();
      }
    }

    if (defaultNode != null) {
      if (firstCaseNode != null) {
        pyCodeBuilder.appendLine("else:");
        pyCodeBuilder.increaseIndent();
      }

      visit(defaultNode);

      if (firstCaseNode != null)
        pyCodeBuilder.decreaseIndent();
    }
  }


  /**
   * Example:
   * <pre>
   *   {foreach $foo in $boo.foos}
   *     ...
   *   {ifempty}
   *     ...
   *   {/foreach}
   * </pre>
   * might generate
   * <pre>
   *   fooList2 = opt_data.boo.foos;
   *   fooListLen2 = len(fooList2);
   *   if (fooListLen2 > 0) {
   *     ...
   *   } else {
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitInternal(ForeachNode node) {

    // Build some local variable names.
    String baseVarName = node.getVarName();
    String nodeId = node.getId();
    String listVarName = baseVarName + "List" + nodeId;
    String listLenVarName = baseVarName + "ListLen" + nodeId;

    // Define list var and list-len var.
    PyExpr dataRefPyExpr = pyExprTranslator.translateToPyExpr(
        node.getDataRef(), node.getDataRefText(), localVarTranslations);

    pyCodeBuilder.appendLine(listVarName, " = ", dataRefPyExpr.getText());
    pyCodeBuilder.appendLine(listLenVarName, " = len(", listVarName, ")");

    // If has 'ifempty' node, add the wrapper 'if' statement.
    boolean hasIfemptyNode = node.numChildren() == 2;
    if (hasIfemptyNode) {
      pyCodeBuilder.appendLine("if ", listLenVarName, " > 0:");
      pyCodeBuilder.increaseIndent();
    }

    // Generate code for nonempty case.
    visit((ForeachNonemptyNode) node.getChild(0));

    // If has 'ifempty' node, add the 'else' block of the wrapper 'if' statement.
    if (hasIfemptyNode) {
      pyCodeBuilder.decreaseIndent();
      pyCodeBuilder.appendLine("else:");
      pyCodeBuilder.increaseIndent();

      // Generate code for empty case.
      visit((ForeachIfemptyNode) node.getChild(1));

      pyCodeBuilder.decreaseIndent();
    }
  }


  /**
   * Example:
   * <pre>
   *   {foreach $foo in $boo.foos}
   *     ...
   *   {/foreach}
   * </pre>
   * might generate
   * <pre>
   *   for (var fooIndex2 = 0; fooIndex2 &lt; fooListLen2; fooIndex2++) {
   *     var fooData2 = fooList2[fooIndex2];
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitInternal(ForeachNonemptyNode node) {

    // Build some local variable names.
    String baseVarName = node.getVarName();
    String foreachNodeId = node.getForeachNodeId();
    String listVarName = baseVarName + "List" + foreachNodeId;
    String listLenVarName = baseVarName + "ListLen" + foreachNodeId;
    String indexVarName = baseVarName + "Index" + foreachNodeId;
    String dataVarName = baseVarName + "Data" + foreachNodeId;

    // The start of the Python 'for' loop.
    pyCodeBuilder.appendLine("for ", indexVarName, " in xrange(", listLenVarName, "):");
    pyCodeBuilder.increaseIndent();
    pyCodeBuilder.appendLine(dataVarName, " = ", listVarName, "[", indexVarName, "]");

    // Add a new localVarTranslations frame and populate it with the translations from this node.
    Map<String, PyExpr> newLocalVarTranslationsFrame = Maps.newHashMap();
    newLocalVarTranslationsFrame.put(
        baseVarName, new PyExpr(dataVarName, Integer.MAX_VALUE));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__isFirst", new PyExpr(indexVarName + " == 0",
                                               Operator.EQUAL.getPrecedence()));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__isLast", new PyExpr(indexVarName + " == " + listLenVarName + " - 1",
                                              Operator.EQUAL.getPrecedence()));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__index", new PyExpr(indexVarName, Integer.MAX_VALUE));
    localVarTranslations.push(newLocalVarTranslationsFrame);

    // Generate the code for the loop body.
    visitChildren(node);

    // Remove the localVarTranslations frame that we added above.
    localVarTranslations.pop();

    // The end of the Python 'for' loop.
    pyCodeBuilder.decreaseIndent();
  }


  /**
   * Example:
   * <pre>
   *   {for $i in range(1, $boo)}
   *     ...
   *   {/for}
   * </pre>
   * might generate
   * <pre>
   *   for i4 in xrange(1, opt_dta.boo, 1):
   *     ...
   * </pre>
   */
  @Override protected void visitInternal(ForNode node) {

    String varName = node.getLocalVarName();
    String nodeId = node.getId();

    // Get the Python expression text for the init/limit/increment values.
    List<ExprRootNode<ExprNode>> rangeArgs = Lists.newArrayList(node.getRangeArgs());
    String incrementPyExprText =
        (rangeArgs.size() == 3) ?
        pyExprTranslator.translateToPyExpr(rangeArgs.remove(2), null, localVarTranslations)
            .getText() :
        "1" /* default */;
    String initPyExprText =
        (rangeArgs.size() == 2) ?
        pyExprTranslator.translateToPyExpr(rangeArgs.remove(0), null, localVarTranslations)
            .getText() :
        "0" /* default */;
    String limitPyExprText =
        pyExprTranslator.translateToPyExpr(rangeArgs.get(0), null, localVarTranslations).getText();

    pyCodeBuilder.appendLine("for ", varName, nodeId, " in xrange(", initPyExprText, ", ",
                             limitPyExprText, ", ", incrementPyExprText, "):");
    pyCodeBuilder.increaseIndent();

    // Add a new localVarTranslations frame and populate it with the translations from this node.
    Map<String, PyExpr> newLocalVarTranslationsFrame = Maps.newHashMap();
    newLocalVarTranslationsFrame.put(varName, new PyExpr(varName + nodeId, Integer.MAX_VALUE));
    localVarTranslations.push(newLocalVarTranslationsFrame);

    // Generate the code for the loop body.
    visitChildren(node);

    // Remove the localVarTranslations frame that we added above.
    localVarTranslations.pop();

    // The end of the Python 'for' loop.
    pyCodeBuilder.decreaseIndent();
  }


  /**
   * Example:
   * <pre>
   *   {call name="some.func" data="all" /}
   *   {call name="some.func" data="$boo.foo" /}
   *   {call name="some.func"}
   *     {param key="goo" value="88" /}
   *   {/call}
   *   {call name="some.func" data="$boo"}
   *     {param key="goo"}
   *       Hello {$name}
   *     {/param}
   *   {/call}
   * </pre>
   * might generate
   * <pre>
   *   output += some.func(opt_data);
   *   output += some.func(opt_data.boo.foo);
   *   output += some.func({goo: 88});
   *   output += some.func(pysoy.augment_data(opt_data.boo, {goo: 'Hello ' + opt_data.name});
   * </pre>
   */
  @Override protected void visitInternal(CallNode node) {

    // If this node has any CallParamContentNode children those contents are not computable as Python
    // expressions, visit them to generate code to define their respective 'param<n>' variables.
    for (CallParamNode child : node.getChildren()) {
      if (child instanceof CallParamContentNode && !isComputableAsPyExprsVisitor.exec(child)) {
        visit(child);
      }
    }

    if (pySrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER) {
      // For 'stringbuilder' code style, pass the current output var to collect the call's output.
      PyExpr objToPass = genCallCodeUtils.genObjToPass(
          node,
          localVarTranslations);
      pyCodeBuilder.indent().append(node.getCalleeName(), "(", objToPass.getText(), ", ")
          .appendOutputVarName().append(")\n");

    } else {
      // For 'concat' code style, we simply add the call's result to the current output var.
      PyExpr callExpr = genCallCodeUtils.genCallExpr(node, localVarTranslations);
      pyCodeBuilder.addToOutputVar(ImmutableList.of(callExpr));
    }
  }


  @Override protected void visitInternal(CallParamContentNode node) {

    // This node should only be visited when it's not computable as Python expressions, because this
    // method just generates the code to define the temporary 'param<n>' variable.
    if (isComputableAsPyExprsVisitor.exec(node)) {
      throw new AssertionError(
          "Should only define 'param<n>' when not computable as Python expressions.");
    }

    localVarTranslations.push(Maps.<String, PyExpr>newHashMap());
    pyCodeBuilder.pushOutputVar("param" + node.getId());

    visitChildren(node);

    pyCodeBuilder.popOutputVar();
    localVarTranslations.pop();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(SoyNode node) {

    if (isComputableAsPyExprsVisitor.exec(node)) {
      // Simply generate Python expressions for this node and add them to the current output var.
      pyCodeBuilder.addToOutputVar(genPyExprsVisitor.exec(node));

    } else {
      // Need to implement visitInternal() for the specific case.
      throw new UnsupportedOperationException();
    }
  }


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
    localVarTranslations.push(Maps.<String, PyExpr>newHashMap());
    visitChildren(node);
    localVarTranslations.pop();
  }

}
