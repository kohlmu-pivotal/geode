/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.modules;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import org.apache.geode.test.junit.rules.gfsh.GfshRule;

@RunWith(Parameterized.class)
public abstract class AbstractDockerizedAcceptanceTest {
  @ClassRule
  public static GfshRule gfshRule = new GfshRule();
  @ClassRule
  public static TemporaryFolder stagingTempDir = new TemporaryFolder();

  protected static GenericContainer<?> geodeContainer = setupDockerContainer();

  private String locatorGFSHConnectionString;

  protected static String currentLaunchCommand;
  private static String previousLocatorGFSHConnectionString;

  static int locatorPort;
  static int serverPort;
  static int httpPort;
  static int redisPort;
  static int memcachePort;

  public String getLocatorGFSHConnectionString() {
    return locatorGFSHConnectionString == null ? previousLocatorGFSHConnectionString
        : locatorGFSHConnectionString;
  }

  protected void launch(String launchCommand) throws IOException, InterruptedException {
    if (!geodeContainer.isRunning()) {
      startDockerContainer(launchCommand);
    } else {
      if (!currentLaunchCommand.equals(launchCommand)) {
        geodeContainer.stop();
        startDockerContainer(launchCommand);
      }
    }
  }

  private void startDockerContainer(String launchCommand) throws IOException, InterruptedException {
    geodeContainer.withCommand("./launch.sh");
    try {
      geodeContainer.start();
      currentLaunchCommand = launchCommand;
    } catch (Exception e) {
      e.printStackTrace();
    }

    geodeContainer.execInContainer("/geode/bin/gfsh", "-e",
        "start locator --name=locator1 --port=10334 --J=-Dgemfire.enable-network-partition-detection=false");
    geodeContainer.execInContainer("/geode/bin/gfsh", "-e", "connect", "-e",
        "configure pdx --read-serialized=true");
    Container.ExecResult execInContainer = geodeContainer.execInContainer("/geode/bin/gfsh", "-e",
        "start server --name=server --locators=localhost[10334] --redis-port=6379 --memcached-port=5678 --server-port=40404 --http-service-port=9090 --start-rest-api "
            + launchCommand);

    System.out.println("execInContainer.getStdout() = " + execInContainer.getStdout());
    System.err.println("execInContainer.getStderr() = " + execInContainer.getStderr());

    String host = geodeContainer.getHost();
    locatorPort = geodeContainer.getMappedPort(10334);
    serverPort = geodeContainer.getMappedPort(40404);
    int jmxHttpPort = geodeContainer.getMappedPort(7070);
    httpPort = geodeContainer.getMappedPort(9090);
    redisPort = geodeContainer.getMappedPort(6379);
    memcachePort = geodeContainer.getMappedPort(5678);

    previousLocatorGFSHConnectionString = locatorGFSHConnectionString =
        "connect --locator=" + host + "[" + locatorPort + "] --use-http --url=http://localhost:"
            + jmxHttpPort + "/gemfire/v1";
  }

  private static GenericContainer<?> setupDockerContainer() {
    String currentDirectory = System.getProperty("user.dir");
    geodeContainer = new GenericContainer<>(
        new ImageFromDockerfile()
            .withDockerfile(new File(
                currentDirectory.substring(0, currentDirectory.indexOf("build"))
                    .concat("build/docker/Dockerfile"))
                        .toPath()));
    geodeContainer.withExposedPorts(9090, 10334, 40404, 1099, 7070, 6379, 5678);
    geodeContainer.withCreateContainerCmdModifier(cmd -> {
      long availableProcessors = Runtime.getRuntime().availableProcessors();
      cmd.getHostConfig()
          .withCpuCount(availableProcessors);
    });
    geodeContainer.waitingFor(Wait.forHealthcheck());
    geodeContainer.withStartupTimeout(Duration.ofSeconds(120));
    return geodeContainer;
  }

  @Parameterized.Parameters
  public static List<String> getStartServerCommand() {
    return Arrays.asList("", "--experimental");
  }
}
