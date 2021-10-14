package org.gradle.testretry

class CoreSettingsPluginFuncTest extends AbstractGeneralPluginFuncTest {

    def setup() {
        //TODO: construct subproject

    }

    @Override
    String baseBuildScript() {
        """
            plugins {
                id 'java'
            }

            repositories {
                mavenCentral()
            }

            ${buildConfiguration()}

            tasks.named("test").configure {
                testLogging {
                    events "passed", "skipped", "failed"
                }
            }
        """
    }

    def "has no effect when all tests pass (gradle version #gradleVersion)"() {
        when:
        settingsFile.text = """
            plugins {
                id("org.gradle.test-retry")
            }
            retry {
                failOnPassedAfterRetry.set(true)
            }
        """

        successfulTest()

        then:
        def result = gradleRunner(gradleVersion).build()

        and:
        result.output.count('PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "retries failed tests (gradle version #gradleVersion)"() {
        given:
        settingsFile.newWriter().withWriter {
            it << """
            plugins {
                id("org.gradle.test-retry")
            }
            retry {
                maxRetries.set(1)
                maxFailures.set(10)
                failOnPassedAfterRetry.set(true)
            }
        """
        }

        successfulTest()
        failedTest()

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then: 'Only the failed test is retried a second time'
        result.output.count('PASSED') == 1

        // 2 individual tests FAILED + 1 overall task FAILED + 1 overall build FAILED
        result.output.count('FAILED') == 2 + 1 + 1

        assertTestReportContains("SuccessfulTests", reportedTestName("successTest"), 1, 0)
        assertTestReportContains("FailedTests", reportedTestName("failedTest"), 0, 2)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }
}
