/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.telecomm;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.MissingResourceException;

/**
 * Provides user interface description information for a {@code PhoneAccount}.
 */
public class PhoneAccount implements Parcelable {

    /**
     * Flag indicating that this {@code PhoneAccount} can act as a call manager for
     * traditional SIM-based telephony calls. The {@link ConnectionService} associated with this
     * phone-account will be allowed to manage SIM-based phone calls including using its own
     * proprietary phone-call implementation (like VoIP calling) to make calls instead of the
     * telephony stack.
     * When a user opts to place a call using the SIM-based telephony stack, the connection-service
     * associated with this phone-account will be attempted first if the user has explicitly
     * selected it to be used as the default call-manager.
     * <p>
     * See {@link #getCapabilities}
     */
    public static final int CAPABILITY_SIM_CALL_MANAGER = 0x1;

    /**
     * Flag indicating that this {@code PhoneAccount} can make phone calls in place of
     * traditional SIM-based telephony calls. This account will be treated as a distinct method
     * for placing calls alongside the traditional SIM-based telephony stack. This flag is
     * distinct from {@link #CAPABILITY_SIM_CALL_MANAGER} in that it is not allowed to manage
     * calls from or use the built-in telephony stack to place its calls.
     * <p>
     * See {@link #getCapabilities}
     *
     * {@hide}
     */
    public static final int CAPABILITY_CALL_PROVIDER = 0x2;

    /**
     * Flag indicating that this {@code PhoneAccount} represents  built-in PSTN SIM
     * subscription.
     * <p>
     * Only the android framework can set this capability on a phone account.
     */
    public static final int CAPABILITY_SIM_SUBSCRIPTION = 0x4;

    private final PhoneAccountHandle mAccountHandle;
    private final Uri mHandle;
    private final String mSubscriptionNumber;
    private final int mCapabilities;
    private final int mIconResId;
    private final CharSequence mLabel;
    private final CharSequence mShortDescription;
    private boolean mVideoCallingSupported;

    public PhoneAccount(
            PhoneAccountHandle account,
            Uri handle,
            String subscriptionNumber,
            int capabilities,
            int iconResId,
            CharSequence label,
            CharSequence shortDescription,
            boolean supportsVideoCalling) {
        mAccountHandle = account;
        mHandle = handle;
        mSubscriptionNumber = subscriptionNumber;
        mCapabilities = capabilities;
        mIconResId = iconResId;
        mLabel = label;
        mShortDescription = shortDescription;
        mVideoCallingSupported = supportsVideoCalling;
    }

    /**
     * The {@code PhoneAccountHandle} to which this metadata pertains.
     *
     * @return A {@code PhoneAccountHandle}.
     */
    public PhoneAccountHandle getAccountHandle() {
        return mAccountHandle;
    }

    /**
     * The handle (e.g., a phone number) associated with this {@code PhoneAccount}. This
     * represents the destination from which outgoing calls using this {@code PhoneAccount}
     * will appear to come, if applicable, and the destination to which incoming calls using this
     * {@code PhoneAccount} may be addressed.
     *
     * @return A handle expressed as a {@code Uri}, for example, a phone number.
     */
    public Uri getHandle() {
        return mHandle;
    }

    /**
     * The subscription number associated with the underlying transport. This may differ from the
     * {@link #getHandle()} number; for example, if the number used to talk to the network is not
     * the same number that will be on the remote party's caller ID display.
     *
     * @return The subscription number, suitable for display to the user.
     */
    public String getSubscriptionNumber() {
        return mSubscriptionNumber;
    }

    /**
     * The capabilities of this {@code PhoneAccount}.
     *
     * @return A bit field of flags describing this {@code PhoneAccount}'s capabilities.
     */
    public int getCapabilities() {
        return mCapabilities;
    }

    /**
     * A short label describing a {@code PhoneAccount}.
     *
     * @return A label for this {@code PhoneAccount}.
     */
    public CharSequence getLabel() {
        return mLabel;
    }

    /**
     * A short paragraph describing a {@code PhoneAccount}.
     *
     * @return A description for this {@code PhoneAccount}.
     */
    public CharSequence getShortDescription() {
        return mShortDescription;
    }

    /**
     * The icon resource ID for the icon of this {@code PhoneAccount}.
     *
     * @return A resource ID.
     */
    public int getIconResId() {
        return mIconResId;
    }

    /**
     * An icon to represent this {@code PhoneAccount} in a user interface.
     *
     * @return An icon for this {@code PhoneAccount}.
     */
    public Drawable getIcon(Context context) {
        return getIcon(context, mIconResId);
    }

    private Drawable getIcon(Context context, int resId) {
        Context packageContext;
        try {
            packageContext = context.createPackageContext(
                    mAccountHandle.getComponentName().getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(this, "Cannot find package %s", mAccountHandle.getComponentName().getPackageName());
            return null;
        }
        try {
            return packageContext.getResources().getDrawable(resId);
        } catch (NotFoundException|MissingResourceException e) {
            Log.e(this, e, "Cannot find icon %d in package %s",
                    resId, mAccountHandle.getComponentName().getPackageName());
            return null;
        }
    }

    /**
     * Determines whether this {@code PhoneAccount} supports video calling.
     *
     * @return {@code true} if this {@code PhoneAccount} supports video calling.
     */
    public boolean isVideoCallingSupported() {
        return mVideoCallingSupported;
    }

    //
    // Parcelable implementation
    //

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mAccountHandle, 0);
        out.writeParcelable(mHandle, 0);
        out.writeString(mSubscriptionNumber);
        out.writeInt(mCapabilities);
        out.writeInt(mIconResId);
        out.writeCharSequence(mLabel);
        out.writeCharSequence(mShortDescription);
        out.writeInt(mVideoCallingSupported ? 1 : 0);
    }

    public static final Creator<PhoneAccount> CREATOR
            = new Creator<PhoneAccount>() {
        @Override
        public PhoneAccount createFromParcel(Parcel in) {
            return new PhoneAccount(in);
        }

        @Override
        public PhoneAccount[] newArray(int size) {
            return new PhoneAccount[size];
        }
    };

    private PhoneAccount(Parcel in) {
        mAccountHandle = in.readParcelable(getClass().getClassLoader());
        mHandle = in.readParcelable(getClass().getClassLoader());
        mSubscriptionNumber = in.readString();
        mCapabilities = in.readInt();
        mIconResId = in.readInt();
        mLabel = in.readCharSequence();
        mShortDescription = in.readCharSequence();
        mVideoCallingSupported = in.readInt() == 1;
    }
}