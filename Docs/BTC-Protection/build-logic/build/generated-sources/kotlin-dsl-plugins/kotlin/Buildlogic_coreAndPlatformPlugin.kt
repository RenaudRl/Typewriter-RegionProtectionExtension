/**
 * Precompiled [buildlogic.core-and-platform.gradle.kts][Buildlogic_core_and_platform_gradle] script plugin.
 *
 * @see Buildlogic_core_and_platform_gradle
 */
public
class Buildlogic_coreAndPlatformPlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Buildlogic_core_and_platform_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
