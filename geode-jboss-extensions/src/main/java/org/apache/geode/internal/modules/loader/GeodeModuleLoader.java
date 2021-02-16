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
package org.apache.geode.internal.modules.loader;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.modules.AliasModuleSpec;
import org.jboss.modules.ConcreteModuleSpec;
import org.jboss.modules.DelegatingModuleLoader;
import org.jboss.modules.JDKModuleFinder;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;

import org.apache.geode.internal.modules.finder.GeodeCompositeModuleFinder;
import org.apache.geode.internal.modules.finder.GeodeJarModuleFinder;
import org.apache.geode.internal.modules.finder.LocalModuleFinder;

/**
 * This {@link ModuleLoader} will be used to bootstrap the JBoss system when the server starts. It
 * allows us access to the internals of JBoss so we can load and unload modules at runtime.
 */
public class GeodeModuleLoader extends DelegatingModuleLoader implements AutoCloseable {

  private static final GeodeCompositeModuleFinder compositeModuleFinder =
      new GeodeCompositeModuleFinder();
  private static final ModuleLoader JDK_MODULE_LOADER =
      new ModuleLoader(JDKModuleFinder.getInstance());
  private final String THIRD_PARTY_MODULE_NAME = "thirdParty";
  private final String CUSTOM_JAR_DEPLOYMENT_MODULE_NAME = "geode-custom-jar-deployments";
  private final String CORE_MODULE_NAME = "geode-core";
  private final String GEODE_BASE_PACKAGE_PATH = "org/apache/geode";

  public GeodeModuleLoader() {
    super(JDK_MODULE_LOADER, new ModuleFinder[] {compositeModuleFinder});
    compositeModuleFinder.addModuleFinder("__default__", new LocalModuleFinder());
  }

  public void registerModule(String moduleName, String path,
      List<String> moduleDependencyNames) {
    if (moduleName.startsWith("geode-")) {
      throw new RuntimeException(
          "Registering deployments starting with \"geode-\" is not allowed.");
    }
    if (findLoadedModuleLocal(moduleName) != null) {
      unregisterModule(moduleName);
    }
    compositeModuleFinder.addModuleFinder(moduleName,
        new GeodeJarModuleFinder(moduleName, path, moduleDependencyNames));
  }

  public void unregisterModule(String moduleName) {
    if (moduleName.startsWith("geode-")) {
      throw new RuntimeException(
          "Unregistering deployments starting with \"geode-\" is not allowed.");
    }
    compositeModuleFinder.removeModuleFinder(moduleName);
    unloadModuleLocal(moduleName, findLoadedModuleLocal(moduleName));
  }


  @Override
  protected Module preloadModule(String name) throws ModuleLoadException {

    if (name.contains(CORE_MODULE_NAME) && findLoadedModuleLocal(name) == null
        && findModule(THIRD_PARTY_MODULE_NAME) != null
        && findModule(CUSTOM_JAR_DEPLOYMENT_MODULE_NAME) != null) {
      Module coreModule = super.preloadModule(name);

      excludeThirdPartyPathsFromModule(name, CUSTOM_JAR_DEPLOYMENT_MODULE_NAME);

      unloadModuleLocal(name, coreModule);
    }
    return super.preloadModule(name);
  }

  @Override
  protected ModuleSpec findModule(String name) throws ModuleLoadException {
    return super.findModule(name);
  }

  @Override
  public void close() throws Exception {

  }

  public void registerModuleAsDependencyOfModule(String moduleName, String moduleToDependOn)
      throws ModuleLoadException {
    compositeModuleFinder.addDependencyToModule(moduleName, moduleToDependOn);
    relinkModule(moduleName);
    relinkModule(CORE_MODULE_NAME);
  }

  private void relinkModule(String moduleName) throws ModuleLoadException {
    ModuleSpec moduleSpec = findModule(moduleName);
    if (moduleSpec instanceof AliasModuleSpec) {
      AliasModuleSpec aliasModuleSpec = (AliasModuleSpec) moduleSpec;
      relinkModule(aliasModuleSpec.getAliasName());
    } else {
      setAndRelinkDependencies(findLoadedModuleLocal(moduleName),
          Arrays.asList(((ConcreteModuleSpec) moduleSpec).getDependencies()));
    }
  }

  public void unregisterModuleDependencyFromModule(String moduleDependencyToRemove)
      throws ModuleLoadException {
    List<String> modulesToRelink =
        compositeModuleFinder.removeDependencyFromModule(moduleDependencyToRemove);
    for (String moduleName : modulesToRelink) {
      relinkModule(moduleName);
    }
  }

  private void excludeThirdPartyPathsFromModule(String moduleToPutExcludeFilterOn,
      String moduleToExcludeFrom)
      throws ModuleLoadException {
    Module thirdPartyModule = loadModule(THIRD_PARTY_MODULE_NAME);
    Set<String> exportedPaths = thirdPartyModule.getExportedPaths();
    List<String> restrictPaths =
        exportedPaths.stream().filter(packageName -> packageName.split("/").length <= 2)
            .collect(Collectors.toList());
    List<String> restrictPathsAndChildren =
        exportedPaths.stream().filter(packageName -> packageName.split("/").length == 3)
            .collect(Collectors.toList());

    restrictPathsAndChildren.add(GEODE_BASE_PACKAGE_PATH);

    compositeModuleFinder.addExcludeFilterToModule(moduleToPutExcludeFilterOn, moduleToExcludeFrom,
        restrictPaths, restrictPathsAndChildren);
  }
}
