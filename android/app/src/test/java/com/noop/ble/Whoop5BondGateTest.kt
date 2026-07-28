package com.noop.ble

import com.noop.protocol.DeviceFamily
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The 5/MG bond gate: what to do before sending CLIENT_HELLO, given the OS bond state.
 *
 * Why this exists. `docs/BLE_REVERSE_ENGINEERING.md` establishes that every `fd4b` operation needs an
 * ENCRYPTED link first, and that on Apple CoreBluetooth raises that bond transparently the first time
 * an encrypted characteristic is touched. The Android port copied the Apple approach — write
 * CLIENT_HELLO to fd4b0002 with response and expect the stack to bond — but Android does not do this.
 *
 * Observed on a Galaxy S22+ (Android 16) with a WHOOP 5.0: 265 bond events for the strap, every one
 * `BOND_STATE_NONE`, never BONDING, never BONDED, and no link keys stored — while the same phone holds
 * a full LE key set for another LE device. The bond is never ATTEMPTED, so the confirmed write stalls
 * forever, `onCharacteristicWrite` never fires, and the link is torn down at ~4s — before the 7s bond
 * watchdog can fire and before any INSUFFICIENT_AUTHENTICATION can be counted. All three of the
 * existing guards (refusal counting, bond watchdog, give-up) therefore stay dormant and it loops.
 *
 * The fix is to ask for the bond explicitly. This gate is the decision; the caller performs it.
 *
 * WHOOP 4.0 is deliberately untouched: its "bond" is the app-level confirmed write, which works today.
 */
class Whoop5BondGateTest {

    // ── WHOOP 4.0 — never gated, its confirmed-write bond already works ─────

    @Test
    fun whoop4AlwaysProceedsRegardlessOfBondState() {
        for (state in listOf(Whoop5BondGate.BOND_NONE, Whoop5BondGate.BOND_BONDING, Whoop5BondGate.BOND_BONDED)) {
            assertEquals(
                "WHOOP 4.0 must not be gated on an OS bond (state=$state)",
                BondAction.PROCEED,
                Whoop5BondGate.actionFor(DeviceFamily.WHOOP4, state),
            )
        }
    }

    // ── WHOOP 5/MG — the actual fix ─────────────────────────────────────────

    @Test
    fun whoop5WithNoBondRequestsOne() {
        assertEquals(
            BondAction.CREATE_BOND,
            Whoop5BondGate.actionFor(DeviceFamily.WHOOP5, Whoop5BondGate.BOND_NONE),
        )
    }

    @Test
    fun whoop5AlreadyBondedProceedsStraightToHello() {
        assertEquals(
            BondAction.PROCEED,
            Whoop5BondGate.actionFor(DeviceFamily.WHOOP5, Whoop5BondGate.BOND_BONDED),
        )
    }

    @Test
    fun whoop5MidBondWaitsRatherThanRequestingAgain() {
        // Calling createBond() while BONDING is in flight makes the stack reject the request and can
        // cancel the attempt already running — the loop would then never converge.
        assertEquals(
            BondAction.WAIT,
            Whoop5BondGate.actionFor(DeviceFamily.WHOOP5, Whoop5BondGate.BOND_BONDING),
        )
    }

    @Test
    fun unknownBondStateIsTreatedAsUnbondedNotAsReady() {
        // An unrecognised state must never be read as "bonded" — that would send CLIENT_HELLO onto an
        // unencrypted link and reproduce the stall this gate exists to prevent.
        assertEquals(
            BondAction.CREATE_BOND,
            Whoop5BondGate.actionFor(DeviceFamily.WHOOP5, 999),
        )
    }
}
