package com.prism.music

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/**
 * Runs Prism's in-app tests. Before Prism starts it puts [RecordedNetwork] in front of every request, so a test never
 * depends on YouTube or a lyrics site that day. `-Pandroid.testInstrumentationRunnerArguments.record=true` records the
 * answers the tests need instead (live), into /sdcard/prism-tests/recorded/ for tools/record.ps1 to pull into
 * src/androidTest/assets/recorded/.
 */
class PrismTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        // Android calls this before PrismApp.onCreate, which builds Prism's network client with it.
        TestHooks.network = RecordedNetwork(record = arguments?.getString("record") == "true")
        // Prism TV's tests (@TvTest) run on the TV emulators, which ask for them (.claude/tv-1.8/tvsuite.ps1); any other
        // run leaves them out. A filter, not a second notAnnotation name: the orchestrator only keeps the first.
        val args = arguments ?: Bundle()
        if (args.getString("annotation")?.contains(TvTest::class.java.name) != true) {
            args.putString("filter", listOfNotNull(args.getString("filter"), NoTvTests::class.java.name).joinToString(","))
        }
        super.onCreate(args)
    }
}

/** Leaves Prism TV's tests (@TvTest classes) out of a run. */
class NoTvTests : org.junit.runner.manipulation.Filter() {
    override fun shouldRun(description: org.junit.runner.Description): Boolean =
        description.testClass?.isAnnotationPresent(TvTest::class.java) != true

    override fun describe() = "no Prism TV tests"
}
