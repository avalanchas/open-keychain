/*
 * Copyright (C) 2012-2014 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2010-2014 Thialfihar <thi@thialfihar.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewAnimator;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTNamedCurves;
import org.bouncycastle.asn1.teletrust.TeleTrusTNamedCurves;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.util.encoders.Hex;
import org.openintents.openpgp.OpenPgpDecryptionResult;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpUtils;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.operations.results.DecryptVerifyResult;
import org.sufficientlysecure.keychain.pgp.KeyRing;
import org.sufficientlysecure.keychain.service.SaveKeyringParcel.Algorithm;
import org.sufficientlysecure.keychain.service.SaveKeyringParcel.Curve;
import org.sufficientlysecure.keychain.util.Log;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class KeyFormattingUtils {

    public static String getAlgorithmInfo(int algorithm, Integer keySize, String oid) {
        return getAlgorithmInfo(null, algorithm, keySize, oid);
    }

    /**
     * Based on <a href="http://tools.ietf.org/html/rfc2440#section-9.1">OpenPGP Message Format</a>
     */
    public static String getAlgorithmInfo(Context context, int algorithm, Integer keySize, String oid) {
        String algorithmStr;

        switch (algorithm) {
            case PublicKeyAlgorithmTags.RSA_GENERAL:
            case PublicKeyAlgorithmTags.RSA_ENCRYPT:
            case PublicKeyAlgorithmTags.RSA_SIGN: {
                algorithmStr = "RSA";
                break;
            }
            case PublicKeyAlgorithmTags.DSA: {
                algorithmStr = "DSA";
                break;
            }

            case PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT:
            case PublicKeyAlgorithmTags.ELGAMAL_GENERAL: {
                algorithmStr = "ElGamal";
                break;
            }

            case PublicKeyAlgorithmTags.ECDSA: {
                if (oid == null) {
                    return "ECDSA";
                }
                String oidName = KeyFormattingUtils.getCurveInfo(context, oid);
                return "ECDSA (" + oidName + ")";
            }
            case PublicKeyAlgorithmTags.ECDH: {
                if (oid == null) {
                    return "ECDH";
                }
                String oidName = KeyFormattingUtils.getCurveInfo(context, oid);
                return "ECDH (" + oidName + ")";
            }

            default: {
                if (context != null) {
                    algorithmStr = context.getResources().getString(R.string.unknown);
                } else {
                    algorithmStr = "unknown";
                }
                break;
            }
        }
        if (keySize != null && keySize > 0)
            return algorithmStr + ", " + keySize + " bit";
        else
            return algorithmStr;
    }

    public static String getAlgorithmInfo(Algorithm algorithm, Integer keySize, Curve curve) {
        return getAlgorithmInfo(null, algorithm, keySize, curve);
    }

    /**
     * Based on <a href="http://tools.ietf.org/html/rfc2440#section-9.1">OpenPGP Message Format</a>
     */
    public static String getAlgorithmInfo(Context context, Algorithm algorithm, Integer keySize, Curve curve) {
        String algorithmStr;

        switch (algorithm) {
            case RSA: {
                algorithmStr = "RSA";
                break;
            }
            case DSA: {
                algorithmStr = "DSA";
                break;
            }

            case ELGAMAL: {
                algorithmStr = "ElGamal";
                break;
            }

            case ECDSA: {
                algorithmStr = "ECDSA";
                if (curve != null) {
                    algorithmStr += " (" + getCurveInfo(context, curve) + ")";
                }
                return algorithmStr;
            }
            case ECDH: {
                algorithmStr = "ECDH";
                if (curve != null) {
                    algorithmStr += " (" + getCurveInfo(context, curve) + ")";
                }
                return algorithmStr;
            }

            default: {
                if (context != null) {
                    algorithmStr = context.getResources().getString(R.string.unknown);
                } else {
                    algorithmStr = "unknown";
                }
                break;
            }
        }
        if (keySize != null && keySize > 0)
            return algorithmStr + ", " + keySize + " bit";
        else
            return algorithmStr;
    }

    /**
     * Return name of a curve. These are names, no need for translation
     */
    public static String getCurveInfo(Context context, Curve curve) {
        switch (curve) {
            case NIST_P256:
                return "NIST P-256";
            case NIST_P384:
                return "NIST P-384";
            case NIST_P521:
                return "NIST P-521";

            /* see SaveKeyringParcel
            case BRAINPOOL_P256:
                return "Brainpool P-256";
            case BRAINPOOL_P384:
                return "Brainpool P-384";
            case BRAINPOOL_P512:
                return "Brainpool P-512";
            */
        }
        if (context != null) {
            return context.getResources().getString(R.string.unknown);
        } else {
            return "unknown";
        }
    }

    public static String getCurveInfo(Context context, String oidStr) {
        ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier(oidStr);

        String name;
        name = NISTNamedCurves.getName(oid);
        if (name != null) {
            return name;
        }
        name = TeleTrusTNamedCurves.getName(oid);
        if (name != null) {
            return name;
        }
        if (context != null) {
            return context.getResources().getString(R.string.unknown);
        } else {
            return "unknown";
        }
    }

    /**
     * Converts fingerprint to hex
     * <p/>
     * Fingerprint is shown using lowercase characters. Studies have shown that humans can
     * better differentiate between numbers and letters when letters are lowercase.
     *
     * @param fingerprint
     * @return
     */
    public static String convertFingerprintToHex(byte[] fingerprint) {
        // NOTE: Even though v3 keys are not imported we need to support both fingerprints for
        // display/comparison before import
        if (fingerprint.length != 16 && fingerprint.length != 20) {
            throw new IllegalArgumentException("No valid v3 or v4 fingerprint!");
        }

        return Hex.toHexString(fingerprint).toLowerCase(Locale.ENGLISH);
    }

    public static long getKeyIdFromFingerprint(byte[] fingerprint) {
        ByteBuffer buf = ByteBuffer.wrap(fingerprint);
        // skip first 12 bytes of the fingerprint
        buf.position(12);
        // the last eight bytes are the key id (big endian, which is default order in ByteBuffer)
        return buf.getLong();
    }

    /**
     * Convert key id from long to 64 bit hex string
     * <p/>
     * V4: "The Key ID is the low-order 64 bits of the fingerprint"
     * <p/>
     * see http://tools.ietf.org/html/rfc4880#section-12.2
     *
     * @param keyId
     * @return
     */
    public static String convertKeyIdToHex(long keyId) {
        long upper = keyId >> 32;
        if (upper == 0) {
            // this is a short key id
            return convertKeyIdToHexShort(keyId);
        }
        return "0x" + convertKeyIdToHex32bit(keyId >> 32) + convertKeyIdToHex32bit(keyId);
    }

    public static String convertKeyIdToHexShort(long keyId) {
        return "0x" + convertKeyIdToHex32bit(keyId);
    }

    private static String convertKeyIdToHex32bit(long keyId) {
        String hexString = Long.toHexString(keyId & 0xffffffffL).toLowerCase(Locale.ENGLISH);
        while (hexString.length() < 8) {
            hexString = "0" + hexString;
        }
        return hexString;
    }

    public static long convertKeyIdHexToKeyId(String hex) {
        return new BigInteger(hex.substring(2), 16).longValue();
    }

    public static long convertFingerprintToKeyId(byte[] fingerprint) {
        return ByteBuffer.wrap(fingerprint, 12, 8).getLong();
    }

    /**
     * Makes a human-readable version of a key ID, which is usually 64 bits: lower-case, no
     * leading 0x, space-separated quartets (for keys whose length in hex is divisible by 4)
     *
     * @param idHex - the key id
     * @return - the beautified form
     */
    public static String beautifyKeyId(String idHex) {
        if (idHex.startsWith("0x")) {
            idHex = idHex.substring(2);
        }
        if ((idHex.length() % 4) == 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < idHex.length(); i += 4) {
                if (i != 0) {
                    sb.appendCodePoint(0x2008); // U+2008 PUNCTUATION SPACE
                }
                sb.append(idHex.substring(i, i + 4).toLowerCase(Locale.US));
            }
            idHex = sb.toString();
        }

        return idHex;
    }

    /**
     * Makes a human-readable version of a key ID, which is usually 64 bits: lower-case, no
     * leading 0x, space-separated quartets (for keys whose length in hex is divisible by 4)
     *
     * @param keyId - the key id
     * @return - the beautified form
     */
    public static String beautifyKeyId(long keyId) {
        return beautifyKeyId(convertKeyIdToHex(keyId));
    }

    public static String beautifyKeyIdWithPrefix(String idHex) {
        return "Key ID: " + beautifyKeyId(idHex);
    }

    public static String beautifyKeyIdWithPrefix(long keyId) {
        return beautifyKeyIdWithPrefix(convertKeyIdToHex(keyId));
    }

    public static SpannableStringBuilder colorizeFingerprint(String fingerprint) {
        // split by 4 characters
        fingerprint = fingerprint.replaceAll("(.{4})(?!$)", "$1 ");

        // add line breaks to have a consistent "image" that can be recognized
        char[] chars = fingerprint.toCharArray();
        chars[24] = '\n';
        fingerprint = String.valueOf(chars);

        SpannableStringBuilder sb = new SpannableStringBuilder(fingerprint);
        try {
            // for each 4 characters of the fingerprint + 1 space
            for (int i = 0; i < fingerprint.length(); i += 5) {
                int spanEnd = Math.min(i + 4, fingerprint.length());
                String fourChars = fingerprint.substring(i, spanEnd);

                int raw = Integer.parseInt(fourChars, 16);
                byte[] bytes = {(byte) ((raw >> 8) & 0xff - 128), (byte) (raw & 0xff - 128)};
                int[] color = getRgbForData(bytes);
                int r = color[0];
                int g = color[1];
                int b = color[2];

                // we cannot change black by multiplication, so adjust it to an almost-black grey,
                // which will then be brightened to the minimal brightness level
                if (r == 0 && g == 0 && b == 0) {
                    r = 1;
                    g = 1;
                    b = 1;
                }

                // Convert rgb to brightness
                double brightness = 0.2126 * r + 0.7152 * g + 0.0722 * b;

                // If a color is too dark to be seen on black,
                // then brighten it up to a minimal brightness.
                if (brightness < 80) {
                    double factor = 80.0 / brightness;
                    r = Math.min(255, (int) (r * factor));
                    g = Math.min(255, (int) (g * factor));
                    b = Math.min(255, (int) (b * factor));

                    // If it is too light, then darken it to a respective maximal brightness.
                } else if (brightness > 180) {
                    double factor = 180.0 / brightness;
                    r = (int) (r * factor);
                    g = (int) (g * factor);
                    b = (int) (b * factor);
                }

                // Create a foreground color with the 3 digest integers as RGB
                // and then converting that int to hex to use as a color
                sb.setSpan(new ForegroundColorSpan(Color.rgb(r, g, b)),
                        i, spanEnd, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Colorization failed", e);
            // if anything goes wrong, then just display the fingerprint without colour,
            // instead of partially correct colour or wrong colours
            return new SpannableStringBuilder(fingerprint);
        }

        return sb;
    }

    /**
     * Converts the given bytes to a unique RGB color using SHA1 algorithm
     *
     * @return an integer array containing 3 numeric color representations (Red, Green, Black)
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.security.DigestException
     */
    private static int[] getRgbForData(byte[] bytes) throws NoSuchAlgorithmException, DigestException {
        MessageDigest md = MessageDigest.getInstance("SHA1");

        md.update(bytes);
        byte[] digest = md.digest();

        return new int[]{((int) digest[0] + 256) % 256,
                ((int) digest[1] + 256) % 256,
                ((int) digest[2] + 256) % 256};
    }

    public static final int DEFAULT_COLOR = -1;

    public enum State {
        REVOKED,
        EXPIRED,
        VERIFIED,
        UNAVAILABLE,
        ENCRYPTED,
        NOT_ENCRYPTED,
        UNVERIFIED,
        UNKNOWN_KEY,
        INVALID,
        NOT_SIGNED,
        INSECURE
    }

    public static void setStatusImage(Context context, ImageView statusIcon, State state) {
        setStatusImage(context, statusIcon, null, state);
    }

    public static void setStatusImage(Context context, ImageView statusIcon, TextView statusText, State state) {
        setStatusImage(context, statusIcon, statusText, state, KeyFormattingUtils.DEFAULT_COLOR, false);
    }

    public static void setStatusImage(Context context, ImageView statusIcon, TextView statusText,
                                      State state, int color) {
        setStatusImage(context, statusIcon, statusText, state, color, false);
    }

    public interface StatusHolder {
        ImageView getEncryptionStatusIcon();

        TextView getEncryptionStatusText();

        ImageView getSignatureStatusIcon();

        TextView getSignatureStatusText();

        View getSignatureLayout();

        TextView getSignatureUserName();

        TextView getSignatureUserEmail();

        ViewAnimator getSignatureAction();

        boolean hasEncrypt();

    }

    @SuppressWarnings("deprecation") // context.getDrawable is api lvl 21, need to use deprecated
    public static void setStatus(Resources resources, StatusHolder holder, DecryptVerifyResult result,
                                 boolean processingkeyLookup) {

        if (holder.hasEncrypt()) {
            OpenPgpDecryptionResult decryptionResult = result.getDecryptionResult();

            int encText, encIcon, encColor;

            switch (decryptionResult.getResult()) {
                case OpenPgpDecryptionResult.RESULT_ENCRYPTED: {
                    encText = R.string.decrypt_result_encrypted;
                    encIcon = R.drawable.status_lock_closed_24dp;
                    encColor = R.color.key_flag_green;
                    break;
                }

                case OpenPgpDecryptionResult.RESULT_INSECURE: {
                    encText = R.string.decrypt_result_insecure;
                    encIcon = R.drawable.status_signature_invalid_cutout_24dp;
                    encColor = R.color.key_flag_red;
                    break;
                }

                default:
                case OpenPgpDecryptionResult.RESULT_NOT_ENCRYPTED: {
                    encText = R.string.decrypt_result_not_encrypted;
                    encIcon = R.drawable.status_lock_open_24dp;
                    encColor = R.color.key_flag_red;
                    break;
                }
            }

            int encColorRes = resources.getColor(encColor);
            holder.getEncryptionStatusIcon().setColorFilter(encColorRes, PorterDuff.Mode.SRC_IN);
            holder.getEncryptionStatusIcon().setImageDrawable(resources.getDrawable(encIcon));
            holder.getEncryptionStatusText().setText(encText);
            holder.getEncryptionStatusText().setTextColor(encColorRes);
        }

        OpenPgpSignatureResult signatureResult = result.getSignatureResult();

        int sigText, sigIcon, sigColor;
        int sigActionDisplayedChild;

        switch (signatureResult.getResult()) {

            case OpenPgpSignatureResult.RESULT_NO_SIGNATURE: {
                // no signature

                sigText = R.string.decrypt_result_no_signature;
                sigIcon = R.drawable.status_signature_invalid_cutout_24dp;
                sigColor = R.color.key_flag_gray;

                // won't be used, but makes compiler happy
                sigActionDisplayedChild = -1;
                break;
            }

            case OpenPgpSignatureResult.RESULT_VALID_CONFIRMED: {
                sigText = R.string.decrypt_result_signature_certified;
                sigIcon = R.drawable.status_signature_verified_cutout_24dp;
                sigColor = R.color.key_flag_green;

                sigActionDisplayedChild = 0;
                break;
            }

            case OpenPgpSignatureResult.RESULT_VALID_UNCONFIRMED: {
                sigText = R.string.decrypt_result_signature_uncertified;
                sigIcon = R.drawable.status_signature_unverified_cutout_24dp;
                sigColor = R.color.key_flag_orange;

                sigActionDisplayedChild = 0;
                break;
            }

            case OpenPgpSignatureResult.RESULT_INVALID_KEY_REVOKED: {
                sigText = R.string.decrypt_result_signature_revoked_key;
                sigIcon = R.drawable.status_signature_revoked_cutout_24dp;
                sigColor = R.color.key_flag_red;

                sigActionDisplayedChild = 0;
                break;
            }

            case OpenPgpSignatureResult.RESULT_INVALID_KEY_EXPIRED: {
                sigText = R.string.decrypt_result_signature_expired_key;
                sigIcon = R.drawable.status_signature_expired_cutout_24dp;
                sigColor = R.color.key_flag_red;

                sigActionDisplayedChild = 0;
                break;
            }

            case OpenPgpSignatureResult.RESULT_KEY_MISSING: {
                sigText = R.string.decrypt_result_signature_missing_key;
                sigIcon = R.drawable.status_signature_unknown_cutout_24dp;
                sigColor = R.color.key_flag_red;

                sigActionDisplayedChild = 1;
                break;
            }

            case OpenPgpSignatureResult.RESULT_INVALID_INSECURE: {
                sigText = R.string.decrypt_result_insecure_cryptography;
                sigIcon = R.drawable.status_signature_invalid_cutout_24dp;
                sigColor = R.color.key_flag_red;

                sigActionDisplayedChild = 0;
                break;
            }

            default:
            case OpenPgpSignatureResult.RESULT_INVALID_SIGNATURE: {
                sigText = R.string.decrypt_result_invalid_signature;
                sigIcon = R.drawable.status_signature_invalid_cutout_24dp;
                sigColor = R.color.key_flag_red;

                // won't be used, but makes compiler happy
                sigActionDisplayedChild = -1;
                break;
            }

        }

        // possibly switch out "Lookup" button for progress bar
        if (sigActionDisplayedChild == 1 && processingkeyLookup) {
            sigActionDisplayedChild = 2;
        }

        int sigColorRes = resources.getColor(sigColor);
        holder.getSignatureStatusIcon().setColorFilter(sigColorRes, PorterDuff.Mode.SRC_IN);
        holder.getSignatureStatusIcon().setImageDrawable(resources.getDrawable(sigIcon));
        holder.getSignatureStatusText().setText(sigText);
        holder.getSignatureStatusText().setTextColor(sigColorRes);

        if (signatureResult.getResult() != OpenPgpSignatureResult.RESULT_NO_SIGNATURE
                && signatureResult.getResult() != OpenPgpSignatureResult.RESULT_INVALID_SIGNATURE) {
            // has a signature, thus display layouts

            holder.getSignatureLayout().setVisibility(View.VISIBLE);

            holder.getSignatureAction().setDisplayedChild(sigActionDisplayedChild);

            String userId = result.getSignatureResult().getPrimaryUserId();
            OpenPgpUtils.UserId userIdSplit = KeyRing.splitUserId(userId);
            if (userIdSplit.name != null) {
                holder.getSignatureUserName().setText(userIdSplit.name);
            } else {
                holder.getSignatureUserName().setText(R.string.user_id_no_name);
            }
            if (userIdSplit.email != null) {
                holder.getSignatureUserEmail().setVisibility(View.VISIBLE);
                holder.getSignatureUserEmail().setText(userIdSplit.email);
            } else {
                holder.getSignatureUserEmail().setVisibility(View.GONE);
            }

        } else {

            holder.getSignatureLayout().setVisibility(View.GONE);

        }


    }

    /**
     * Sets status image based on constant
     */
    @SuppressWarnings("deprecation") // context.getDrawable is api lvl 21
    public static void setStatusImage(Context context, ImageView statusIcon, TextView statusText,
                                      State state, int color, boolean big) {
        switch (state) {
            /** GREEN: everything is good **/
            case VERIFIED: {
                if (big) {
                    statusIcon.setImageDrawable(
                            context.getResources().getDrawable(R.drawable.status_signature_verified_cutout_96dp));
                } else {
                    statusIcon.setImageDrawable(
                            context.getResources().getDrawable(R.drawable.status_signature_verified_cutout_24dp));
                }
                if (color == KeyFormattingUtils.DEFAULT_COLOR) {
                    color = R.color.key_flag_green;
                }
                statusIcon.setColorFilter(context.getResources().getColor(color),
                        PorterDuff.Mode.SRC_IN);
                if (statusText != null) {
                    statusText.setTextColor(context.getResources().getColor(color));
                }
                break;
            }
            case ENCRYPTED: {
                statusIcon.setImageDrawable(
                        context.getResources().getDrawable(R.drawable.status_lock_closed_24dp));
                if (color == KeyFormattingUtils.DEFAULT_COLOR) {
                    color = R.color.key_flag_green;
                }
                statusIcon.setColorFilter(context.getResources().getColor(color),
                        PorterDuff.Mode.SRC_IN);
                if (statusText != null) {
                    statusText.setTextColor(context.getResources().getColor(color));
                }
                break;
            }
            /** ORANGE: mostly bad... **/
            case UNVERIFIED: {
                if (big) {
                    statusIcon.setImageDrawable(
                            context.getResources().getDrawable(R.drawable.status_signature_unverified_cutout_96dp));
                } else {
                    statusIcon.setImageDrawable(
                            context.getResources().getDrawable(R.drawable.status_signature_unverified_cutout_24dp));
                }
                if (color == KeyFormattingUtils.DEFAULT_COLOR) {
                    color = R.color.key_flag_orange;
                }
                statusIcon.setColorFilter(context.getResources().getColor(color),
                        PorterDuff.Mode.SRC_IN);
                if (statusText != null) {
                    statusText.setTextColor(context.getResources().getColor(color));
                }
                break;
            }
            case UNKNOWN_KEY: {
                statusIcon.setImageDrawable(
                        context.getResources().getDrawable(R.drawable.status_signature_unknown_cutout_24dp));
                if (color == KeyFormattingUtils.DEFAULT_COLOR) {
                    color = R.color.key_flag_red;
                }
                statusIcon.setColorFilter(context.getResources().getColor(color),
                        PorterDuff.Mode.SRC_IN);
                if (statusText != null) {
                    statusText.setTextColor(context.getResources().getColor(color));
                }
                break;
            }
            /** RED: really bad... **/
            case REVOKED: {
                if (big) {
                    statusIcon.setImageDrawable(
                            context.getResources().getDrawable(R.drawable.status_signature_revoked_cutout_96dp));
                } else {
                    statusIcon.setImageDrawable(
                            context.getResources().getDrawable(R.drawable.status_signature_revoked_cutout_24dp));
                }
                if (color == KeyFormattingUtils.DEFAULT_COLOR) {
                    color = R.color.key_flag_red;
                }
                statusIcon.setColorFilter(context.getResources().getColor(color),
                        PorterDuff.Mode.SRC_IN);
                if (statusText != null) {
                    statusText.setTextColor(context.getResources().getColor(color));
                }
                break;
            }
            case EXPIRED: {
                if (big) {
                    statusIcon.setImageDrawable(
                            context.getResources().getDrawable(R.drawable.status_signature_expired_cutout_96dp));
                } else {
                    statusIcon.setImageDrawable(
                            context.getResources().getDrawable(R.drawable.status_signature_expired_cutout_24dp));
                }
                if (color == KeyFormattingUtils.DEFAULT_COLOR) {
                    color = R.color.key_flag_red;
                }
                statusIcon.setColorFilter(context.getResources().getColor(color),
                        PorterDuff.Mode.SRC_IN);
                if (statusText != null) {
                    statusText.setTextColor(context.getResources().getColor(color));
                }
                break;
            }
            case INSECURE: {
                if (big) {
                    statusIcon.setImageDrawable(
                            context.getResources().getDrawable(R.drawable.status_signature_invalid_cutout_96dp));
                } else {
                    statusIcon.setImageDrawable(
                            context.getResources().getDrawable(R.drawable.status_signature_invalid_cutout_24dp));
                }
                if (color == KeyFormattingUtils.DEFAULT_COLOR) {
                    color = R.color.key_flag_red;
                }
                statusIcon.setColorFilter(context.getResources().getColor(color),
                        PorterDuff.Mode.SRC_IN);
                if (statusText != null) {
                    statusText.setTextColor(context.getResources().getColor(color));
                }
                break;
            }
            case NOT_ENCRYPTED: {
                statusIcon.setImageDrawable(
                        context.getResources().getDrawable(R.drawable.status_lock_open_24dp));
                if (color == KeyFormattingUtils.DEFAULT_COLOR) {
                    color = R.color.key_flag_red;
                }
                statusIcon.setColorFilter(context.getResources().getColor(color),
                        PorterDuff.Mode.SRC_IN);
                if (statusText != null) {
                    statusText.setTextColor(context.getResources().getColor(color));
                }
                break;
            }
            case NOT_SIGNED: {
                statusIcon.setImageDrawable(
                        context.getResources().getDrawable(R.drawable.status_signature_unknown_cutout_24dp));
                if (color == KeyFormattingUtils.DEFAULT_COLOR) {
                    color = R.color.key_flag_red;
                }
                statusIcon.setColorFilter(context.getResources().getColor(color),
                        PorterDuff.Mode.SRC_IN);
                if (statusText != null) {
                    statusText.setTextColor(context.getResources().getColor(color));
                }
                break;
            }
            case INVALID: {
                statusIcon.setImageDrawable(
                        context.getResources().getDrawable(R.drawable.status_signature_invalid_cutout_24dp));
                if (color == KeyFormattingUtils.DEFAULT_COLOR) {
                    color = R.color.key_flag_red;
                }
                statusIcon.setColorFilter(context.getResources().getColor(color),
                        PorterDuff.Mode.SRC_IN);
                if (statusText != null) {
                    statusText.setTextColor(context.getResources().getColor(color));
                }
                break;
            }
            /** special **/
            case UNAVAILABLE: {
                statusIcon.setImageDrawable(
                        context.getResources().getDrawable(R.drawable.status_signature_invalid_cutout_24dp));
                if (color == KeyFormattingUtils.DEFAULT_COLOR) {
                    color = R.color.key_flag_gray;
                }
                statusIcon.setColorFilter(context.getResources().getColor(color),
                        PorterDuff.Mode.SRC_IN);
                if (statusText != null) {
                    statusText.setTextColor(context.getResources().getColor(color));
                }
                break;
            }
        }
    }

}
