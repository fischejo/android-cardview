package de.pecheur.card;

import android.os.Build;

public class CardUtil {

    /**
     * @return true when the caller can use CardView in its environment.
     */
    public static boolean supportsCardView() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }
}