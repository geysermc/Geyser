/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.session.auth;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import org.geysermc.floodgate.util.DeviceOs;
import org.geysermc.floodgate.util.InputMode;
import org.geysermc.floodgate.util.UiProfile;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Getter
public final class BedrockClientData {
    @SerializedName(value = "GameVersion")
    private String gameVersion;
    @SerializedName(value = "ServerAddress")
    private String serverAddress;
    @SerializedName(value = "ThirdPartyName")
    private String username;
    @SerializedName(value = "LanguageCode")
    private String languageCode;

    @SerializedName(value = "SkinId")
    private String skinId;
    @SerializedName(value = "SkinData")
    private String skinData;
    @SerializedName(value = "SkinImageHeight")
    private int skinImageHeight;
    @SerializedName(value = "SkinImageWidth")
    private int skinImageWidth;
    @SerializedName(value = "CapeId")
    private String capeId;
    @SerializedName(value = "CapeData")
    @JsonAdapter(value = StringToByteDeserializer.class)
    private byte[] capeData;
    @SerializedName(value = "CapeImageHeight")
    private int capeImageHeight;
    @SerializedName(value = "CapeImageWidth")
    private int capeImageWidth;
    @SerializedName(value = "CapeOnClassicSkin")
    private boolean capeOnClassicSkin;
    @SerializedName(value = "SkinResourcePatch")
    private String geometryName;
    @SerializedName(value = "SkinGeometryData")
    private String geometryData;
    @SerializedName(value = "PersonaSkin")
    private boolean personaSkin;
    @SerializedName(value = "PremiumSkin")
    private boolean premiumSkin;

    @SerializedName(value = "DeviceId")
    private String deviceId;
    @SerializedName(value = "DeviceModel")
    private String deviceModel;
    @SerializedName(value = "DeviceOS")
    private DeviceOs deviceOs;
    @SerializedName(value = "UIProfile")
    private UiProfile uiProfile;
    @SerializedName(value = "GuiScale")
    private int guiScale;
    @SerializedName(value = "CurrentInputMode")
    private InputMode currentInputMode;
    @SerializedName(value = "DefaultInputMode")
    private InputMode defaultInputMode;
    @SerializedName("PlatformOnlineId")
    private String platformOnlineId;
    @SerializedName(value = "PlatformOfflineId")
    private String platformOfflineId;
    @SerializedName(value = "SelfSignedId")
    private UUID selfSignedId;
    @SerializedName(value = "ClientRandomId")
    private long clientRandomId;

    @SerializedName(value = "ArmSize")
    private String armSize;
    @SerializedName(value = "SkinAnimationData")
    private String skinAnimationData;
    @SerializedName(value = "SkinColor")
    private String skinColor;
    @SerializedName(value = "ThirdPartyNameOnly")
    private boolean thirdPartyNameOnly;
    @SerializedName(value = "PlayFabId")
    private String playFabId;

    @Setter
    private transient String originalString = null;

    public DeviceOs getDeviceOs() {
        return deviceOs != null ? deviceOs : DeviceOs.UNKNOWN;
    }

    public InputMode getCurrentInputMode() {
        return currentInputMode != null ? currentInputMode : InputMode.UNKNOWN;
    }

    public InputMode getDefaultInputMode() {
        return defaultInputMode != null ? defaultInputMode : InputMode.UNKNOWN;
    }

    public UiProfile getUiProfile() {
        return uiProfile != null ? uiProfile : UiProfile.CLASSIC;
    }

    private static final class StringToByteDeserializer implements JsonDeserializer<byte[]> {
        @Override
        public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return json.getAsString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
