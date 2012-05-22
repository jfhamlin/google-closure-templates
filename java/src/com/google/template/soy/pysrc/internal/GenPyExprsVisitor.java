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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.ParseException;
import com.google.template.soy.exprparse.TokenMgrError;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.Deque;
import java.util.List;
import java.util.Map;


/**
 * Visitor for generating Python expressions for parse tree nodes.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 * @author Kai Huang
 */
public class GenPyExprsVisitor extends AbstractSoyNodeVisitor<List<PyExpr>> {


  /**
   * Injectable factory for creating an instance of this class.
   */
  public static interface GenPyExprsVisitorFactory {

    /**
     * @param localVarTranslations The current stack of replacement Python expressions for the local
     *     variables (and foreach-loop special functions) current in scope.
     */
    public GenPyExprsVisitor create(Deque<Map<String, PyExpr>> localVarTranslations);
  }


  /** Map of all SoyPySrcPrintDirectives (name to directive). */
  Map<String, SoyPySrcPrintDirective> soyPySrcDirectivesMap;

  /** Instance of PyExprTranslator to use. */
  private final PyExprTranslator pyExprTranslator;

  /** Instance of GenCallCodeUtils to use. */
  private final GenCallCodeUtils genCallCodeUtils;

  /** The IsComputableAsPyExprsVisitor used by this instance (when needed). */
  private final IsComputableAsPyExprsVisitor isComputableAsPyExprsVisitor;

  /** Factory for creating an instance of GenPyExprsVisitor. */
  private final GenPyExprsVisitorFactory genPyExprsVisitorFactory;

  /** The current stack of replacement Python expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  private final Deque<Map<String, PyExpr>> localVarTranslations;

  /** List to collect the results. */
  private List<PyExpr> pyExprs;


  /**
   * @param soyPySrcDirectivesMap Map of all SoyPySrcPrintDirectives (name to directive).
   * @param pyExprTranslator Instance of PyExprTranslator to use.
   * @param genCallCodeUtils Instance of GenCallCodeUtils to use.
   * @param isComputableAsPyExprsVisitor The IsComputableAsPyExprsVisitor used by this instance
   *     (when needed).
   * @param genPyExprsVisitorFactory Factory for creating an instance of GenPyExprsVisitor.
   * @param localVarTranslations The current stack of replacement Python expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   */
  @AssistedInject
  GenPyExprsVisitor(
      Map<String, SoyPySrcPrintDirective> soyPySrcDirectivesMap, PyExprTranslator pyExprTranslator,
      GenCallCodeUtils genCallCodeUtils, IsComputableAsPyExprsVisitor isComputableAsPyExprsVisitor,
      GenPyExprsVisitorFactory genPyExprsVisitorFactory,
      @Assisted Deque<Map<String, PyExpr>> localVarTranslations) {
    this.soyPySrcDirectivesMap = soyPySrcDirectivesMap;
    this.pyExprTranslator = pyExprTranslator;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsPyExprsVisitor = isComputableAsPyExprsVisitor;
    this.genPyExprsVisitorFactory = genPyExprsVisitorFactory;
    this.localVarTranslations = localVarTranslations;
  }


  @Override public List<PyExpr> exec(SoyNode node) {
    Preconditions.checkArgument(isComputableAsPyExprsVisitor.exec(node));
    return super.exec(node);
  }


  @Override protected void setup() {
    pyExprs = Lists.newArrayList();
  }


  @Override protected List<PyExpr> getResult() {
    return pyExprs;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(TemplateNode node) {
    visitChildren(node);
  }


  /**
   * Example:
   * <pre>
   *   I'm feeling lucky!
   * </pre>
   * generates
   * <pre>
   *   u'I\'m feeling lucky!'
   * </pre>
   */
  @Override protected void visitInternal(RawTextNode node) {

    // Note: BaseUtils.escapeToSoyString() builds a Soy string, which is usually a valid Python string.
    // The rare exception is a string containing a Unicode Format character (Unicode category "Cf")
    // because of the Python language quirk that requires all category "Cf" characters to be
    // escaped in Python strings. Therefore, we must call PySrcUtils.escapeUnicodeFormatChars() on the
    // result.
    String exprText = BaseUtils.escapeToSoyString(node.getRawText(), false);
    exprText = PySrcUtils.escapeUnicodeFormatChars(exprText);
    pyExprs.add(new PyExpr("u" + exprText, Integer.MAX_VALUE));
  }


  /**
   * Example:
   * <pre>{@literal
   *   <a href="{$url}">
   * }</pre>
   * might generate
   * <pre>{@literal
   *   '<a href="' + opt_data.url + '">'
   * }</pre>
   */
  @Override protected void visitInternal(MsgHtmlTagNode node) {
    visitChildren(node);
  }


  /**
   * Example:
   * <pre>
   *   {$boo.foo}
   *   {$goo.moo + 5}
   * </pre>
   * might generate
   * <pre>
   *   opt_data.boo.foo
   *   gooData4.moo + 5
   * </pre>
   */
  @Override protected void visitInternal(PrintNode node) {

    PyExpr pyExpr = pyExprTranslator.translateToPyExpr(
        node.getExpr(), node.getExprText(), localVarTranslations);

    // Process directives.
    for (PrintDirectiveNode directiveNode : node.getChildren()) {

      // Get directive.
      SoyPySrcPrintDirective directive = soyPySrcDirectivesMap.get(directiveNode.getName());
      if (directive == null) {
        throw new SoySyntaxException(
            "Failed to find SoyPySrcPrintDirective with name '" + directiveNode.getName() + "'" +
            " (tag " + node.toSourceString() +")");
      }

      // Get directive args.
      List<ExprRootNode<ExprNode>> args = directiveNode.getArgs();
      if (! directive.getValidArgsSizes().contains(args.size())) {
        throw new SoySyntaxException(
            "Print directive '" + directiveNode.getName() + "' used with the wrong number of" +
            " arguments (tag " + node.toSourceString() + ").");
      }

      // Translate directive args.
      List<PyExpr> argsPyExprs = Lists.newArrayListWithCapacity(args.size());
      for (ExprRootNode<ExprNode> arg : args) {
        argsPyExprs.add(pyExprTranslator.translateToPyExpr(arg, null, localVarTranslations));
      }

      // Apply directive.
      pyExpr = directive.applyForPySrc(pyExpr, argsPyExprs);
    }

    pyExprs.add(pyExpr);
  }


  /**
   * Note: We would only see a CssNode if the css-handling scheme is BACKEND_SPECIFIC.
   *
   * Example:
   * <pre>
   *   {css selected-option}
   *   {css $foo, bar}
   * </pre>
   * might generate
   * <pre>
   *   goog.getCssName('selected-option')
   *   goog.getCssName(opt_data.foo, 'bar')
   * </pre>
   */
  @Override protected void visitInternal(CssNode node) {

    StringBuilder sb = new StringBuilder();
    sb.append("goog.getCssName(");

    String selectorText = node.getCommandText();

    int delimPos = node.getCommandText().lastIndexOf(',');
    if (delimPos != -1) {
      String baseText = node.getCommandText().substring(0, delimPos).trim();

      ExprRootNode<ExprNode> baseExpr = null;
      try {
        baseExpr = (new ExpressionParser(baseText)).parseExpression();
      } catch (TokenMgrError tme) {
        throw createExceptionForInvalidBase(baseText, tme);
      } catch (ParseException pe) {
        throw createExceptionForInvalidBase(baseText, pe);
      }
      
      PyExpr basePyExpr =
          pyExprTranslator.translateToPyExpr(baseExpr, baseText, localVarTranslations);
      sb.append(basePyExpr.getText()).append(", ");
      selectorText = node.getCommandText().substring(delimPos + 1).trim();
    }

    sb.append("'").append(selectorText).append("')");

    pyExprs.add(new PyExpr(sb.toString(), Integer.MAX_VALUE));
  }

  
  /**
   * Private helper for {@link #visitInternal(CssNode)}.
   * @param baseText The base part of the goog.getCssName() call being generated.
   * @param cause The underlying exception.
   * @return The SoySyntaxException to be thrown.
   */
  private SoySyntaxException createExceptionForInvalidBase(String baseText, Throwable cause) {
    //noinspection ThrowableInstanceNeverThrown
    return new SoySyntaxException(
        "Invalid expression for base in 'css' command text \"" + baseText + "\".", cause);
  }


  /**
   * Example:
   * <pre>
   *   {if $boo}
   *     AAA
   *   {elseif $foo}
   *     BBB
   *   {else}
   *     CCC
   *   {/if}
   * </pre>
   * might generate
   * <pre>
   *   AAA if (opt_data['boo']) else BBB if (opt_data['foo']) else CCC
   * </pre>
   */
  @Override protected void visitInternal(IfNode node) {

    // Create another instance of this visitor class for generating Python expressions from children.
    GenPyExprsVisitor genPyExprsVisitor = genPyExprsVisitorFactory.create(localVarTranslations);

    StringBuilder pyExprTextSb = new StringBuilder();

    boolean hasElse = false;
    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;

        PyExpr condPyExpr = pyExprTranslator.translateToPyExpr(
            icn.getExpr(), icn.getExprText(), localVarTranslations);

        List<PyExpr> condBlockPyExprs = genPyExprsVisitor.exec(icn);
        pyExprTextSb.append(PyExprUtils.concatPyExprs(condBlockPyExprs).getText());

        pyExprTextSb.append(" if (").append(condPyExpr.getText()).append(") else ");

      } else if (child instanceof IfElseNode) {
        hasElse = true;
        IfElseNode ien = (IfElseNode) child;

        List<PyExpr> elseBlockPyExprs = genPyExprsVisitor.exec(ien);
        pyExprTextSb.append(PyExprUtils.concatPyExprs(elseBlockPyExprs).getText());

      } else {
        throw new AssertionError();
      }
    }

    if (!hasElse) {
      pyExprTextSb.append("''");
    }

    pyExprs.add(new PyExpr(pyExprTextSb.toString(), Operator.CONDITIONAL.getPrecedence()));
  }


  @Override protected void visitInternal(IfCondNode node) {
    visitChildren(node);
  }


  @Override protected void visitInternal(IfElseNode node) {
    visitChildren(node);
  }


  /**
   * Example:
   * <pre>
   *   {call name="some.func" data="all" /}
   *   {call name="some.func" data="$boo.foo" /}
   *   {call name="some.func"}
   *     {param key="goo" value="$moo" /}
   *   {/call}
   *   {call name="some.func" data="$boo"}
   *     {param key="goo"}Blah{/param}
   *   {/call}
   * </pre>
   * might generate
   * <pre>
   *   some.func(opt_data)
   *   some.func(opt_data.boo.foo)
   *   some.func({'goo': opt_data.moo})
   *   some.func(soy.$$augmentData(opt_data.boo, {'goo': 'Blah'}))
   * </pre>
   */
  @Override protected void visitInternal(CallNode node) {
    pyExprs.add(genCallCodeUtils.genCallExpr(node, localVarTranslations));
  }


  @Override protected void visitInternal(CallParamContentNode node) {
    visitChildren(node);
  }

}
