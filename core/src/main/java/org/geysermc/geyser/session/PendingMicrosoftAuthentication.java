/*
 * Copyright (c) 2019-2021 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.session;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode;
import net.raphimc.minecraftauth.util.MicrosoftConstants;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.util.MinecraftAuthLogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Pending Microsoft authentication task cache.
 * It permits user to exit the server while they authorize Geyser to access their Microsoft account.
 */
public class PendingMicrosoftAuthentication {
    public static final HttpClient AUTH_CLIENT = MinecraftAuth.createHttpClient();
    public static final BiFunction<Boolean, Integer, StepFullJavaSession> AUTH_FLOW = (offlineAccess, timeoutSec) -> MinecraftAuth.builder()
            .withClientId(GeyserImpl.OAUTH_CLIENT_ID)
            .withScope(offlineAccess ? "XboxLive.signin XboxLive.offline_access" : "XboxLive.signin")
            .withTimeout(timeoutSec)
            .deviceCode()
            .withoutDeviceToken()
            .regularAuthentication(MicrosoftConstants.JAVA_XSTS_RELYING_PARTY)
            .buildMinecraftJavaProfileStep(false);
    /**
     * For GeyserConnect usage.
     */
    private boolean storeServerInformation = false;
    private final LoadingCache<String, AuthenticationTask> authentications;

    public PendingMicrosoftAuthentication(int timeoutSeconds) {
        this.authentications = CacheBuilder.newBuilder()
                .build(new CacheLoader<>() {
                    @Override
                    public AuthenticationTask load(@NonNull String userKey) {
                        return storeServerInformation ? new ProxyAuthenticationTask(userKey, timeoutSeconds)
                                : new AuthenticationTask(userKey, timeoutSeconds);
                    }
                });
    }

    public AuthenticationTask getTask(@NonNull String userKey) {
        return authentications.getIfPresent(userKey);
    }

    @SneakyThrows(ExecutionException.class)
    public AuthenticationTask getOrCreateTask(@NonNull String userKey) {
        return authentications.get(userKey);
    }

    @SuppressWarnings("unused") // GeyserConnect
    public void setStoreServerInformation() {
        storeServerInformation = true;
    }

    public class AuthenticationTask {
        private static final Executor DELAYED_BY_ONE_SECOND = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS);

        private final String userKey;
        private final int timeoutSec;
        @Getter
        private CompletableFuture<StepChainResult> authentication;

        private AuthenticationTask(String userKey, int timeoutSec) {
            this.userKey = userKey;
            this.timeoutSec = timeoutSec;
        }

        public void resetRunningFlow() {
            if (authentication == null) {
                return;
            }

            // Interrupt the current flow
            this.authentication.cancel(true);
        }

        public void cleanup() {
            GeyserLogger logger = GeyserLogger.get();
            if (logger.isDebug()) {
                logger.debug("Cleaning up authentication task for " + userKey);
            }
            authentications.invalidate(userKey);
        }

        public CompletableFuture<StepChainResult> performLoginAttempt(boolean offlineAccess, Consumer<StepMsaDeviceCode.MsaDeviceCode> deviceCodeConsumer) {
            return authentication = CompletableFuture.supplyAsync(() -> {
                try {
                    StepFullJavaSession step = AUTH_FLOW.apply(offlineAccess, timeoutSec);
                    return new StepChainResult(step, step.getFromInput(MinecraftAuthLogger.INSTANCE, AUTH_CLIENT, new StepMsaDeviceCode.MsaDeviceCodeCallback(deviceCodeConsumer)));
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, DELAYED_BY_ONE_SECOND).whenComplete((r, ex) -> {
                // avoid memory leak, in case player doesn't connect again
                CompletableFuture.delayedExecutor(timeoutSec, TimeUnit.SECONDS).execute(this::cleanup);
            });
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{userKey='" + userKey + "'}";
        }
    }

    @Getter
    @Setter
    public final class ProxyAuthenticationTask extends AuthenticationTask {
        private String server;
        private int port;

        private ProxyAuthenticationTask(String userKey, int timeoutSec) {
            super(userKey, timeoutSec);
        }
    }

    public record StepChainResult(StepFullJavaSession step, StepFullJavaSession.FullJavaSession session) {
    }
}
