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
package org.apache.geode.modules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.Test;

import org.apache.geode.test.junit.rules.gfsh.GfshScript;

public class LoggingAcceptanceTest extends AbstractDockerizedAcceptanceTest {

  public LoggingAcceptanceTest(String launchCommand) throws IOException, InterruptedException {
    launch(launchCommand);
  }

  @Test
  public void testServerProducesLogging() {
    assertThat(GfshScript.of(getLocatorGFSHConnectionString(),
        "show log --member=server").execute(gfshRule).getOutputText())
            .contains("SystemLog").doesNotContain("There is no log for this member");
  }
}
