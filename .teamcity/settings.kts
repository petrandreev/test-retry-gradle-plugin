import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2018_2.ui.*

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2019.1"

project {
    val quickFeedbackBuildType = buildType("Quick Feedback") {
        steps {
            gradle {
                tasks = "clean build"
                buildFile = ""
            }
        }
    }
    val crossVersionTestLinux = buildType("CrossVersionTest Linux - Java 1.8") {
        steps {
            gradle {
                tasks = "clean testAll"
                buildFile = ""
                gradleParams = "-s $useGradleInternalScansServer"
                param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
            }
        }

        dependencies {
            snapshot(quickFeedbackBuildType) {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
    }
    val verifyAllBuildType = buildType("Verify all") {
        triggers.vcs {}

        dependencies {
            snapshot(quickFeedbackBuildType) {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
            snapshot(crossVersionTestLinux) {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
    }
    buildType("Publish Snapshot") {
        description = "Publish Gradle Test Retry Plugin snapshot to Gradle's Artifactory repository"

        params {
            java8Home(Os.linux)
            text("ARTIFACTORY_USERNAME", "bot-build-tool", allowEmpty = true)
            password("ARTIFACTORY_PASSWORD", "credentialsJSON:2b7529cd-77cd-49f4-9416-9461f6ac9018", display = ParameterDisplay.HIDDEN)
            text("systemProp.org.gradle.internal.publish.checksums.insecure", "true")
        }

        steps {
            gradle {
                tasks = "clean devSnapshot publishPluginMavenPublicationToGradleBuildInternalRepository"
                gradleParams = "-PartifactoryUsername=%ARTIFACTORY_USERNAME% -PartifactoryPassword=%ARTIFACTORY_PASSWORD% $useGradleInternalScansServer"
                param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
                buildFile = ""
            }
        }
        dependencies {
            snapshot(verifyAllBuildType) {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
    }

}
