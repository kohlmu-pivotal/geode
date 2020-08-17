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
package org.apache.geode.services.management.impl;

import static org.apache.geode.services.result.impl.Success.SUCCESS_TRUE;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.Logger;

import org.apache.geode.services.bootstrapping.BootstrappingService;
import org.apache.geode.services.management.ComponentManagementService;
import org.apache.geode.services.management.ManagementService;
import org.apache.geode.services.module.ModuleService;
import org.apache.geode.services.result.ServiceResult;
import org.apache.geode.services.result.impl.Failure;
import org.apache.geode.services.result.impl.Success;

/**
 * An implementation of {@link ManagementService} that manages (creates and destroys) Geode
 * components. This implementation uses the {@link BootstrappingService} to ensure the environment
 * is properly setup before creating components using {@link ComponentManagementService}s.
 */
public class ManagementServiceImpl implements ManagementService {
  // private final ModuleService moduleService;
  // private final BootstrappingService bootstrappingService;

  private final Map<String, ApplicationContext> applicationContexts = new ConcurrentHashMap<>();
  private final Map<String, ComponentManagementService<?>> createdModuleManagementServices;
  private final Logger logger;

  public ManagementServiceImpl(Logger logger) {
    this.logger = logger;
    // this.bootstrappingService = bootstrappingService;
    // this.moduleService = bootstrappingService.getModuleService();
    this.createdModuleManagementServices = new ConcurrentHashMap<>();
  }

  @Override
  public ServiceResult<Boolean> createApp(String appName,
      BootstrappingService bootstrappingService) {
    if (!applicationContexts.containsKey(appName)) {
      applicationContexts.put(appName, new ApplicationContext(bootstrappingService));
    }
    return SUCCESS_TRUE;
  }

  @Override
  public ServiceResult<Boolean> destroyApp(String appName) {
    return null;
  }

  /**
   * Uses the {@link BootstrappingService} to ensure the environment is correctly setup to create
   * the Geode Component described by the {@link ComponentIdentifier}. Then performs a service
   * lookup
   * to find the {@link ComponentManagementService} associated with the Component to create it.
   *
   *
   * @param componentIdentifier a {@link ComponentIdentifier} representing the Geode Component to be
   *        created.
   * @param args arguments to be used in creating the specified Component.
   * @return {@link Success} when the Component is created and {@link Failure} on failure.
   */
  @Override
  public ServiceResult<Boolean> createComponent(String appName, String instanceName,
      final ComponentIdentifier componentIdentifier,
      final Object... args) {

    ApplicationContext applicationContext = applicationContexts.get(appName);
    if (applicationContext == null) {
      return Failure.of("No application context for name: " + appName
          + " was found. Please ensure one is created");
    }

    if (componentIdentifier == null) {
      return Failure.of("Component Identifier cannot be null");
    }

    if (createdModuleManagementServices.containsKey(instanceName)) {
      // return Failure.of("Component for name: " + componentIdentifier.getComponentName()
      return Failure.of("Component for name: " + instanceName
          + ", has already been created");
    }

    ServiceResult<Boolean> bootstrappingResult =
        applicationContext.getBootstrappingService().bootStrapModule(componentIdentifier);

    if (bootstrappingResult.isSuccessful()) {
      ServiceResult<Set<ComponentManagementService>> loadServiceResult =
          applicationContext.getModuleService().loadService(ComponentManagementService.class);

      if (loadServiceResult.isSuccessful()) {
        ServiceResult<ComponentManagementService> serviceResult =
            loadAndCreateServiceForComponentIdentifier(componentIdentifier,
                applicationContext.getModuleService(),
                loadServiceResult.getMessage(), args);

        if (serviceResult.isSuccessful()) {
          createdModuleManagementServices.put(instanceName, serviceResult.getMessage());
          return Success.of(true);
        } else {
          return Failure.of(serviceResult.getErrorMessage());
        }
      }
      return Failure.of(loadServiceResult.getErrorMessage());
    } else {
      return bootstrappingResult;
    }
  }

  /**
   * Find the correct {@link ComponentManagementService} for the Geode Component described by the
   * {@link ComponentIdentifier}, store it, and use it to create the component.
   *
   * @param componentIdentifier {@link ComponentIdentifier} describing the Geode Component to be
   *        loaded
   *        and created.
   * @param componentManagementServices {@link Set< ComponentManagementService >} from which one
   *        will be selected to create the
   *        component.
   * @param args {@link Object[]} to be passed to the component when creating it.
   * @return {@link Optional<Success<Boolean>>} when the component is created,
   *         {@link Optional<Failure>} on failure, or an empty {@code Option} if not suitable
   *         {@link ComponentManagementService} can be found.
   */
  private ServiceResult<ComponentManagementService> loadAndCreateServiceForComponentIdentifier(
      final ComponentIdentifier componentIdentifier,
      final ModuleService moduleService,
      final Set<ComponentManagementService> componentManagementServices,
      final Object[] args) {

    Optional<ComponentManagementService> matchingComponentServiceType =
        componentManagementServices.stream()
            .filter(componentManagementService -> componentManagementService
                .canCreateComponent(componentIdentifier))
            .findFirst();
    if (matchingComponentServiceType.isPresent()) {
      ComponentManagementService<?> componentManagementService = matchingComponentServiceType.get();
      ServiceResult<Boolean> createServiceInstanceResult =
          componentManagementService.init(moduleService, args);

      if (createServiceInstanceResult.isSuccessful()) {
        return Success.of(componentManagementService);
      } else {
        return Failure.of(createServiceInstanceResult.getErrorMessage());
      }

    } else {
      return Failure.of("Could not find ComponentManagementService for component: "
          + componentIdentifier.getComponentName());
    }
  }

  /**
   * Destroys the Geode Component represented by the {@link ComponentIdentifier} using the stored
   * {@link ComponentManagementService}.
   *
   *
   * @param componentIdentifier a {@link ComponentIdentifier} representing the Geode Component to be
   *        destroyed.
   * @return {@link Success} when the specified component is destroyed and {@link Failure}
   *         on failure.
   */
  @Override
  public ServiceResult<Boolean> closeComponent(String appName, String instanceName,
      final ComponentIdentifier componentIdentifier,
      Object... args) {

    ApplicationContext applicationContext = applicationContexts.get(appName);
    if (applicationContext == null) {
      return Failure.of("No application context for name: " + appName
          + " was found. Please ensure one is created");
    }

    if (componentIdentifier == null) {
      return Failure.of("Component Identifier cannot be null");
    }

    Optional<ComponentManagementService<?>> moduleManagementService =
        Optional.ofNullable(createdModuleManagementServices.get(instanceName));

    if (moduleManagementService.isPresent()) {
      return moduleManagementService.get().close(args);
    } else {
      return SUCCESS_TRUE;
    }
  }

  private class ApplicationContext {

    private final BootstrappingService bootstrappingService;
    private final ModuleService moduleService;

    public ApplicationContext(
        BootstrappingService bootstrappingService) {
      this.bootstrappingService = bootstrappingService;
      this.moduleService = bootstrappingService.getModuleService();
    }

    public BootstrappingService getBootstrappingService() {
      return bootstrappingService;
    }

    public ModuleService getModuleService() {
      return moduleService;
    }
  }
}
