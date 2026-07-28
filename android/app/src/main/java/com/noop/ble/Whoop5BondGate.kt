package com.noop.ble

import com.noop.protocol.DeviceFamily

/** What the caller must do before it may send the 5/MG CLIENT_HELLO. */
enum class BondAction {
    /** The link is usable — send CLIENT_HELLO now. */
    PROCEED,

    /** No bond exists — ask the OS for one and defer the handshake until it lands. */
    CREATE_BOND,

    /** A bond is already in flight — do nothing and let it finish. */
    WAIT,
}

/**
 * Decides whether a WHOOP 5/MG link is ready for its CLIENT_HELLO, or needs an OS bond first.
 *
 * Pure (no Android imports) so it is unit-testable without a BLE seam, matching [BondRefusalGiveUp].
 *
 * ## Why this is needed
 *
 * `docs/BLE_REVERSE_ENGINEERING.md`: every `fd4b` operation requires an ENCRYPTED link, and without a
 * bond the operation "simply stalls while the stack waits for an encryption that never arrives". On
 * Apple, CoreBluetooth raises that just-works bond transparently the first time an encrypted
 * characteristic is touched, which is why writing CLIENT_HELLO with response is sufficient there.
 *
 * **Android does not do this.** Nothing in the app called `BluetoothDevice.createBond()`, so on a
 * strap with no existing bond the write never completes: `onCharacteristicWrite` never fires, so the
 * refusal counters ([BondRefusalGiveUp], the pairing hint) never increment, and the link is dropped at
 * ~4s — before the 7s bond watchdog can bounce it. The result is an unbounded connect/disconnect loop
 * with none of the three existing guards engaging, and only standard-profile HR working (that rides
 * the unencrypted 0x180D profile, which is why it looks healthy).
 *
 * Evidence, Galaxy S22+ / Android 16 / WHOOP 5.0 (non-MG): 265 bond events for the strap, every one
 * `BOND_STATE_NONE` — never BONDING, never BONDED — and no Link Key Types entry, while the same phone
 * held a full `LE_KEY_PENC/PID/LENC/LID` set for another LE device. The bond was never attempted.
 *
 * `docs/ANDROID.md` already anticipated this: *"you may also need to handle
 * `BluetoothDevice.createBond()` / the `ACTION_BOND_STATE_CHANGED` broadcast depending on the OEM
 * stack."* It was never implemented.
 */
object Whoop5BondGate {
    /** Mirrors `BluetoothDevice.BOND_NONE`, kept literal so this file stays Android-free. */
    const val BOND_NONE = 10

    /** Mirrors `BluetoothDevice.BOND_BONDING`. */
    const val BOND_BONDING = 11

    /** Mirrors `BluetoothDevice.BOND_BONDED`. */
    const val BOND_BONDED = 12

    /**
     * What to do for [family] given the peripheral's current OS [bondState].
     *
     * WHOOP 4.0 is never gated: its bond is the app-level confirmed write, which works today, and
     * forcing an OS bond on it would change a hardware-verified path for no reason.
     *
     * An unrecognised [bondState] is treated as unbonded rather than ready — reading it as "bonded"
     * would send CLIENT_HELLO onto an unencrypted link and reproduce the stall this gate prevents.
     */
    fun actionFor(family: DeviceFamily, bondState: Int): BondAction = when {
        family != DeviceFamily.WHOOP5 -> BondAction.PROCEED
        bondState == BOND_BONDED -> BondAction.PROCEED
        bondState == BOND_BONDING -> BondAction.WAIT
        else -> BondAction.CREATE_BOND
    }
}
