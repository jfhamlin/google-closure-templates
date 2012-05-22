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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryProvider;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.internal.GenPyExprsVisitor.GenPyExprsVisitorFactory;
import com.google.template.soy.pysrc.internal.TranslateToPyExprVisitor.TranslateToPyExprVisitorFactory;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.shared.internal.ApiCallScope;
import com.google.template.soy.shared.internal.BackendModuleUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;

import java.util.Map;
import java.util.Set;


/**
 * Guice module for the Python Source backend.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class PySrcModule extends AbstractModule {


  @Override protected void configure() {

    // Install requisite modules.
    install(new SharedModule());

    // Bind providers of factories (created via assisted inject).
    bind(GenPyExprsVisitorFactory.class)
        .toProvider(FactoryProvider.newFactory(
            GenPyExprsVisitorFactory.class, GenPyExprsVisitor.class));
    bind(TranslateToPyExprVisitorFactory.class)
        .toProvider(FactoryProvider.newFactory(
            TranslateToPyExprVisitorFactory.class, TranslateToPyExprVisitor.class));

    // Bind unscoped providers for parameters in ApiCallScope (these throw exceptions).
    bind(SoyPySrcOptions.class)
        .toProvider(GuiceSimpleScope.<SoyPySrcOptions>getUnscopedProvider())
        .in(ApiCallScope.class);
  }


  /**
   * Builds and provides the map of SoyPySrcFunctions (name to function).
   * @param soyFunctionsSet The installed set of SoyFunctions (from Guice Multibinder). Each
   *     SoyFunction may or may not implement SoyPySrcFunction.
   */
  @Provides
  @Singleton
  Map<String, SoyPySrcFunction> provideSoyPySrcFunctionsMap(Set<SoyFunction> soyFunctionsSet) {

    return BackendModuleUtils.buildBackendSpecificSoyFunctionsMap(
        SoyPySrcFunction.class, soyFunctionsSet);
  }


  /**
   * Builds and provides the map of SoyPySrcDirectives (name to directive).
   * @param soyDirectivesSet The installed set of SoyDirectives (from Guice Multibinder). Each
   *     SoyDirective may or may not implement SoyPySrcDirective.
   */
  @Provides
  @Singleton
  Map<String, SoyPySrcPrintDirective> provideSoyPySrcDirectivesMap(
      Set<SoyPrintDirective> soyDirectivesSet) {

    return BackendModuleUtils.buildBackendSpecificSoyDirectivesMap(
        SoyPySrcPrintDirective.class, soyDirectivesSet);
  }

}
