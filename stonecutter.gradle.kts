plugins {
    id("dev.kikugie.stonecutter")
}
stonecutter.active("1.21.1-neoforge")

stonecutter.parameters {
    val currentProject = current.project
    val currentLoader = currentProject.substringAfterLast('-')
    constants.match(currentLoader, "fabric", "neoforge")
}
