// Loads the convention plugins' classpath once at the root: Spotless 8 shares one build service across modules and
// fails when each module loads the plugin into its own classloader.
plugins {
    id("nx-l2.java-conventions") apply false
}
