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

package org.geysermc.geyser.util;

import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult.IdentityData;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.cumulus.response.result.FormResponseResult;
import org.geysermc.cumulus.response.result.ValidFormResponseResult;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.AuthData;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.geysermc.geyser.text.ChatColor;
import org.geysermc.geyser.text.GeyserLocale;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import java.util.function.BiConsumer;

public class LoginEncryptionUtils {
    private static boolean HAS_SENT_ENCRYPTION_MESSAGE = false;

    public static void encryptPlayerConnection(GeyserSession session, LoginPacket loginPacket) {
        encryptConnectionWithCert(session, loginPacket.getExtra(), loginPacket.getChain());
    }

    private static void encryptConnectionWithCert(GeyserSession session, String clientData, List<String> certChainData) {
        try {
            GeyserImpl geyser = session.getGeyser();

            ChainValidationResult result = EncryptionUtils.validateChain(certChainData);

            geyser.getLogger().debug(String.format("Is player data signed? %s", result.signed()));

            if (!result.signed() && !session.getGeyser().config().enableProxyConnections()) {
                session.disconnect(GeyserLocale.getLocaleStringLog("geyser.network.remote.invalid_xbox_account"));
                return;
            }

            IdentityData extraData = result.identityClaims().extraData;
            session.setAuthenticationData(new AuthData(extraData.displayName, extraData.identity, extraData.xuid));
            session.setCertChainData(certChainData);

            PublicKey identityPublicKey = result.identityClaims().parsedIdentityPublicKey();

            byte[] clientDataPayload = EncryptionUtils.verifyClientData(clientData, identityPublicKey);
            if (clientDataPayload == null) {
                throw new IllegalStateException("Client data isn't signed by the given chain data");
            }

            BedrockClientData data = JsonUtils.fromJson(clientDataPayload, BedrockClientData.class);
            data.setOriginalString(clientData);
            session.setClientData(data);

            try {
                startEncryptionHandshake(session, identityPublicKey);
            } catch (Throwable e) {
                // An error can be thrown on older Java 8 versions about an invalid key
                if (geyser.config().debugMode()) {
                    e.printStackTrace();
                }

                sendEncryptionFailedMessage(geyser);
            }
        } catch (Exception ex) {
            session.disconnect("disconnectionScreen.internalError.cantConnect");
            throw new RuntimeException("Unable to complete login", ex);
        }
    }

    private static void startEncryptionHandshake(GeyserSession session, PublicKey key) throws Exception {
        KeyPair serverKeyPair = EncryptionUtils.createKeyPair();
        byte[] token = EncryptionUtils.generateRandomToken();

        ServerToClientHandshakePacket packet = new ServerToClientHandshakePacket();
        packet.setJwt(EncryptionUtils.createHandshakeJwt(serverKeyPair, token));
        session.sendUpstreamPacketImmediately(packet);

        SecretKey encryptionKey = EncryptionUtils.getSecretKey(serverKeyPair.getPrivate(), key, token);
        session.getUpstream().getSession().enableEncryption(encryptionKey);
    }

    private static void sendEncryptionFailedMessage(GeyserImpl geyser) {
        if (!HAS_SENT_ENCRYPTION_MESSAGE) {
            geyser.getLogger().warning(GeyserLocale.getLocaleStringLog("geyser.network.encryption.line_1"));
            geyser.getLogger().warning(GeyserLocale.getLocaleStringLog("geyser.network.encryption.line_2", "https://geysermc.org/supported_java"));
            HAS_SENT_ENCRYPTION_MESSAGE = true;
        }
    }

    public static void buildAndShowLoginWindow(GeyserSession session) {
        if (session.isLoggedIn()) {
            // Can happen if a window is cancelled during dimension switch
            return;
        }

        // Set DoDaylightCycle to false so the time doesn't accelerate while we're here
        session.setDaylightCycle(false);

        session.sendForm(
                SimpleForm.builder()
                        .translator(GeyserLocale::getPlayerLocaleString, session.locale())
                        .title("geyser.auth.login.form.notice.title")
                        .content("geyser.auth.login.form.notice.desc")
                        .button("geyser.auth.login.form.notice.btn_login.microsoft")
                        .button("geyser.auth.login.form.notice.btn_disconnect")
                        .closedOrInvalidResultHandler(() -> buildAndShowLoginWindow(session))
                        .validResultHandler((response) -> {
                            if (response.clickedButtonId() == 0) {
                                session.authenticateWithMicrosoftCode();
                                return;
                            }

                            session.disconnect(GeyserLocale.getPlayerLocaleString("geyser.auth.login.form.disconnect", session.locale()));
                        }));
    }

    /**
     * Build a window that explains the user's credentials will be saved to the system.
     */
    public static void buildAndShowConsentWindow(GeyserSession session) {
        String locale = session.locale();

        session.sendForm(
                SimpleForm.builder()
                        .translator(LoginEncryptionUtils::translate, locale)
                        .title("%gui.signIn")
                        .content("""
                                geyser.auth.login.save_token.warning

                                geyser.auth.login.save_token.proceed""")
                        .button("%gui.ok")
                        .button("%gui.decline")
                        .resultHandler(authenticateOrKickHandler(session))
        );
    }

    public static void buildAndShowTokenExpiredWindow(GeyserSession session) {
        String locale = session.locale();

        session.sendForm(
                SimpleForm.builder()
                        .translator(LoginEncryptionUtils::translate, locale)
                        .title("geyser.auth.login.form.expired")
                        .content("""
                                geyser.auth.login.save_token.expired

                                geyser.auth.login.save_token.proceed""")
                        .button("%gui.ok")
                        .resultHandler(authenticateOrKickHandler(session))
        );
    }

    private static BiConsumer<SimpleForm, FormResponseResult<SimpleFormResponse>> authenticateOrKickHandler(GeyserSession session) {
        return (form, genericResult) -> {
            if (genericResult instanceof ValidFormResponseResult<SimpleFormResponse> result &&
                    result.response().clickedButtonId() == 0) {
                session.authenticateWithMicrosoftCode(true);
            } else {
                session.disconnect("%disconnect.quitting");
            }
        };
    }

    /**
     * Shows the code that a user must input into their browser
     */
    public static void buildAndShowMicrosoftCodeWindow(GeyserSession session, StepMsaDeviceCode.MsaDeviceCode msCode) {
        String locale = session.locale();

        StringBuilder message = new StringBuilder("%xbox.signin.website\n")
                .append(ChatColor.AQUA)
                .append("%xbox.signin.url")
                .append(ChatColor.RESET)
                .append("\n%xbox.signin.enterCode\n")
                .append(ChatColor.GREEN)
                .append(msCode.getUserCode());
        int timeout = session.getGeyser().config().pendingAuthenticationTimeout();
        if (timeout != 0) {
            message.append("\n\n")
                    .append(ChatColor.RESET)
                    .append(GeyserLocale.getPlayerLocaleString("geyser.auth.login.timeout", session.locale(), String.valueOf(timeout)));
        }

        session.sendForm(
                ModalForm.builder()
                        .title("%xbox.signin")
                        .content(message.toString())
                        .button1("%gui.done")
                        .button2("%menu.disconnect")
                        .closedOrInvalidResultHandler(() -> buildAndShowLoginWindow(session))
                        .validResultHandler((response) -> {
                            if (response.clickedButtonId() == 1) {
                                session.disconnect(GeyserLocale.getPlayerLocaleString("geyser.auth.login.form.disconnect", locale));
                            }
                        })
        );
    }

    /*
    This checks per line if there is something to be translated, and it skips Bedrock translation keys (%)
     */
    private static String translate(String key, String locale) {
        StringBuilder newValue = new StringBuilder();
        int previousIndex = 0;
        while (previousIndex < key.length()) {
            int nextIndex = key.indexOf('\n', previousIndex);
            int endIndex = nextIndex == -1 ? key.length() : nextIndex;

            // if there is more to this line than just a new line char
            if (endIndex - previousIndex > 1) {
                String substring = key.substring(previousIndex, endIndex);
                if (key.charAt(previousIndex) != '%') {
                    newValue.append(GeyserLocale.getPlayerLocaleString(substring, locale));
                } else {
                    newValue.append(substring);
                }
            }
            newValue.append('\n');

            previousIndex = endIndex + 1;
        }
        return newValue.toString();
    }
}
