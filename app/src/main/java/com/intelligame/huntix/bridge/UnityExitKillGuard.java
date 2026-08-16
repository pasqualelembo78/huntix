package com.intelligame.huntix.bridge;

import com.unity3d.player.UnityPlayer;

/** Impedisce a UnityPlayer.destroy() di SIGKILLare il processo.
 *  Unity assume che la sua activity sia la sola activity della app: alla chiusura
 *  chiama Process.killProcess(Process.myPid()) (SIG 9, visibile come
 *  reason=SIGNALED signal=9). In questa app la activity Unity chiude solo
 *  per tornare alla Home, quindi il self-kill va disabilitato. */
public final class UnityExitKillGuard {

    private UnityExitKillGuard() {
    }

    public static void disableSelfKill(UnityPlayer player) {
        if (player == null) {
            return;
        }
        try {
            java.lang.reflect.Field f = UnityPlayer.class.getDeclaredField("mProcessKillRequested");
            f.setAccessible(true);
            f.setBoolean(player, false);
        } catch (Throwable t) {
            android.util.Log.w("HuntixBridge", "disableSelfKill fallita", t);
        }
    }
}
