/**
 * Precompiled [buildlogic.common.gradle.kts][Buildlogic_common_gradle] script plugin.
 *
 * @see Buildlogic_common_gradle
 */
public
class Buildlogic_commonPlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Buildlogic_common_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
