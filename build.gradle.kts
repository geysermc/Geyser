plugins {
    // Ensure AP works in eclipse (no effect on other IDEs)
    eclipse
    id("geyser.base-conventions")
    id("io.freefair.lombok") apply false
}
