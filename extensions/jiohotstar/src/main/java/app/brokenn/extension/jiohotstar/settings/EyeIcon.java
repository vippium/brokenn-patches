package app.brokenn.extension.jiohotstar.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * Eye toggle icons: Material Symbols (rounded, filled) "visibility" and
 * "visibility_off", pre-rendered OUTLINE PNGs embedded as base64 so they never
 * depend on host-app resources. Tinted per state at runtime.
 */
public final class EyeIcon {

    private static final String VISIBLE_PNG =
        "iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAYAAADDPmHLAAAB5klEQVR42u3di3XDIBBEUfpvmrQQ"
            + "n8TS7M59DRh4IwnzPQcAAAAAAAAAAAAAAAAAAACYxP0FWmm54L+ilcuECwTpwkC6MBAvCMQLAvGC"
            + "QLwgkC8ExAvCEPkN5ST+wUYUhKCGU69S+epYKF69S+U/Vba2NoiXn1CWje1RIT+xY1YRgo3i08q4"
            + "Un7T4FLSp4l8IZhdiQ3j+OND0Cz/7bqMlL91JnFye44r7OYp3REh2DywIwQL5U/8LseG4M1CTfgu"
            + "b2nr8U//BhFRb4G3C5M4g5gYvq+FoFn+tBCskv/J7yf1gVY58PQXvwU8/eVvgSmdr4YAPO4jdf2e"
            + "ADzkJOH7mzohkt4mfy5DygCEALzkZtJ/71N45JwACIAACEBQP0AAlnUCBUAAjAO0jwMYCSwfCTQX"
            + "YC5ACEwHWw1kVZA1Ad0rgoSAfKuC7Q2YuTFEXW0K/fj3r02iudum7v3ORs47/NCI03IugO3hw7eI"
            + "bwxBpXwhIL8+BOQXHxblhLDiEJBfemagswEHHYe6SbwTw8tPNDvHnQHq7taQGY1x3RbiziDyH+yd"
            + "q5MgRC4JIz5w+HZ62QThujdYCMgXBOIFgXhBIF4QiBcE4oWBdGEgHeGXRwMAAAAAAAAAAAAAAAAA"
            + "AAAAAAAA8E/8AFHpdtWNVod3AAAAAElFTkSuQmCC";

    private static final String HIDDEN_PNG =
        "iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAYAAADDPmHLAAACWUlEQVR42u3dQZLDMAgFUd3/0sx2"
            + "anZTiS34vD6BhLpkh2A4BwAAAAAAAAAAAAAAAKhfiMbSg/+L6Cw+fBIQgAQOv7cEBH5RgFuBrBcg"
            + "QMNg1UVIcDlA1QgCvByYagwJXghIDYEEDwWihkGCLwehhkKC5QLEiEACEpCACL0lmCQWCcL3GS/C"
            + "xk2TgASPiUACMtTKANxaHxEabTqpsGWNBJ2fgbfXES9B57fhtLWs+22ceDNFSdA9OZJ6M605/E83"
            + "nPp4WnX4n2w6+RE1tkg0SYIt1ddfXygJhkrwzQUmFZqu+BDnicVVUMl5tARPLqrri9C07OXobwRJ"
            + "0Ph9YMKLWhcJ4m6BSZk7EjwQBxmy+30GrsVg81+4boEG3UEmSzD+Fkj4Ajit3cyr+6+QT8AJMOx5"
            + "N6GUalJMxh5+58paAgwU4O0s3eicRKoAb+YpCBBcbUyAgE5g3gEW/Qq4UWi6SoDJreAmCdA6G3gG"
            + "Vx5PmZ+wLh1cNa/k/KRVJ5HgnNUd27c1hHT4QXUBCRKMrQskQVhl8MZmkGY0kKAcfhMJqna0cm2/"
            + "xoR2L10DPOamIoH+iiTYdvWToF+282wfKZciwepOYSTIGM9LguEl+FqyLi80jfqca6IEUa3jEzpv"
            + "dZLgnMWDEzY/ys4xPeOaCMbELpgjQALzBEhgtgAJ4ruMP92xlARLZ/6SgAQkIAIJiEACIpCACCQg"
            + "AglWyiBZJLEkY5gshLQx/HcAEoAEIAFIABKABCABSAASYGNjCGgOAQ0iMH0uMQZKIFKLJRChxRKI"
            + "DAAAAAAAAID/8wMRhjKY2JH6hgAAAABJRU5ErkJggg==";

    private static final Map<Integer, Drawable> CACHE = new HashMap<>();

    private EyeIcon() {
    }

    /** Open eye - the tab is visible. */
    public static Drawable show(Context context, int color) {
        return icon(context, VISIBLE_PNG, color);
    }

    /** Eye with a slash - the tab is hidden. */
    public static Drawable hide(Context context, int color) {
        return icon(context, HIDDEN_PNG, color);
    }

    private static Drawable icon(Context context, String base64, int color) {
        Integer key = (color << 1) | (base64 == VISIBLE_PNG ? 0 : 1);
        Drawable cached = CACHE.get(key);
        if (cached != null) return cached;
        Drawable drawable;
        try {
            byte[] data = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            Bitmap src = BitmapFactory.decodeByteArray(data, 0, data.length);
            Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(),
                Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(src, 0f, 0f, paint);
            drawable = new BitmapDrawable(context.getResources(), out);
        } catch (Throwable t) {
            return null;
        }
        CACHE.put(key, drawable);
        return drawable;
    }
}
