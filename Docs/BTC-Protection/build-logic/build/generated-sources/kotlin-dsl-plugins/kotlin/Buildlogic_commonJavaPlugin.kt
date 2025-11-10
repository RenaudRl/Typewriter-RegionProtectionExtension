/**
 * Precompiled [buildlogic.common-java.gradle.kts][Buildlogic_common_java_gradle] script plugin.
 *
 * @see Buildlogic_common_java_gradle
 */
public
class Buildlogic_commonJavaPlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Buildlogic_common_java_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
