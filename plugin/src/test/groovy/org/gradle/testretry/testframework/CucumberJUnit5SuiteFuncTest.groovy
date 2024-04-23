/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.testretry.testframework

import org.gradle.testretry.AbstractFrameworkFuncTest

class CucumberJUnit5SuiteFuncTest extends AbstractFrameworkFuncTest {

    String baseBuildScript() {
        // append yet another plugin
        super.baseBuildScript()
            .replace("id 'org.gradle.test-retry'", "id 'org.gradle.test-retry'\n                id 'com.gradle.cucumber.companion' version '1.0.1'")
            .replace("tasks.named(\"test\").configure {", "tasks.named(\"test\").configure {\n                useJUnitPlatform()\n")
            .replace("dependencies { testImplementation \"junit:junit:4.13.2\" }", "")
    }

    def setup() {
        buildFile << """
            dependencies {
                testImplementation 'org.junit.platform:junit-platform-suite:1.10.2'
                testImplementation 'io.cucumber:cucumber-java:7.17.0'
                testImplementation 'io.cucumber:cucumber-junit-platform-engine:7.17.0'
            }
            
            test.retry.maxRetries = 1
        """
    }

    def "retries scenarios independently from each other (gradle version #gradleVersion)"(String gradleVersion) {
        given:
        writeFlakyFeatureFile()
        writeFlakyStepDefinitions()
        writeCucumberEntrypoint()

        when:
        def runner = gradleRunner(gradleVersion as String)
        def result = runner.build()

        then:
        with(result.output) {
            it.count("Another passing scenario PASSED") == 1
            it.count("Passing scenario PASSED") == 1
            it.count("Flaky scenario FAILED") == 1
            it.count("Flaky scenario PASSED") == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    private void writeFlakyFeatureFile() {
        writeFeatureFile("flaky_and_passing_scenarios", """
            Feature: Retry feature
              
              Scenario: Passing scenario
                Given a passing setup
                When a passing action
                Then a passing verification
            
              Scenario: Flaky scenario
                Given a passing setup
                When a passing action
                Then a flaky verification
        """)

        writeFeatureFile("just_another_feature", """
            Feature: Just another feature
              
              Scenario: Another passing scenario
                Given a passing setup
                When a passing action
                Then a passing verification
        """)
    }

    private void writeFeatureFile(String name, String content) {
        def featuresDir = "src/test/resources/features"
        new File(testProjectDir.root, featuresDir).mkdirs()
        testProjectDir.newFile("$featuresDir/${name}.feature") << content
    }

    private void writeFlakyStepDefinitions() {
        writeJavaTestSource """
            package acme;

            import io.cucumber.java.en.Given;
            import io.cucumber.java.en.Then;
            import io.cucumber.java.en.When;

            public class RetryFeatureStepDefinitions {
                @Given("a passing setup")
                public void passingSetup() {
                }

                @When("a passing action")
                public void passingAction() {
                }

                @Then("a passing verification")
                public void passingVerification() {
                }

                @Then("a flaky verification")
                public void flakyVerification() {
                    ${flakyAssert("cucumber")}
                }
            }
        """
    }

    private writeCucumberEntrypoint() {
        def resourcesDir = "src/test/resources"
        new File(testProjectDir.root, resourcesDir).mkdirs()
        def cucumberConfig = "cucumber.plugin=pretty,summary\n"
        testProjectDir.newFile("$resourcesDir/junit-platform.properties") << cucumberConfig
    }
}
