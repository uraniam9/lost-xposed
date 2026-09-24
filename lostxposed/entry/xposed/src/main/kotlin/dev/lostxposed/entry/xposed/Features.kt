package dev.lostxposed.entry.xposed

import dev.lostxposed.core.engine.FeatureRegistry
import dev.lostxposed.features.displayprofiles.DisplayProfilesFeature
import dev.lostxposed.features.hardwarekeys.HardwareKeysFeature
import dev.lostxposed.features.noop.NoOpFeature
import dev.lostxposed.features.notificationrules.NotificationRulesFeature
import dev.lostxposed.features.powerinspector.PowerInspectorFeature
import dev.lostxposed.features.smartstatusbar.SmartStatusBarFeature
import dev.lostxposed.features.textengine.TextEngineFeature

/**
 * The single place features are registered. Adding a feature is one line here plus its own
 * Gradle module — nothing else in the spine changes.
 */
object Features {
    val registry: FeatureRegistry = FeatureRegistry.build {
        register(SelfCheckFeature.FACTORY)
        register(NoOpFeature.FACTORY)
        register(DisplayProfilesFeature.FACTORY)
        register(TextEngineFeature.FACTORY)
        register(PowerInspectorFeature.FACTORY)
        register(NotificationRulesFeature.FACTORY)
        register(HardwareKeysFeature.FACTORY)
        register(SmartStatusBarFeature.FACTORY)
    }
}
