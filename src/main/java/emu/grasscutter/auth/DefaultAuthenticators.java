package emu.grasscutter.auth;

import static emu.grasscutter.config.Configuration.GAME;
import static emu.grasscutter.utils.lang.Language.translate;

import at.favre.lib.crypto.bcrypt.BCrypt;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.auth.AuthenticationSystem.AuthenticationRequest;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.server.dispatch.*;
import emu.grasscutter.server.http.objects.*;
import emu.grasscutter.utils.*;
import io.javalin.http.ContentType;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.*;
import javax.crypto.Cipher;

/** A class containing default authenticators. */
public final class DefaultAuthenticators {
    public static String buildBanMessage(Account account) {
        if (account == null) return "This account is banned.";
        int endTime = account.getBanEndTime();
        String timeStr;
        if (endTime <= 0) {
            timeStr = "permanent";
        } else {
            timeStr =
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .format(new java.util.Date(((long) endTime) * 1000L));
        }
        String reason = account.getBanReason();
        if (reason == null || reason.trim().isEmpty()) reason = "violation of the game rules";
        return String.format("This account is banned.\nUnban time: %s\nReason: %s", timeStr, reason);
    }

    private static String plainPassword(LoginAccountRequestJson data, boolean integrationPassword) {
        String password = data.password == null ? "" : data.password;
        if (integrationPassword || !data.is_crypto || password.isEmpty()) return password;
        try {
            return RSADecryptionUtil.decrypt(password);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean verifyPassword(Account account, String password) {
        try {
            return account.verifyPassword(password);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static class PasswordAuthenticator implements Authenticator<LoginResultJson> {
        @Override
        public LoginResultJson authenticate(AuthenticationRequest request) {
            var response = new LoginResultJson();
            var requestData = request.getPasswordRequest();
            assert requestData != null;
            boolean useIntegrationPassword = GAME.account.useIntegrationPassword;
            if (useIntegrationPassword) requestData.parse();

            boolean successfulLogin = false;
            String address = Utils.address(request.getContext());
            String responseMessage = translate("messages.dispatch.account.username_error");
            String loggerMessage = "";

            if (requestData.account == null || requestData.account.isBlank()) {
                response.retcode = -201;
                response.message =
                        useIntegrationPassword
                                ? "Enter your account and password in the username box as account&&password."
                                : responseMessage;
                Grasscutter.getLogger().info("[Dispatch] Client {} sent no usable account name.", address);
                return response;
            }

            if (useIntegrationPassword
                    && (requestData.password == null || requestData.password.isEmpty())) {
                response.retcode = -201;
                response.message = "The password half of account&&password is empty.";
                Grasscutter.getLogger().info("[Dispatch] Client {} sent an empty password half.", address);
                return response;
            }

            Account account = DatabaseHelper.getAccountByName(requestData.account);
            if (account == null && GAME.account.autoCreate) {
                account =
                        useIntegrationPassword
                                ? DatabaseHelper.createAccountWithHashedPassword(
                                        requestData.account, requestData.password, null)
                                : DatabaseHelper.createAccountWithUid(requestData.account, 0);
                if (account == null) {
                    responseMessage = translate("messages.dispatch.account.username_create_error");
                    Grasscutter.getLogger()
                            .info(translate("messages.dispatch.account.account_login_create_error", address));
                } else {
                    successfulLogin = true;
                    Grasscutter.getLogger()
                            .info(
                                    translate(
                                            "messages.dispatch.account.account_login_create_success",
                                            address,
                                            account.getId()));
                }
            } else if (account != null) {
                String rawPassword = plainPassword(requestData, useIntegrationPassword);
                if (rawPassword == null) {
                    successfulLogin = true;
                    Grasscutter.getLogger()
                            .info(
                                    "[Dispatch] Client "
                                            + address
                                            + " sent a password this server cannot decrypt; account "
                                            + account.getId()
                                            + " logs in without a password check.");
                } else if ((account.getPassword() == null || account.getPassword().isEmpty())
                        && !rawPassword.isEmpty()) {
                    try {
                        account.setPassword(
                                BCrypt.withDefaults().hashToString(10, rawPassword.toCharArray()));
                        account.save();
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                if (successfulLogin
                        || account.getPassword() == null
                        || account.getPassword().isEmpty()
                        || verifyPassword(account, rawPassword)) {
                    successfulLogin = true;
                } else {
                    responseMessage = translate("messages.dispatch.account.password_error");
                    loggerMessage = translate("messages.dispatch.account.login_password_error", address);
                }
            } else {
                loggerMessage = translate("messages.dispatch.account.account_login_exist_error", address);
            }

            if (successfulLogin) {
                if (account != null && account.isBanned()) {
                    response.retcode = -201;
                    response.message = buildBanMessage(account);
                    loggerMessage = String.format("Login rejected: account %s is banned", account.getId());
                } else {
                    response.message = "OK";
                    response.data.account.uid = account.getId();
                    response.data.account.token = account.generateSessionKey();
                    response.data.account.email = account.getEmail();
                    loggerMessage =
                            translate("messages.dispatch.account.login_success", address, account.getId());
                }
            } else {
                response.retcode = -3201;
                response.message = responseMessage;
            }
            Grasscutter.getLogger().info(loggerMessage);
            return response;
        }
    }

    public static class ExperimentalPasswordAuthenticator implements Authenticator<LoginResultJson> {
        @Override
        public LoginResultJson authenticate(AuthenticationRequest request) {
            var response = new LoginResultJson();
            var requestData = request.getPasswordRequest();
            assert requestData != null;
            boolean successfulLogin = false;
            String address = Utils.address(request.getContext());
            String responseMessage = translate("messages.dispatch.account.username_error");
            String loggerMessage = "";
            String decryptedPassword = "";
            try {
                byte[] key = FileUtils.readResource("/keys/auth_private-key.der");
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.DECRYPT_MODE, privateKey);
                decryptedPassword =
                        new String(
                                cipher.doFinal(Utils.base64Decode(requestData.password)),
                                StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                decryptedPassword = requestData.password;
            }

            if (decryptedPassword == null) {
                loggerMessage = translate("messages.dispatch.account.login_password_error", address);
                responseMessage = translate("messages.dispatch.account.password_error");
            }

            Account account = DatabaseHelper.getAccountByName(requestData.account);
            if (account == null && GAME.account.autoCreate) {
                if (decryptedPassword.length() >= 8) {
                    account = DatabaseHelper.createAccountWithUid(requestData.account, 0);
                    if (account != null) {
                        account.setPassword(
                                BCrypt.withDefaults().hashToString(12, decryptedPassword.toCharArray()));
                        account.save();
                        successfulLogin = true;
                        Grasscutter.getLogger()
                                .info(
                                        translate(
                                                "messages.dispatch.account.account_login_create_success",
                                                address,
                                                response.data.account.uid));
                    } else {
                        responseMessage = translate("messages.dispatch.account.username_create_error");
                        loggerMessage =
                                translate("messages.dispatch.account.account_login_create_error", address);
                    }
                } else {
                    loggerMessage = translate("messages.dispatch.account.login_password_error", address);
                    responseMessage = translate("messages.dispatch.account.password_length_error");
                }
            } else if (account != null) {
                if (account.getPassword() != null && !account.getPassword().isEmpty()) {
                    if (BCrypt.verifyer()
                            .verify(decryptedPassword.toCharArray(), account.getPassword())
                            .verified) {
                        successfulLogin = true;
                    } else {
                        loggerMessage = translate("messages.dispatch.account.login_password_error", address);
                        responseMessage = translate("messages.dispatch.account.password_error");
                    }
                } else if (decryptedPassword != null && !decryptedPassword.isEmpty()) {
                    account.setPassword(
                            BCrypt.withDefaults().hashToString(12, decryptedPassword.toCharArray()));
                    account.save();
                    successfulLogin = true;
                } else {
                    loggerMessage = translate("messages.dispatch.account.login_password_error", address);
                    responseMessage = translate("messages.dispatch.account.password_error");
                }
            } else {
                loggerMessage = translate("messages.dispatch.account.account_login_exist_error", address);
            }

            if (successfulLogin) {
                if (account != null && account.isBanned()) {
                    response.retcode = -201;
                    response.message = buildBanMessage(account);
                    loggerMessage = String.format("Login rejected: account %s is banned", account.getId());
                } else {
                    response.message = "OK";
                    response.data.account.uid = account.getId();
                    response.data.account.token = account.generateSessionKey();
                    response.data.account.email = account.getEmail();
                    loggerMessage =
                            translate("messages.dispatch.account.login_success", address, account.getId());
                }
            } else {
                response.retcode = -3201;
                response.message = responseMessage;
            }
            Grasscutter.getLogger().info(loggerMessage);
            return response;
        }
    }

    public static class TokenAuthenticator implements Authenticator<LoginResultJson> {
        @Override
        public LoginResultJson authenticate(AuthenticationRequest request) {
            var response = new LoginResultJson();
            var requestData = request.getTokenRequest();
            assert requestData != null;
            boolean successfulLogin;
            String address = Utils.address(request.getContext());
            String loggerMessage;
            Grasscutter.getLogger().info(translate("messages.dispatch.account.login_token_attempt", address));
            Account account = DatabaseHelper.getAccountById(requestData.uid);
            successfulLogin = account != null && account.getSessionKey().equals(requestData.token);

            if (successfulLogin) {
                if (account.isBanned()) {
                    response.retcode = -201;
                    response.message = buildBanMessage(account);
                    loggerMessage = String.format("Token login rejected: account %s is banned", account.getId());
                } else {
                    response.message = "OK";
                    response.data.account.uid = account.getId();
                    response.data.account.token = account.getSessionKey();
                    response.data.account.email = account.getEmail();
                    loggerMessage =
                            translate("messages.dispatch.account.login_token_success", address, requestData.uid);
                }
            } else {
                response.retcode = -3201;
                response.message = translate("messages.dispatch.account.account_cache_error");
                loggerMessage = translate("messages.dispatch.account.login_token_error", address);
            }
            Grasscutter.getLogger().info(loggerMessage);
            return response;
        }
    }

    public static class SessionKeyAuthenticator implements Authenticator<ComboTokenResJson> {
        @Override
        public ComboTokenResJson authenticate(AuthenticationRequest request) {
            var response = new ComboTokenResJson();
            var requestData = request.getSessionKeyRequest();
            var loginData = request.getSessionKeyData();
            assert requestData != null;
            boolean successfulLogin;
            String address = Utils.address(request.getContext());
            String loggerMessage;

            if (loginData == null) {
                response.retcode = -3201;
                response.message = translate("messages.dispatch.account.session_key_error");
                Grasscutter.getLogger().info("[Combo] login from " + address + " carried no usable data.");
                return response;
            }

            String dbKey = "";
            Account account = DatabaseHelper.getAccountById(loginData.uid);
            if (account != null) {
                var sk = account.getSessionKey();
                dbKey = sk == null ? "<null>" : sk.substring(0, Math.min(20, sk.length()));
            }
            Grasscutter.getLogger()
                    .info(
                            "[Combo] login from "
                                    + address
                                    + " uid="
                                    + loginData.uid
                                    + " token="
                                    + (loginData.token == null
                                            ? "<null>"
                                            : loginData.token.substring(0, Math.min(20, loginData.token.length())))
                                    + " dbKey="
                                    + dbKey
                                    + " account="
                                    + (account != null));

            if (account != null) {
                var sk = account.getSessionKey();
                if (sk == null || !sk.equals(loginData.token)) {
                    Grasscutter.getLogger()
                            .info(
                                    "[Combo] adopting token for uid="
                                            + loginData.uid
                                            + " (old="
                                            + (sk == null ? "null" : sk.substring(0, Math.min(12, sk.length())))
                                            + " new="
                                            + (loginData.token == null
                                                    ? "null"
                                                    : loginData.token.substring(
                                                            0, Math.min(12, loginData.token.length())))
                                            + ")");
                    account.setSessionKey(loginData.token);
                    account.save();
                }
                successfulLogin = true;
            } else {
                successfulLogin = false;
            }
            Grasscutter.getLogger().info("[Combo] verification=" + successfulLogin);

            if (successfulLogin) {
                if (account.isBanned()) {
                    response.retcode = -201;
                    response.message = buildBanMessage(account);
                    loggerMessage = String.format("Combo login rejected: account %s is banned", account.getId());
                } else {
                    response.message = "OK";
                    response.data.open_id = account.getId();
                    response.data.combo_id = "157795300";
                    response.data.combo_token = account.generateLoginToken();
                    loggerMessage = translate("messages.dispatch.account.combo_token_success", address);
                }
            } else {
                response.retcode = -3201;
                response.message = translate("messages.dispatch.account.session_key_error");
                loggerMessage = translate("messages.dispatch.account.combo_token_error", address);
            }
            Grasscutter.getLogger().info(loggerMessage);
            return response;
        }
    }

    public static class ExternalAuthentication implements ExternalAuthenticator {
        @Override
        public void handleLogin(AuthenticationRequest request) {
            request.getContext().result("Authentication is not available with the default authentication method.");
        }
        @Override
        public void handleAccountCreation(AuthenticationRequest request) {
            request.getContext().result("Authentication is not available with the default authentication method.");
        }
        @Override
        public void handlePasswordReset(AuthenticationRequest request) {
            request.getContext().result("Authentication is not available with the default authentication method.");
        }
    }

    public static class OAuthAuthentication implements OAuthAuthenticator {
        @Override
        public void handleLogin(AuthenticationRequest request) {
            request.getContext().result("Authentication is not available with the default authentication method.");
        }
        @Override
        public void handleRedirection(AuthenticationRequest request, ClientType type) {
            request.getContext().result("Authentication is not available with the default authentication method.");
        }
        @Override
        public void handleTokenProcess(AuthenticationRequest request) {
            request.getContext().result("Authentication is not available with the default authentication method.");
        }
    }

    public static class SessionTokenValidator implements Authenticator<Account> {
        @Override
        public Account authenticate(AuthenticationRequest request) {
            var tokenRequest = request.getTokenRequest();
            if (tokenRequest == null) {
                Grasscutter.getLogger().warn("Invalid session token validator request.");
                return null;
            }

            var client = Grasscutter.getGameServer().getDispatchClient();
            var future = new CompletableFuture<Account>();
            client.registerCallback(
                    PacketIds.TokenValidateRsp,
                    packet -> {
                        var data = IDispatcher.decode(packet);
                        var valid = data.get("valid").getAsBoolean();
                        if (!valid) {
                            future.complete(null);
                            return;
                        }
                        future.complete(IDispatcher.decode(data.get("account"), Account.class));
                    });
            client.sendMessage(PacketIds.TokenValidateReq, tokenRequest);

            try {
                return future.get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public static class HandbookAuthentication implements HandbookAuthenticator {
        private final String authPage;

        public HandbookAuthentication() {
            try {
                this.authPage = new String(FileUtils.readResource("/html/handbook_auth.html"));
            } catch (Exception ignored) {
                throw new RuntimeException("Failed to load handbook auth page.");
            }
        }

        @Override
        public void presentPage(AuthenticationRequest request) {
            var ctx = request.getContext();
            if (ctx == null) return;
            if (Grasscutter.getRunMode() == ServerRunMode.HYBRID) {
                var player = Grasscutter.getGameServer().getPlayerByIpAddress(Utils.address(ctx));
                if (player != null) {
                    var sessionKey = player.getAccount().getSessionKey();
                    ctx.status(200)
                            .result(
                                    this.authPage
                                            .replace("{{VALUE}}", "true")
                                            .replace("{{SESSION_TOKEN}}", sessionKey)
                                            .replace("{{PLAYER_ID}}", String.valueOf(player.getUid())));
                    return;
                }
            }
            ctx.contentType(ContentType.TEXT_HTML).result(this.authPage);
        }

        @Override
        public Response authenticate(AuthenticationRequest request) {
            var ctx = request.getContext();
            if (ctx == null) return null;
            var playerId = ctx.formParam("playerid");
            if (playerId == null) return Response.builder().status(400).body("Invalid player ID.").build();

            try {
                var sessionKey = DispatchUtils.fetchSessionKey(Integer.parseInt(playerId));
                if (sessionKey == null) {
                    return Response.builder().status(400).body("Invalid player ID.").build();
                }
                return Response.builder()
                        .status(200)
                        .body(
                                this.authPage
                                        .replace("{{VALUE}}", "true")
                                        .replace("{{SESSION_TOKEN}}", sessionKey)
                                        .replace("{{PLAYER_ID}}", playerId))
                        .build();
            } catch (NumberFormatException ignored) {
                return Response.builder().status(500).body("Invalid player ID.").build();
            }
        }
    }
}
