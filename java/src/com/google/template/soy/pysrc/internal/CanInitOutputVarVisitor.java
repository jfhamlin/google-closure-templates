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

package com.google.template.soy.jssrc.internal;

import com.google.inject.Inject;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.SoyJsSrcOptions.CodeStyle;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.SoyNode;

import java.util.ArrayDeque;
import java.util.Deque;


/**
 * Visitor for determining whther the code generated from a given node's subtree can be made to
 * also initialize the current variable (if not already initialized).
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 * @author Kai Huang
 */
class CanInitOutputVarVisitor extends AbstractSoyNodeVisitor<Boolean> {


  /** The options for generating JS source code. */
  private final SoyJsSrcOptions jsSrcOptions;

  /** The IsComputableAsJsExprsVisitor used by this instance (when needed). */
  private final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;

  /** Stack of partial results (during run). */
  private Deque<Boolean> resultStack;


  /**
   * @param jsSrcOptions The options for generating JS source code.
   * @param isComputableAsJsExprsVisitor The IsComputableAsJsExprsVisitor used by this instance
   *     (when needed).
   */
  @Inject
  CanInitOutputVarVisitor(SoyJsSrcOptions jsSrcOptions,
                          IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor) {
    this.jsSrcOptions = jsSrcOptions;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
  }


  @Override protected void setup() {
    resultStack = new ArrayDeque<Boolean>();
  }


  @Override protected Boolean getResult() {
    return resultStack.peek();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(CallNode node) {
    // If we're generating code in the 'concat' style, then the call is a JS expression that returns
    // its output as a string. However, if we're generating code in the 'stringbuilder' style, then
    // the call is a full statement that returns no value (instead, the output is directly appended
    // to the StringBuilder we pass to the callee).
    resultStack.push(jsSrcOptions.getCodeStyle() == CodeStyle.CONCAT);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(SoyNode node) {
    // For the vast majority of nodes, the return value of this visitor should be the same as the
    // return value of IsComputableAsJsExprsVisitor.
    resultStack.push(isComputableAsJsExprsVisitor.exec(node));
  }

}
