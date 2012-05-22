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

package com.google.template.soy;

import com.google.common.collect.Lists;
import com.google.inject.Injector;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.SoyPySrcOptions.CodeStyle;
import com.google.template.soy.shared.SoyGeneralOptions.CssHandlingScheme;
import com.google.template.soy.shared.SoyGeneralOptions.SafePrintTagsInferenceLevel;
import com.google.template.soy.xliffmsgplugin.XliffMsgPluginModule;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.util.List;


/**
 * Executable for compiling a set of Soy files into corresponding PY source files.
 *
 * @author Kai Huang
 */
public final class SoyToPySrcCompiler {


  /** The string to prepend to the usage message. */
  private static final String USAGE_PREFIX =
      "Usage:\n" +
      "java com.google.template.soy.SoyToPySrcCompiler  \\\n" +
      "     [<flag1> <flag2> ...] --outputPathFormat <formatString>  \\\n" +
      "     <soyFile1> <soyFile2> ...\n";


  @Option(name = "--inputPrefix",
          usage = "If provided, this path prefix will be prepended to each input file path" +
                  " listed on the command line. This is a literal string prefix, so you'll need" +
                  " to include a trailing slash if necessary.")
  private String inputPrefix = "";

  @Option(name = "--outputPathFormat",
          required = true,
          usage = "[Required] A format string that specifies how to build the path to each" +
                  " output file. If not generating localized PY, then there will be one output" +
                  " PY file (UTF-8) for each input Soy file. If generating localized PY, then" +
                  " there will be one output PY file for each combination of input Soy file and" +
                  " locale. The format string can include literal characters as well as the" +
                  " placeholders {INPUT_PREFIX}, {INPUT_DIRECTORY}, {INPUT_FILE_NAME}," +
                  " {INPUT_FILE_NAME_NO_EXT}, {LOCALE}, {LOCALE_LOWER_CASE}. Note" +
                  " {LOCALE_LOWER_CASE} also turns dash into underscore, e.g. pt-BR becomes" +
                  " pt_br.")
  private String outputPathFormat = "";

  @Option(name = "--safePrintTagsInferenceLevel",
          usage = "The level of safe-print-tags inference ('none', 'simple', or 'advanced'). For" +
                  " all inferred safe print tags, the compiler automatically adds the" +
                  " '|noAutoescape' directive." +
                  "    NONE: No inference. '@safe' declarations are ignored." +
                  "    SIMPLE: Infers safe print tags based on '@safe' declarations in the" +
                  " current template's SoyDoc (same behavior for public and private templates)." +
                  "    ADVANCED: Infers safe print tags based on '@safe' declarations in SoyDoc" +
                  " of public templates. For private templates, infers safe data from the data" +
                  " being passed in all the calls to the private template from the rest of the" +
                  " Soy code (in the same compiled bundle)." +
                  "    Also, for inference levels SIMPLE and ADVANCED, the compiler checks calls" +
                  " between templates. Specifically, if the callee template's SoyDoc specifies" +
                  " '@safe' declarations, then the data being passed in the call must be known" +
                  " to be safe for all of those declared safe data paths. Otherwise, a" +
                  " data-safety-mismatch error is reported.")
  private String safePrintTagsInferenceLevel = "none";

  @Option(name = "--codeStyle",
          usage = "The code style to use when generating PY code ('stringbuilder' or 'concat').")
  private CodeStyle codeStyle = CodeStyle.STRINGBUILDER;

  @Option(name = "--shouldGeneratePydoc",
          usage = "Whether we should generate PYDoc with type info for the Closure Compiler." +
                  " Note the generated PYDoc does not have description text, only types for the" +
                  " benefit of the Closure Compiler.")
  private boolean shouldGeneratePydoc = false;

  @Option(name = "--shouldProvideRequireSoyNamespaces",
          usage = "When this option is used, each generated PY file will contain (a) one single" +
                  " goog.provide statement for the corresponding Soy file's namespace and" +
                  " (b) goog.require statements for the namespaces of the called templates.")
  private boolean shouldProvideRequireSoyNamespaces = false;

  @Option(name = "--shouldDeclareTopLevelNamespaces",
          usage = "[Only applicable when generating regular PY code to define namespaces (i.e." +
                  " not generating goog.provide/goog.require).] When this option is set to" +
                  " false, each generated PY file will not attempt to declare the top-level" +
                  " name in its namespace, instead assuming the top-level name is already" +
                  " declared in the global scope. E.g. for namespace aaa.bbb, the code will not" +
                  " attempt to declare aaa, but will still define aaa.bbb if it's not already" +
                  " defined.")
  private boolean shouldDeclareTopLevelNamespaces = true;

  @Option(name = "--locales",
          usage = "[Required for generating localized PY] Comma-delimited list of locales for" +
                  " which to generate localized PY. There will be one output PY file for each" +
                  " combination of input Soy file and locale.",
          handler = MainClassUtils.StringListOptionHandler.class)
  private List<String> locales = Lists.newArrayList();

  @Option(name = "--messageFilePathFormat",
          usage = "[Required for generating localized PY] A format string that specifies how to" +
                  " build the path to each translated messages file. The format string can" +
                  " include literal characters as well as the placeholders {INPUT_PREFIX}," +
                  " {LOCALE}, and {LOCALE_LOWER_CASE}. Note {LOCALE_LOWER_CASE} also turns dash" +
                  " into underscore, e.g. pt-BR becomes pt_br. The format string must end with" +
                  " an extension matching the message file format (case-insensitive).")
  private String messageFilePathFormat = "";

  @Option(name = "--bidiGlobalDir",
          usage = "The bidi global directionality (ltr=1, rtl=-1) to use when processing bidi" +
                  " functions/directives. Only applicable if your Soy code uses bidi" +
                  " functions/directives. Also note that this flag is usually not necessary if" +
                  " a message file is provided, because in that case the bidi global" +
                  " directionality is simply inferred from the message bundle.")
  private int bidiGlobalDir = 0;

  @Option(name = "--cssHandlingScheme",
          usage = "The scheme to use for handling 'css' commands. Specifying 'literal' will" +
                  " cause command text to be inserted as literal text. Specifying 'reference'" +
                  " will cause command text to be evaluated as a data or global reference." +
                  " Specifying 'goog' will cause generation of calls goog.getCssName. This" +
                  " option has no effect if the Soy code does not contain 'css' commands.")
  private String cssHandlingScheme = "literal";

  @Option(name = "--compileTimeGlobalsFile",
          usage = "The path to a file containing the mappings for global names to be substituted" +
                  " at compile time. Each line of the file should have the format" +
                  " \"<global_name> = <primitive_data>\" where primitive_data is a valid Soy" +
                  " expression literal for a primitive type (null, boolean, integer, float, or" +
                  " string). Empty lines and lines beginning with \"//\" are ignored. The file" +
                  " should be encoded in UTF-8. If you need to generate a file in this format" +
                  " from Java, consider using the utility" +
                  " SoyUtils.generateCompileTimeGlobalsFile().")
  private String compileTimeGlobalsFile = "";

  @Option(name = "--messagePluginModule",
          usage = "Specifies the full class name of a Guice module that binds a SoyMsgPlugin." +
                  " If not specified, the default is" +
                  " com.google.template.soy.xliffmsgplugin.XliffMsgPluginModule, which binds" +
                  " the XliffMsgPlugin.")
  private String messagePluginModule = XliffMsgPluginModule.class.getName();

  @Option(name = "--pluginModules",
          usage = "Specifies the full class names of Guice modules for function plugins and" +
                  " print directive plugins (comma-delimited list).")
  private String pluginModules = "";

  /** The remaining arguments after parsing command-line flags. */
  @Argument
  private List<String> arguments = Lists.newArrayList();


  /**
   * Compiles a set of Soy files into corresponding PY source files.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   * @throws IOException If there are problems reading the input files or writing the output file.
   * @throws SoySyntaxException If a syntax error is detected.
   */
  public static void main(String[] args) throws IOException, SoySyntaxException {
    (new SoyToPySrcCompiler()).execMain(args);
  }


  private SoyToPySrcCompiler() {}


  private void execMain(String[] args) throws IOException, SoySyntaxException {

    CmdLineParser cmdLineParser = MainClassUtils.parseFlags(this, args, USAGE_PREFIX);
    if (arguments.size() == 0) {
      MainClassUtils.exitWithError("Must provide list of Soy files.", cmdLineParser, USAGE_PREFIX);
    }
    if (outputPathFormat.length() == 0) {
      MainClassUtils.exitWithError(
          "Must provide the output path format.", cmdLineParser, USAGE_PREFIX);
    }

    Injector injector = MainClassUtils.createInjector(messagePluginModule, pluginModules);

    // Create SoyPySrcOptions.
    SoyPySrcOptions pySrcOptions = new SoyPySrcOptions();
    pySrcOptions.setCodeStyle(codeStyle);
    pySrcOptions.setShouldGeneratePydoc(shouldGeneratePydoc);
    pySrcOptions.setShouldProvideRequireSoyNamespaces(shouldProvideRequireSoyNamespaces);
    pySrcOptions.setShouldDeclareTopLevelNamespaces(shouldDeclareTopLevelNamespaces);
    pySrcOptions.setBidiGlobalDir(bidiGlobalDir);

    // Create SoyFileSet.
    SoyFileSet.Builder sfsBuilder = injector.getInstance(SoyFileSet.Builder.class);
    String inputPrefixStr = inputPrefix;
    for (String arg : arguments) {
      sfsBuilder.add(new File(inputPrefixStr + arg));
    }
    String cssHandlingSchemeUc = cssHandlingScheme.toUpperCase();
    sfsBuilder.setCssHandlingScheme(
        cssHandlingSchemeUc.equals("GOOG") ?
        CssHandlingScheme.BACKEND_SPECIFIC : CssHandlingScheme.valueOf(cssHandlingSchemeUc));
    sfsBuilder.setSafePrintTagsInferenceLevel(
        SafePrintTagsInferenceLevel.valueOf(safePrintTagsInferenceLevel.toUpperCase()));
    if (compileTimeGlobalsFile.length() > 0) {
      sfsBuilder.setCompileTimeGlobals(new File(compileTimeGlobalsFile));
    }
    SoyFileSet sfs = sfsBuilder.build();

    // Compile.
    if (locales.size() == 0) {
      // Not generating localized PY.
      sfs.compileToPySrcFiles(outputPathFormat, inputPrefix, pySrcOptions, locales, null);

    } else {
      // Generating localized PY.
      sfs.compileToPySrcFiles(
          outputPathFormat, inputPrefix, pySrcOptions, locales, messageFilePathFormat);
    }
  }

}
