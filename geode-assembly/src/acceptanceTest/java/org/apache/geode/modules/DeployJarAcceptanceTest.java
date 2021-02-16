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


import static org.apache.geode.test.util.ResourceUtils.createTempFileFromResource;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.Container;

import org.apache.geode.test.awaitility.GeodeAwaitility;
import org.apache.geode.test.compiler.JarBuilder;
import org.apache.geode.test.junit.rules.gfsh.GfshScript;

public class DeployJarAcceptanceTest extends AbstractDockerizedAcceptanceTest {
  private static final JarBuilder jarBuilder = new JarBuilder();
  private static File jarFile;
  private static File jarFileV2;
  private static File anotherJarFile;

  public DeployJarAcceptanceTest(String launchCommand) throws IOException, InterruptedException {
    launch(launchCommand);
  }

  @BeforeClass
  public static void setup() throws IOException {
    File stagingDir = stagingTempDir.newFolder("staging");
    jarFile = new File(stagingDir, "myJar-1.0.jar");
    jarFileV2 = new File(stagingDir, "myJar-2.0.jar");
    anotherJarFile = new File(stagingDir, "anotherJar-1.0.jar");
    jarBuilder.buildJarFromClassNames(jarFile, "SomeClass");
    jarBuilder.buildJarFromClassNames(jarFileV2, "SomeClass", "SomeClassVersionTwo");
    jarBuilder.buildJarFromClassNames(anotherJarFile, "SomeOtherClass");
  }

  // @Override
  // public String getLocatorGFSHConnectionString() {
  // return "connect";
  // }

  @After
  public void teardown() {
    System.out.println(GfshScript.of(getLocatorGFSHConnectionString(), "undeploy")
        .execute(gfshRule).getOutputText());
  }

  @Test
  public void testDeployJar() throws IOException {
    GfshScript.of(getLocatorGFSHConnectionString(),
        "deploy --jar=" + jarFile.getCanonicalPath()).execute(gfshRule);

    assertThat(GfshScript.of(getLocatorGFSHConnectionString(), "list deployed")
        .execute(gfshRule).getOutputText()).contains(jarFile.getName()).contains("JAR Location");
  }

  @Test
  public void testDeployJarWithDeploymentName() throws IOException {
    GfshScript.of(getLocatorGFSHConnectionString(),
        "deploy --name=myDeployment --jar=" + jarFile.getCanonicalPath()).execute(gfshRule);

    assertThat(GfshScript.of(getLocatorGFSHConnectionString(), "list deployed")
        .execute(gfshRule).getOutputText()).contains("myDeployment").contains("JAR Location");
  }

  @Test
  public void testUndeployJar() throws IOException {
    GfshScript.of(getLocatorGFSHConnectionString(),
        "deploy --jar=" + jarFile.getCanonicalPath()).execute(gfshRule);

    assertThat(
        GfshScript.of(getLocatorGFSHConnectionString(),
            "undeploy --jar=" + jarFile.getName())
            .execute(gfshRule).getOutputText()).contains(jarFile.getName())
                .contains("Un-Deployed From JAR Location");

    assertThat(GfshScript.of(getLocatorGFSHConnectionString(), "list deployed")
        .execute(gfshRule).getOutputText()).doesNotContain(jarFile.getName());
  }

  @Test
  public void testUndeployWithNothingDeployed() {
    assertThat(
        GfshScript.of(getLocatorGFSHConnectionString(),
            "undeploy --jar=" + jarFile.getName())
            .execute(gfshRule).getOutputText()).contains(jarFile.getName() + " not deployed");
  }

  @Test
  public void testRedeployNewJar() throws IOException {
    GfshScript.of(getLocatorGFSHConnectionString(),
        "deploy --jar=" + jarFile.getCanonicalPath()).execute(gfshRule);

    assertThat(
        GfshScript.of(getLocatorGFSHConnectionString(),
            "undeploy --jar=" + jarFile.getName())
            .execute(gfshRule).getOutputText()).contains(jarFile.getName())
                .contains("Un-Deployed From JAR Location");

    assertThat(GfshScript.of(getLocatorGFSHConnectionString(), "list deployed")
        .execute(gfshRule).getOutputText()).doesNotContain(jarFile.getName());

    GfshScript
        .of(getLocatorGFSHConnectionString(),
            "deploy --jar=" + anotherJarFile.getCanonicalPath())
        .execute(gfshRule);
    assertThat(GfshScript.of(getLocatorGFSHConnectionString(), "list deployed")
        .execute(gfshRule).getOutputText()).contains(anotherJarFile.getName());
  }

  @Test
  public void testUpdateJar() throws IOException {
    GfshScript.of(getLocatorGFSHConnectionString(),
        "deploy --jar=" + jarFile.getCanonicalPath()).execute(gfshRule);

    GfshScript.of(getLocatorGFSHConnectionString(),
        "deploy --jar=" + jarFileV2.getCanonicalPath()).execute(gfshRule);

    assertThat(GfshScript.of(getLocatorGFSHConnectionString(),
        "list deployed").execute(gfshRule).getOutputText()).contains(jarFileV2.getName())
            .doesNotContain(jarFile.getName());
  }

  @Test
  public void testDeployMultipleJars() throws IOException {
    GfshScript.of(getLocatorGFSHConnectionString(),
        "deploy --jar=" + jarFile.getCanonicalPath(),
        "deploy --jar=" + anotherJarFile.getCanonicalPath()).execute(gfshRule);

    assertThat(GfshScript.of(getLocatorGFSHConnectionString(),
        "list deployed").execute(gfshRule).getOutputText()).contains(jarFile.getName())
            .contains(anotherJarFile.getName());
  }

  @Test
  public void testDeployFunction() throws IOException {
    File source = loadTestResource("/example/test/function/ExampleFunction.java");

    File outputJar = new File(stagingTempDir.newFolder(), "function.jar");
    jarBuilder.buildJar(outputJar, source);

    GfshScript.of(getLocatorGFSHConnectionString(), "deploy --jars=" + outputJar.getCanonicalPath())
        .execute(gfshRule);

    assertThat(GfshScript.of(getLocatorGFSHConnectionString(), "list functions").execute(gfshRule)
        .getOutputText()).contains("ExampleFunction");

    assertThat(
        GfshScript.of(getLocatorGFSHConnectionString(), "execute function --id=ExampleFunction")
            .execute(gfshRule)
            .getOutputText()).contains("SUCCESS");
  }

  @Test
  public void testDeployAndUndeployFunction() throws IOException {
    File source = loadTestResource("/example/test/function/ExampleFunction.java");

    File outputJar = new File(stagingTempDir.newFolder(), "function.jar");
    jarBuilder.buildJar(outputJar, source);

    GfshScript.of(getLocatorGFSHConnectionString(), "deploy --jars=" + outputJar.getCanonicalPath())
        .execute(gfshRule);

    assertThat(GfshScript.of(getLocatorGFSHConnectionString(), "list functions").execute(gfshRule)
        .getOutputText()).contains("ExampleFunction");

    assertThat(
        GfshScript.of(getLocatorGFSHConnectionString(), "execute function --id=ExampleFunction")
            .execute(gfshRule)
            .getOutputText()).contains("SUCCESS");

    GfshScript
        .of(getLocatorGFSHConnectionString(), "undeploy --jars=" + outputJar.getName())
        .execute(gfshRule);

    assertThat(GfshScript.of(getLocatorGFSHConnectionString(), "list functions").execute(gfshRule)
        .getOutputText()).doesNotContain("ExampleFunction");
  }

  @Test
  public void testDeployPojo() throws IOException, InterruptedException {
    File functionSource = loadTestResource("/example/test/function/PojoFunction.java");
    File pojoSource = loadTestResource("/version1/example/test/pojo/ExamplePojo.java");

    File outputJar = new File(stagingTempDir.newFolder(), "functionAndPojo.jar");
    jarBuilder.buildJar(outputJar, pojoSource, functionSource);

    System.out.println(GfshScript
        .of(getLocatorGFSHConnectionString(),
            "create disk-store --name=ExampleDiskStore --dir="
                + stagingTempDir.newFolder().getCanonicalPath())
        .execute(gfshRule).getOutputText());

    System.out.println(GfshScript
        .of(getLocatorGFSHConnectionString(),
            "create region --name=/ExampleRegion --type=REPLICATE_PERSISTENT --disk-store=ExampleDiskStore")
        .execute(gfshRule).getOutputText());

    System.out.println(GfshScript
        .of(getLocatorGFSHConnectionString(), "deploy --jars=" + outputJar.getAbsolutePath())
        .execute(gfshRule));

    System.out.println(
        GfshScript.of(getLocatorGFSHConnectionString(), "execute function --id=PojoFunction")
            .execute(gfshRule).getOutputText());

    assertThat(GfshScript
        .of(getLocatorGFSHConnectionString(), "query --query=\"SELECT * FROM /ExampleRegion\"")
        .execute(gfshRule).getOutputText()).contains("John");

    Container.ExecResult result = geodeContainer.execInContainer("./killServer.sh");
    System.out.println(result.getStdout());
    System.out.println(result.getStderr());

    Container.ExecResult startServerResult = geodeContainer.execInContainer("/geode/bin/gfsh", "-e",
        "connect", "-e",
        "start server --name=server --locators=localhost[10334] --redis-port=6379 --memcached-port=5678 --server-port=40404 --http-service-port=9090 --start-rest-api "
            + currentLaunchCommand);

    System.out.println(startServerResult.getStdout());
    System.out.println(startServerResult.getStderr());
    assertThat(startServerResult.getStderr()).isEmpty();

    GeodeAwaitility.await().pollDelay(5, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS)
        .atMost(30, TimeUnit.SECONDS)
        .until(() -> GfshScript.of(getLocatorGFSHConnectionString(), "list regions")
            .execute(gfshRule).getOutputText().contains("ExampleRegion"));

    assertThat(GfshScript
        .of(getLocatorGFSHConnectionString(), "query --query=\"SELECT * FROM /ExampleRegion\"")
        .execute(gfshRule).getOutputText()).contains("John");

    GfshScript.of(getLocatorGFSHConnectionString(), "destroy region --name=/ExampleRegion")
        .execute(gfshRule);
  }

  @Test
  public void testClassesNotAccessibleAfterUndeploy() throws IOException, InterruptedException {
    File functionSource = loadTestResource("/example/test/function/PojoFunction.java");
    File pojoSource = loadTestResource("/version1/example/test/pojo/ExamplePojo.java");

    File pojoJar = new File(stagingTempDir.newFolder(), "pojo.jar");
    jarBuilder.buildJar(pojoJar, "example.test.pojo.ExamplePojo", pojoSource);

    File functionJar = new File(stagingTempDir.newFolder(), "function.jar");
    jarBuilder.buildJar(functionJar, "example.test.function.PojoFunction", functionSource);

    System.out.println(GfshScript
        .of(getLocatorGFSHConnectionString(),
            "create region --name=/ExampleRegion --type=PARTITION")
        .execute(gfshRule).getOutputText());

    System.out.println(GfshScript
        .of(getLocatorGFSHConnectionString(), "deploy --jars=" + pojoJar.getAbsolutePath())
        .execute(gfshRule));

    System.out.println(GfshScript
        .of(getLocatorGFSHConnectionString(), "deploy --jars=" + functionJar.getAbsolutePath())
        .execute(gfshRule));

    assertThat(
        GfshScript.of(getLocatorGFSHConnectionString(), "execute function --id=PojoFunction")
            .execute(gfshRule).getOutputText()).contains("SUCCESS");

    System.out.println(GfshScript
        .of(getLocatorGFSHConnectionString(), "undeploy --jars=" + pojoJar.getName())
        .execute(gfshRule));

    System.out.println(GfshScript
        .of(getLocatorGFSHConnectionString(), "undeploy --jars=" + functionJar.getName())
        .execute(gfshRule));

    System.out.println(GfshScript
        .of(getLocatorGFSHConnectionString(), "deploy --jars=" + functionJar.getAbsolutePath())
        .execute(gfshRule));

    assertThat(GfshScript.of(getLocatorGFSHConnectionString(), "execute function --id=PojoFunction")
        .expectExitCode(1)
        .execute(gfshRule).getOutputText())
            .contains("java.lang.NoClassDefFoundError: example/test/pojo/ExamplePojo");

    GfshScript.of(getLocatorGFSHConnectionString(), "destroy region --name=/ExampleRegion")
        .execute(gfshRule);
  }

  @Test
  public void testSpringVersionsDoNotConflict() {
    String jarPath = System.getenv("DEPLOY_TEST_SPRING_JAR");

    assertThat(jarPath).isNotNull();

    System.out.println(GfshScript
        .of(getLocatorGFSHConnectionString(), "deploy --jar=" + jarPath)
        .execute(gfshRule).getOutputText());

    if (currentLaunchCommand.contains("experimental")) {
      assertThat(GfshScript
          .of(getLocatorGFSHConnectionString(), "execute function --id=" + "SpringFunction")
          .execute(gfshRule).getOutputText()).contains("Salutations, Earth");
    } else {
      assertThat(GfshScript
          .of(getLocatorGFSHConnectionString(), "execute function --id=" + "SpringFunction")
          .expectExitCode(1)
          .execute(gfshRule).getOutputText()).doesNotContain("Salutations, Earth");
    }
  }

  @Test
  public void testDeployFailsWhenFunctionIsInExcludedPath() throws IOException {
    if (currentLaunchCommand.contains("experimental")) {
      JarBuilder jarBuilder = new JarBuilder();
      File excludedFunctionSource = loadTestResource("/org/apache/geode/ExcludedFunction.java");
      File includedFunctionSource = loadTestResource("/example/test/function/ExampleFunction.java");
      File functionJar = new File(stagingTempDir.newFolder(), "function.jar");
      jarBuilder.buildJar(functionJar, excludedFunctionSource, includedFunctionSource);

      GfshScript
          .of(getLocatorGFSHConnectionString(), "deploy --jars=" + functionJar.getAbsolutePath())
          .execute(gfshRule).getOutputText();

      assertThat(GfshScript.of(getLocatorGFSHConnectionString(), "list deployed").execute(gfshRule)
          .getOutputText()).doesNotContain("function");

      assertThat(GfshScript
          .of(getLocatorGFSHConnectionString(), "list functions")
          .execute(gfshRule).getOutputText())
              .doesNotContain("ExcludedFunction")
              .doesNotContain("ExampleFunction");
    }
  }

  @Test
  public void testUpdateFunctionVersion() throws IOException {
    File functionVersion1Source =
        loadTestResource("/version1/example/test/function/VersionedFunction.java");
    File functionVersion2Source =
        loadTestResource("/version2/example/test/function/VersionedFunction.java");

    File functionVersion1Jar = new File(stagingTempDir.newFolder(), "function-1.0.0.jar");
    File functionVersion2Jar = new File(stagingTempDir.newFolder(), "function-2.0.0.jar");

    jarBuilder.buildJar(functionVersion1Jar, functionVersion1Source);
    jarBuilder.buildJar(functionVersion2Jar, functionVersion2Source);

    GfshScript.of(getLocatorGFSHConnectionString(),
        "deploy --jar=" + functionVersion1Jar.getAbsolutePath()).execute(gfshRule);
    assertThat(
        GfshScript.of(getLocatorGFSHConnectionString(), "execute function --id=VersionedFunction")
            .execute(gfshRule).getOutputText()).contains("Version1");

    GfshScript.of(getLocatorGFSHConnectionString(),
        "deploy --jar=" + functionVersion2Jar.getAbsolutePath()).execute(gfshRule);
    assertThat(
        GfshScript.of(getLocatorGFSHConnectionString(), "execute function --id=VersionedFunction")
            .execute(gfshRule).getOutputText()).contains("Version2");
  }

  @Test
  public void testUpdatePojoVersion() throws IOException {
    File pojoVersion1Source = loadTestResource("/version1/example/test/pojo/ExamplePojo.java");
    File pojoVersion2Source = loadTestResource("/version2/example/test/pojo/ExamplePojo.java");
    File pojoFunctionSource = loadTestResource("/example/test/function/PojoFunction.java");

    File pojoVersion1Jar = new File(stagingTempDir.newFolder(), "pojo-1.0.0.jar");
    File pojoVersion2Jar = new File(stagingTempDir.newFolder(), "pojo-2.0.0.jar");
    File pojoFunctionJar = new File(stagingTempDir.newFolder(), "pojo-function.jar");

    jarBuilder.buildJar(pojoVersion1Jar, pojoVersion1Source);
    jarBuilder.buildJar(pojoVersion2Jar, pojoVersion2Source);
    jarBuilder.buildJar(pojoFunctionJar, "example.test.function.PojoFunction", pojoFunctionSource);

    GfshScript.of(getLocatorGFSHConnectionString(),
        "create region --name=/ExampleRegion --type=PARTITION")
        .execute(gfshRule);

    GfshScript
        .of(getLocatorGFSHConnectionString(), "deploy --jar=" + pojoVersion1Jar.getAbsolutePath())
        .execute(gfshRule);
    GfshScript
        .of(getLocatorGFSHConnectionString(), "deploy --jar=" + pojoFunctionJar.getAbsolutePath())
        .execute(gfshRule);
    GfshScript.of(getLocatorGFSHConnectionString(), "execute function --id=PojoFunction")
        .execute(gfshRule);
    assertThat(GfshScript.of(getLocatorGFSHConnectionString(),
        "query --query=\"SELECT version FROM /ExampleRegion\"").execute(gfshRule).getOutputText())
            .contains("Version1");

    GfshScript
        .of(getLocatorGFSHConnectionString(), "deploy --jar=" + pojoVersion2Jar.getAbsolutePath())
        .execute(gfshRule);

    GfshScript.of(getLocatorGFSHConnectionString(), "execute function --id=PojoFunction")
        .execute(gfshRule);
    assertThat(GfshScript.of(getLocatorGFSHConnectionString(),
        "query --query=\"SELECT version FROM /ExampleRegion\"").execute(gfshRule).getOutputText())
            .contains("Version2");

    GfshScript.of(getLocatorGFSHConnectionString(), "destroy region --name=/ExampleRegion")
        .execute(gfshRule);
  }

  @Test
  public void testCannotDeployModulesThatStartWithGeode() throws IOException {
    if (currentLaunchCommand.contains("experimental")) {
      File source = loadTestResource("/example/test/pojo/ExamplePojo.java");

      File geodeCoreJar = new File(stagingTempDir.newFolder(), "geode-core.jar");

      jarBuilder.buildJar(geodeCoreJar, source);

      assertThat(GfshScript
          .of(getLocatorGFSHConnectionString(), "deploy --jars=" + geodeCoreJar.getAbsolutePath())
          .expectExitCode(1)
          .execute(gfshRule).getOutputText())
              .contains("Jar names may not start with \"geode-\"");

      assertThat(GfshScript
          .of(getLocatorGFSHConnectionString(), "list deployed").execute(gfshRule).getOutputText())
              .doesNotContain("geode-core");
    }
  }

  private File loadTestResource(String fileName) {
    String filePath =
        createTempFileFromResource(this.getClass(), fileName).getAbsolutePath();
    assertThat(filePath).isNotNull();

    return new File(filePath);
  }
}
