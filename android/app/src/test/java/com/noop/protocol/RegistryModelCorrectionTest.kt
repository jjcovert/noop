package com.noop.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Registry `model` label correction once the connected strap's family is known.
 *
 * Background: #716 taught the scan callback to stamp a concrete model over the seeded "WHOOP"
 * label. It derived that label from the user-selected `WhoopModel`, which defaults to WHOOP4, and
 * stamped on the FIRST scan callback — before the advertised services were read. A WHOOP 5.0 that
 * matched the scan filter while the selection still said 4.0 was therefore stamped "WHOOP 4.0",
 * and because the stamp is one-shot and only ever matched rows still labelled "WHOOP", the wrong
 * value could never be corrected — including by the fallback rotation that then connected the
 * strap as 5/MG. The visible damage is the same as #716's: skin temp decoded on the wrong ADC
 * scale (#938) and day charts drawn for the wrong generation.
 *
 * [DeviceFamily.correctedRegistryModel] moves the decision onto the family actually discovered on
 * the peripheral, and makes it a *correction* rather than a one-shot stamp.
 *
 * Deliberately conservative: it returns null (leave the label alone) whenever the stored spelling
 * already resolves to the confirmed family, so the wizard's own spellings are never churned.
 */
class RegistryModelCorrectionTest {

    // ── The bug: a 5.0 stamped as 4.0 must be correctable ───────────────────

    @Test
    fun whoop4LabelOnAConfirmedWhoop5IsCorrected() {
        assertEquals(
            "WHOOP 5.0 / MG",
            DeviceFamily.correctedRegistryModel("WHOOP 4.0", DeviceFamily.WHOOP5),
        )
    }

    @Test
    fun wizardBare40LabelOnAConfirmedWhoop5IsCorrected() {
        assertEquals(
            "WHOOP 5.0 / MG",
            DeviceFamily.correctedRegistryModel("4.0", DeviceFamily.WHOOP5),
        )
    }

    @Test
    fun whoop5LabelOnAConfirmedWhoop4IsCorrected() {
        assertEquals(
            "WHOOP 4.0",
            DeviceFamily.correctedRegistryModel("WHOOP 5.0 / MG", DeviceFamily.WHOOP4),
        )
    }

    // ── #716's original case: the family-neutral seed gets a concrete label ──

    @Test
    fun seededWhoopLabelIsStampedForEitherFamily() {
        // "WHOOP" resolves to the WHOOP5 fallback, so a family comparison alone would leave a
        // 5/MG install showing the bare seed forever. Stamp it explicitly instead.
        assertEquals("WHOOP 5.0 / MG", DeviceFamily.correctedRegistryModel("WHOOP", DeviceFamily.WHOOP5))
        assertEquals("WHOOP 4.0", DeviceFamily.correctedRegistryModel("WHOOP", DeviceFamily.WHOOP4))
    }

    @Test
    fun blankAndNullLabelsAreStamped() {
        assertEquals("WHOOP 5.0 / MG", DeviceFamily.correctedRegistryModel(null, DeviceFamily.WHOOP5))
        assertEquals("WHOOP 4.0", DeviceFamily.correctedRegistryModel("", DeviceFamily.WHOOP4))
        assertEquals("WHOOP 4.0", DeviceFamily.correctedRegistryModel("   ", DeviceFamily.WHOOP4))
    }

    // ── Correct labels are left alone, including the wizard's own spellings ──

    @Test
    fun alreadyCorrectLabelsAreNotChurned() {
        assertNull(DeviceFamily.correctedRegistryModel("WHOOP 4.0", DeviceFamily.WHOOP4))
        assertNull(DeviceFamily.correctedRegistryModel("4.0", DeviceFamily.WHOOP4))
        assertNull(DeviceFamily.correctedRegistryModel("WHOOP 5.0 / MG", DeviceFamily.WHOOP5))
        // The Add-Device wizard writes this bare spelling; rewriting it to the picker label would
        // be pointless churn on every connect.
        assertNull(DeviceFamily.correctedRegistryModel("5.0 MG", DeviceFamily.WHOOP5))
    }

    // ── Non-WHOOP rows must never be relabelled as a WHOOP ───────────────────

    @Test
    fun nonWhoopLabelsAreNeverRewritten() {
        // forRegistryModel maps unknown labels to the WHOOP5 fallback, so a confirmed WHOOP4
        // would otherwise "correct" an Oura row into "WHOOP 4.0" and destroy its identity. The
        // caller is expected to pass only WHOOP rows, but the guard belongs here too: this
        // function is the one place that decides whether a label is wrong.
        assertNull(DeviceFamily.correctedRegistryModel("Oura Ring Gen3", DeviceFamily.WHOOP4))
        assertNull(DeviceFamily.correctedRegistryModel("Oura Ring Gen3", DeviceFamily.WHOOP5))
        assertNull(DeviceFamily.correctedRegistryModel("garmin-hrm", DeviceFamily.WHOOP4))
        assertNull(DeviceFamily.correctedRegistryModel("Polar H10", DeviceFamily.WHOOP4))
    }
}
