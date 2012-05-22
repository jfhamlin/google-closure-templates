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

package com.google.template.soy.pysrc;

import com.google.common.base.Preconditions;


/**
 * Compilation options for the Python Src output target (backend).
 *
 * @author James Hamlin
 */
public class SoyPySrcOptions implements Cloneable {


  /**
   * The two supported code styles.
   */
  public static enum CodeStyle {
    STRINGBUILDER, CONCAT;
  }


  /** Whether to allow deprecated syntax (semi backwards compatible mode). */
  private boolean shouldAllowDeprecatedSyntax;

  /** The output variable code style to use. */
  private CodeStyle codeStyle;

  /** Whether we should generate PyDoc with type info for the Closure Compiler. */
  private boolean shouldGeneratePydoc;

  /** Whether we should generate code to provide/require Soy namespaces. */
  private boolean shouldProvideRequireSoyNamespaces;

  /** Whether we should generate code to provide/require template JS functions. */
  private boolean shouldProvideRequirePyFunctions;

  /** Whether we should generate code to declare the top level namespace. */
  private boolean shouldDeclareTopLevelNamespaces;

  /** Whether we should generate Closure Library message definitions (i.e. goog.getMsg). */
  private boolean shouldGenerateGoogMsgDefs;

  /** Whether the Closure Library messages are external, i.e. "MSG_EXTERNAL_[soyGeneratedMsgId]". */
  private boolean googMsgsAreExternal;

  /** Bidi global directionality (ltr=1, rtl=-1). Required if shouldGeneratePyMsgDefs is true. */
  private int bidiGlobalDir;


  public SoyPySrcOptions() {
    shouldAllowDeprecatedSyntax = false;
    codeStyle = CodeStyle.STRINGBUILDER;
    shouldGeneratePydoc = false;
    shouldProvideRequireSoyNamespaces = false;
    shouldProvideRequirePyFunctions = false;
    shouldDeclareTopLevelNamespaces = true;
    shouldGenerateGoogMsgDefs = false;
    googMsgsAreExternal = false;
    bidiGlobalDir = 0;
  }


  /**
   * Sets whether to allow deprecated syntax (semi backwards compatible mode).
   * @param shouldAllowDeprecatedSyntax The value to set.
   */
  public void setShouldAllowDeprecatedSyntax(boolean shouldAllowDeprecatedSyntax) {
    this.shouldAllowDeprecatedSyntax = shouldAllowDeprecatedSyntax;
  }

  /** Returns whether we're set to allow deprecated syntax (semi backwards compatible mode). */
  public boolean shouldAllowDeprecatedSyntax() {
    return shouldAllowDeprecatedSyntax;
  }


  /**
   * Sets the output variable code style to use.
   * @param codeStyle The code style to set.
   */
  public void setCodeStyle(CodeStyle codeStyle) {
    this.codeStyle = codeStyle;
  }

  /** Returns the currently set code style. */
  public CodeStyle getCodeStyle() {
    return codeStyle;
  }


  /**
   * Sets whether we should generate JSDoc with type info for the Closure Compiler.
   * @param shouldGeneratePydoc The value to set.
   */
  public void setShouldGeneratePydoc(boolean shouldGeneratePydoc) {
    this.shouldGeneratePydoc = shouldGeneratePydoc;
  }

  /** Returns whether we should generate JSDoc with type info for the Closure Compiler. */
  public boolean shouldGeneratePydoc() {
    return shouldGeneratePydoc;
  }


  /**
   * Sets whether we should generate code to provide/require Soy namespaces.
   * @param shouldProvideRequireSoyNamespaces The value to set.
   */
  public void setShouldProvideRequireSoyNamespaces(
      boolean shouldProvideRequireSoyNamespaces) {
    this.shouldProvideRequireSoyNamespaces = shouldProvideRequireSoyNamespaces;
    Preconditions.checkState(
        !(this.shouldProvideRequireSoyNamespaces && this.shouldProvideRequirePyFunctions),
        "Must not enable both shouldProvideRequireSoyNamespaces and" +
        " shouldProvideRequirePyFunctions.");
    Preconditions.checkState(
        !(!this.shouldDeclareTopLevelNamespaces && this.shouldProvideRequireSoyNamespaces),
        "Turning off shouldDeclareTopLevelNamespaces has no meaning when" +
        " shouldProvideRequireSoyNamespaces is enabled.");
  }

  /** Returns whether we're set to generate code to provide/require Soy namespaces. */
  public boolean shouldProvideRequireSoyNamespaces() {
    return shouldProvideRequireSoyNamespaces;
  }


  /**
   * Sets whether we should generate code to provide/require template JS functions.
   * @param shouldProvideRequirePyFunctions The value to set.
   */
  public void setShouldProvideRequirePyFunctions(
      boolean shouldProvideRequirePyFunctions) {
    this.shouldProvideRequirePyFunctions = shouldProvideRequirePyFunctions;
    Preconditions.checkState(
        !(this.shouldProvideRequireSoyNamespaces && this.shouldProvideRequirePyFunctions),
        "Must not enable both shouldProvideRequireSoyNamespaces and" +
        " shouldProvideRequirePyFunctions.");
    Preconditions.checkState(
        !(!this.shouldDeclareTopLevelNamespaces && this.shouldProvideRequirePyFunctions),
        "Turning off shouldDeclareTopLevelNamespaces has no meaning when" +
        " shouldProvideRequirePyFunctions is enabled.");
  }

  /** Returns whether we're set to generate code to provide/require template JS functions. */
  public boolean shouldProvideRequirePyFunctions() {
    return shouldProvideRequirePyFunctions;
  }


  /**
   * Sets whether we should generate code to declare the top level namespace.
   * @param shouldDeclareTopLevelNamespaces The value to set.
   */
  public void setShouldDeclareTopLevelNamespaces(
      boolean shouldDeclareTopLevelNamespaces) {
    this.shouldDeclareTopLevelNamespaces = shouldDeclareTopLevelNamespaces;
    Preconditions.checkState(
        !(!this.shouldDeclareTopLevelNamespaces && this.shouldProvideRequireSoyNamespaces),
        "Turning off shouldDeclareTopLevelNamespaces has no meaning when" +
        " shouldProvideRequireSoyNamespaces is enabled.");
    Preconditions.checkState(
        !(!this.shouldDeclareTopLevelNamespaces && this.shouldProvideRequirePyFunctions),
        "Turning off shouldDeclareTopLevelNamespaces has no meaning when" +
        " shouldProvideRequirePyFunctions is enabled.");
  }

  /** Returns whether we should attempt to declare the top level namespace. */
  public boolean shouldDeclareTopLevelNamespaces() {
    return shouldDeclareTopLevelNamespaces;
  }


  /**
   * Sets whether we should generate Closure Library message definitions (i.e. goog.getMsg).
   * @param shouldGenerateGoogMsgDefs The value to set.
   */
  public void setShouldGenerateGoogMsgDefs(boolean shouldGenerateGoogMsgDefs) {
    this.shouldGenerateGoogMsgDefs = shouldGenerateGoogMsgDefs;
  }

  /** Returns whether we should generate Closure Library message definitions (i.e. goog.getMsg). */
  public boolean shouldGenerateGoogMsgDefs() {
    return shouldGenerateGoogMsgDefs;
  }


  /**
   * Sets whether the generated Closure Library message definitions are for external messages
   * (only applicable if shouldGenerateGoogMsgDefs is true).
   *
   * If this option is true, then we generate
   *     var MSG_EXTERNAL_[soyGeneratedMsgId] = goog.getMsg(...);
   * If this option is false, then we generate
   *     var MSG_UNNAMED_[uniquefier] = goog.getMsg(...);
   *
   * @param googMsgsAreExternal The value to set.
   */
  public void setGoogMsgsAreExternal(boolean googMsgsAreExternal) {
    this.googMsgsAreExternal = googMsgsAreExternal;
  }

  /**
   * Returns whether the generated Closure Library message definitions are for external messages
   * (only applicable if shouldGenerateGoogMsgDefs is true).
   *
   * If this option is true, then we generate
   *     var MSG_EXTERNAL_[soyGeneratedMsgId] = goog.getMsg(...);
   * If this option is false, then we generate
   *     var MSG_UNNAMED_[uniquefier] = goog.getMsg(...);
   */
  public boolean googMsgsAreExternal() {
    return googMsgsAreExternal;
  }


  /**
   * Sets the bidi global directionality (1 for LTR, -1 for RTL, 0 to infer from message bundle).
   * Note: If shouldGenerateGoogMsgDefs is true, this must be set to a non-zero value.
   * @param bidiGlobalDir The value to set.
   */
  public void setBidiGlobalDir(int bidiGlobalDir) {
    Preconditions.checkArgument(
        bidiGlobalDir == 1 || bidiGlobalDir == -1 || bidiGlobalDir == 0,
        "Bidi global directionality must be 1 for LTR, -1 for RTL, or 0 to infer this from the" +
        " locale string in the message bundle.");
    this.bidiGlobalDir = bidiGlobalDir;
    Preconditions.checkState(
        ! (this.shouldGenerateGoogMsgDefs && bidiGlobalDir == 0),
        "If shouldGenerateGoogMsgDefs is true, then bidiGlobalDir must be set to 1 or -1.");
  }

  /**
   * Returns the bidi global directionality (ltr=1, rtl=-1).
   */
  public int getBidiGlobalDir() {
    return bidiGlobalDir;
  }


  @Override public SoyPySrcOptions clone() {
    try {
      return (SoyPySrcOptions) super.clone();
    } catch (CloneNotSupportedException cnse) {
      throw new RuntimeException("Cloneable interface removed from SoyPySrcOptions");
    }
  }

}
