/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.modules;


import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.modules.filter.MultiplePathFilterBuilder;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;

/**
 * A set of utilities that simplify working with {@link ModuleSpec}s.
 */
public class ModuleSpecUtil {

  public static ModuleSpec.Builder createBuilder(String name, boolean addJavaBaseDeps) {
    return ModuleSpec.build(name, addJavaBaseDeps);
  }

  public static ModuleSpec removeDependencyFromSpec(ModuleSpec moduleSpec,
      String... dependenciesToRemove) {
    ModuleSpec.Builder builder =
        createBuilderAndRemoveDependencies(moduleSpec, dependenciesToRemove);
    return builder.create();
  }

  public static ModuleSpec addSystemClasspathDependency(ModuleSpec spec) {
    if (spec instanceof ConcreteModuleSpec) {
      return createBuilder(spec).addDependency(new LocalDependencySpecBuilder()
          .setImportFilter(PathFilters.acceptAll())
          .setExport(true)
          .setLocalLoader(ClassLoaderLocalLoader.SYSTEM)
          .setLoaderPaths(JDKPaths.JDK)
          .build())
          .create();
    }
    return spec;
  }

  private static ModuleSpec.Builder createBuilder(ModuleSpec moduleSpec) {
    return createBuilderAndRemoveDependencies(moduleSpec);
  }

  private static ModuleSpec.Builder createBuilderAndRemoveDependencies(ModuleSpec moduleSpec,
      String... dependenciesToRemove) {
    List<String> listOfDependenciesToRemove = Arrays.asList(dependenciesToRemove);
    ModuleSpec.Builder builder = ModuleSpec.build(moduleSpec.getName(), false);
    ConcreteModuleSpec concreteModuleSpec = (ConcreteModuleSpec) moduleSpec;

    for (ResourceLoaderSpec resourceLoader : concreteModuleSpec.getResourceLoaders()) {
      builder.addResourceRoot(resourceLoader);
    }

    Map<String, DependencySpec> dependencies = new HashMap<>();
    for (DependencySpec dependency : concreteModuleSpec.getDependencies()) {
      boolean addDependency = true;
      String name = dependency.toString();
      if (dependency instanceof ModuleDependencySpec) {
        ModuleDependencySpec moduleDependencySpec = (ModuleDependencySpec) dependency;
        name = moduleDependencySpec.getName();
        if (listOfDependenciesToRemove.contains(name)) {
          addDependency = false;
        }
      }
      if (addDependency) {
        dependencies.put(name, dependency);
      }
    }
    dependencies.forEach((k, v) -> builder.addDependency(v));

    builder.setMainClass(concreteModuleSpec.getMainClass());
    builder.setAssertionSetting(concreteModuleSpec.getAssertionSetting());
    builder.setFallbackLoader(concreteModuleSpec.getFallbackLoader());
    builder.setModuleClassLoaderFactory(concreteModuleSpec.getModuleClassLoaderFactory());
    builder.setClassFileTransformer(concreteModuleSpec.getClassFileTransformer());
    builder.setPermissionCollection(concreteModuleSpec.getPermissionCollection());
    builder.setVersion(concreteModuleSpec.getVersion());
    concreteModuleSpec.getProperties().forEach(builder::addProperty);

    return builder;
  }

  public static ModuleSpec addModuleDependencyToSpec(ModuleSpec moduleSpec, String dependencyName) {

    final MultiplePathFilterBuilder exportBuilder = PathFilters.multiplePathFilterBuilder(true);
    exportBuilder.addFilter(PathFilters.getMetaInfServicesFilter(), true);
    exportBuilder.addFilter(PathFilters.getMetaInfSubdirectoriesFilter(), true);
    exportBuilder.addFilter(PathFilters.getMetaInfFilter(), true);

    ModuleSpec.Builder builder = createBuilder(moduleSpec);
    builder.addDependency(new ModuleDependencySpecBuilder()
        .setName(dependencyName)
        .setImportFilter(PathFilters.getDefaultImportFilterWithServices())
        .setExportFilter(exportBuilder.create())
        .build());

    return builder.create();
  }

  public static ModuleSpec addExcludeFilter(ModuleSpec moduleSpec, List<String> pathsToExclude,
      List<String> pathsToExcludeChildrenOf,
      String moduleToPutExcludeOn) {
    ModuleSpec.Builder builder =
        createBuilderAndRemoveDependencies(moduleSpec, moduleToPutExcludeOn);
    MultiplePathFilterBuilder pathFilterBuilder = PathFilters.multiplePathFilterBuilder(true);

    for (String path : pathsToExclude) {
      pathFilterBuilder.addFilter(PathFilters.is(path), false);
    }

    for (String path : pathsToExcludeChildrenOf) {
      pathFilterBuilder.addFilter(PathFilters.isOrIsChildOf(path), false);
    }

    builder.addDependency(new ModuleDependencySpecBuilder()
        .setName(moduleToPutExcludeOn)
        .setImportFilter(pathFilterBuilder.create())
        .build());

    return builder.create();
  }

  /**
   * Checks if the moduleSpec is dependent on the module represented by moduleName.
   * <p>
   *
   * @return a {@link Boolean} which is null if the ModuleSpec does does not depend on the module,
   *         true if it does depend on the module and the module is exported, and false if the
   *         module is not
   *         exported.
   */
  public static Boolean isModuleDependentOnModule(ModuleSpec moduleSpec,
      String moduleName) {
    if (moduleSpec instanceof ConcreteModuleSpec) {
      ConcreteModuleSpec concreteModuleSpec = (ConcreteModuleSpec) moduleSpec;
      for (DependencySpec dependencySpec : concreteModuleSpec.getDependencies()) {
        if (dependencySpec instanceof ModuleDependencySpec) {
          ModuleDependencySpec spec = (ModuleDependencySpec) dependencySpec;
          if (spec.getName().equals(moduleName)) {
            PathFilter exportFilter = spec.getExportFilter();
            // We assume that anything besides a 'Reject' is possibly an export
            return !exportFilter.toString().equals("Reject");
          }
        }
      }
    }
    return null;
  }
}
