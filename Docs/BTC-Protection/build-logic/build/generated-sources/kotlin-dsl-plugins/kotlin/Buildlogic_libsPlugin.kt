/**
 * Precompiled [buildlogic.libs.gradle.kts][Buildlogic_libs_gradle] script plugin.
 *
 * @see Buildlogic_libs_gradle
 */
public
class Buildlogic_libsPlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Buildlogic_libs_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
