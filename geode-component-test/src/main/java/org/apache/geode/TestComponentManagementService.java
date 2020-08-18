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
package org.apache.geode;

import java.util.Optional;

import org.apache.geode.services.management.ComponentManagementService;
import org.apache.geode.services.management.impl.ComponentIdentifier;
import org.apache.geode.services.module.ModuleService;
import org.apache.geode.services.result.ServiceResult;
import org.apache.geode.services.result.impl.Success;

public class TestComponentManagementService implements ComponentManagementService {
  @Override
  public boolean canCreateComponent(ComponentIdentifier componentIdentifier) {
    return componentIdentifier.getComponentName().equals("TEST");
  }

  @Override
  public ServiceResult<Boolean> init(ModuleService moduleService, Object[] args) {
    System.err.println("Initialized TestComponentManagementService");
    return Success.SUCCESS_TRUE;
  }

  @Override
  public Optional getInitializedComponent() {
    return Optional.empty();
  }

  @Override
  public ServiceResult<Boolean> close(Object[] args) {
    return Success.SUCCESS_TRUE;
  }
}
