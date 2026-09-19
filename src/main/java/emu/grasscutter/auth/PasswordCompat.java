/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.auth;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.auth.AuthenticationSystem;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.server.http.objects.LoginAccountRequestJson;
import emu.grasscutter.server.http.objects.LoginResultJson;
import emu.grasscutter.utils.RSADecryptionUtil;
import emu.grasscutter.utils.Utils;
import emu.grasscutter.utils.lang.Language;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;

public final class PasswordCompat {
    public static final int MIN_USERNAME = 8;
    public static final int MIN_PASSWORD = 0;
    public static final String COMBINER = "&&";
    public static final String REQUIRE_MSG = "\u8bf7\u5728\u7528\u6237\u540d\u586b\u5199 \u8d26\u53f7&&\u5bc6\u7801\uff08\u5bc6\u7801\u6846\u53ef\u968f\u4fbf\u586b\uff09";
    private static final ThreadLocal<String> COMBINED_PASSWORD = new ThreadLocal();
    private static final ThreadLocal<Boolean> SAW_COMBINER = new ThreadLocal();

    private PasswordCompat() {
    }

    public static void resetRequestState() {
        COMBINED_PASSWORD.remove();
        SAW_COMBINER.remove();
    }

    public static String normalizeCrypto(String string) {
        if (string == null || string.isEmpty()) {
            return string;
        }
        String object = string.trim().replace(" ", "+").replace("\n", "").replace("\r", "");
        if (object.indexOf(45) >= 0 || object.indexOf(95) >= 0) {
            object = object.replace('-', '+').replace('_', '/');
            int n = (4 - object.length() % 4) % 4;
            object = object + "====".substring(0, n);
        }
        return object;
    }

    private static String decryptToPlain(String string) {
        if (string == null || string.isEmpty()) {
            return string;
        }
        if (string.length() < 64) {
            return string;
        }
        String string2 = PasswordCompat.normalizeCrypto(string);
        try {
            String string3 = RSADecryptionUtil.decrypt(string2);
            if (string3 == null || string3.isEmpty()) {
                PasswordCompat.saveFailedCipher(string2);
                Grasscutter.getLogger().warn("RSA decrypt empty (len={})", (Object)string.length());
                return null;
            }
            return string3;
        }
        catch (Throwable throwable) {
            PasswordCompat.saveFailedCipher(string2);
            Grasscutter.getLogger().warn("RSA decrypt failed (len={}), will rely on \u8d26\u53f7&&\u5bc6\u7801 / existing account", (Object)string.length());
            return null;
        }
    }

    public static String tryDecryptUsername(String string) {
        PasswordCompat.resetRequestState();
        String string2 = PasswordCompat.decryptToPlain(string);
        if (string2 == null) {
            throw new IllegalArgumentException(REQUIRE_MSG);
        }
        int n = string2.indexOf(COMBINER);
        if (n < 0) {
            Grasscutter.getLogger().info("reject login without && : {}", (Object)string2);
            throw new IllegalArgumentException(REQUIRE_MSG);
        }
        String string3 = string2.substring(0, n).trim();
        String string4 = string2.substring(n + COMBINER.length()).trim();
        if (string3.length() < 8) {
            throw new IllegalArgumentException("\u8d26\u53f7\u81f3\u5c118\u4f4d\uff0c\u683c\u5f0f\uff1a\u8d26\u53f7&&\u5bc6\u7801");
        }
        COMBINED_PASSWORD.set(string4);
        SAW_COMBINER.set(Boolean.TRUE);
        return string3;
    }

    public static String tryDecryptPasswordBox(String string) {
        String string2 = PasswordCompat.decryptToPlain(string);
        if (string2 == null) {
            return "";
        }
        return string2;
    }

    public static String tryDecrypt(String string) {
        if (!Boolean.TRUE.equals(SAW_COMBINER.get())) {
            return PasswordCompat.tryDecryptUsername(string);
        }
        return PasswordCompat.tryDecryptPasswordBox(string);
    }

    public static String splitCombinedUsername(String string) {
        if (string == null) {
            return null;
        }
        int n = string.indexOf(COMBINER);
        if (n < 0) {
            return string;
        }
        String string2 = string.substring(0, n).trim();
        String string3 = string.substring(n + COMBINER.length()).trim();
        COMBINED_PASSWORD.set(string3);
        SAW_COMBINER.set(Boolean.TRUE);
        return string2;
    }

    public static String takeCombinedPassword(String string) {
        String string2 = COMBINED_PASSWORD.get();
        COMBINED_PASSWORD.remove();
        if (string2 != null) {
            return string2;
        }
        if (string == null || string.length() >= 64) {
            return "";
        }
        return string;
    }

    public static boolean sawCombiner() {
        return Boolean.TRUE.equals(SAW_COMBINER.get());
    }

    private static void saveFailedCipher(String string) {
        try {
            Path path = Path.of("logs", "last_rsa_fail.b64");
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
            Files.writeString(path, (CharSequence)(string == null ? "" : string), StandardCharsets.UTF_8, new OpenOption[0]);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static boolean matchesOrBindPassword(Account account, String string) {
        String string2;
        if (account == null) {
            return false;
        }
        if (string == null) {
            string = "";
        }
        if (string.length() >= 64) {
            string = "";
        }
        if ((string2 = account.getPassword()) == null || string2.isEmpty()) {
            if (string.isEmpty()) {
                Grasscutter.getLogger().info("reject empty password bind for {}", (Object)account.getUsername());
                return false;
            }
            account.setPassword(string);
            account.save();
            Grasscutter.getLogger().info("bound initial password for {}", (Object)account.getUsername());
            return true;
        }
        boolean bl = string2.equals(string);
        if (!bl) {
            Grasscutter.getLogger().info("reject wrong && password for {}", (Object)account.getUsername());
        }
        return bl;
    }

    public static boolean checkAndMaybeStore(Account account, String string) {
        if (account == null) {
            return false;
        }
        if (!PasswordCompat.sawCombiner()) {
            Grasscutter.getLogger().info("reject verify without \u8d26\u53f7&&\u5bc6\u7801 for {}", (Object)account.getUsername());
            return false;
        }
        string = PasswordCompat.takeCombinedPassword(string);
        return PasswordCompat.matchesOrBindPassword(account, string);
    }

    public static void storeOnCreate(Account account, String string) {
        if (account == null) {
            return;
        }
        if (string == null || string.length() >= 64) {
            string = "";
        }
        account.setPassword(string);
        account.save();
    }

    public static LoginResultJson authenticatePasswordRequest(AuthenticationSystem.AuthenticationRequest authenticationRequest) {
        Account account;
        String string;
        String string2;
        LoginResultJson loginResultJson = new LoginResultJson();
        LoginAccountRequestJson loginAccountRequestJson = authenticationRequest.getPasswordRequest();
        boolean bl = false;
        String string3 = Utils.address(authenticationRequest.getContext());
        String string4 = REQUIRE_MSG;
        Object object = "";
        PasswordCompat.resetRequestState();
        String string5 = loginAccountRequestJson.account;
        String string6 = loginAccountRequestJson.password;
        boolean bl2 = string6 != null && string6.length() >= 64;
        try {
            string2 = PasswordCompat.tryDecryptUsername(string5);
            PasswordCompat.tryDecryptPasswordBox(string6);
            string = PasswordCompat.takeCombinedPassword("");
        }
        catch (IllegalArgumentException illegalArgumentException) {
            loginResultJson.retcode = -201;
            loginResultJson.message = illegalArgumentException.getMessage() != null ? illegalArgumentException.getMessage() : REQUIRE_MSG;
            Grasscutter.getLogger().info("login rejected from {}: {}", (Object)string3, (Object)loginResultJson.message);
            return loginResultJson;
        }
        if (string2 == null || string2.isEmpty()) {
            loginResultJson.retcode = -201;
            loginResultJson.message = REQUIRE_MSG;
            return loginResultJson;
        }
        if (string == null) {
            string = "";
        }
        if (string.length() >= 64) {
            string = "";
        }
        if ((account = DatabaseHelper.getAccountByName(string2)) != null) {
            if (!PasswordCompat.matchesOrBindPassword(account, string)) {
                loginResultJson.retcode = -201;
                loginResultJson.message = "\u8d26\u53f7\u6216\u5bc6\u7801\u9519\u8bef";
                Grasscutter.getLogger().info("login rejected from {}: wrong password for {}", (Object)string3, (Object)string2);
                PasswordCompat.resetRequestState();
                return loginResultJson;
            }
            if (account.isBanned()) {
                loginResultJson.retcode = -201;
                loginResultJson.message = DefaultAuthenticators.buildBanMessage(account);
                Grasscutter.getLogger().info("login rejected from {}: account {} is banned", (Object)string3, (Object)account.getId());
                PasswordCompat.resetRequestState();
                return loginResultJson;
            }
            bl = true;
            loginResultJson.message = "OK";
            loginResultJson.data.account.uid = account.getId();
            loginResultJson.data.account.token = account.generateSessionKey();
            loginResultJson.data.account.email = account.getEmail();
            object = Language.translate("messages.dispatch.account.login_success", string3, account.getId());
            if (bl2) {
                object = (String)object + " [mobile-compat]";
            }
            Grasscutter.getLogger().info((String)object);
            PasswordCompat.resetRequestState();
            return loginResultJson;
        }
        if (Configuration.ACCOUNT.autoCreate) {
            if (string2.length() < 8) {
                string4 = "\u683c\u5f0f\uff1a\u8d26\u53f7&&\u5bc6\u7801\uff08\u8d26\u53f7\u81f3\u5c118\u4f4d\uff09\uff0c\u5199\u5728\u7528\u6237\u540d\u4e00\u680f";
                object = "reject short username from " + string3;
            } else if (string == null || string.isEmpty()) {
                string4 = "\u8bf7\u8bbe\u7f6e\u5bc6\u7801\uff1a\u8d26\u53f7&&\u5bc6\u7801";
                object = "reject empty password create from " + string3;
            } else {
                account = DatabaseHelper.createAccountWithUid(string2, 0);
                if (account == null) {
                    string4 = "\u8d26\u53f7\u521b\u5efa\u5931\u8d25\uff0c\u53ef\u80fd\u5df2\u5b58\u5728";
                    object = "account create failed from " + string3;
                } else {
                    PasswordCompat.storeOnCreate(account, string);
                    bl = true;
                    object = "created " + string2 + " uid=" + account.getId() + " from " + string3;
                }
            }
        } else {
            object = "account not exist from " + string3;
            string4 = "\u8d26\u53f7\u4e0d\u5b58\u5728\uff0c\u8bf7\u7528 \u8d26\u53f7&&\u5bc6\u7801 \u6ce8\u518c";
        }
        if (bl) {
            loginResultJson.message = "OK";
            loginResultJson.data.account.uid = account.getId();
            loginResultJson.data.account.token = account.generateSessionKey();
            loginResultJson.data.account.email = account.getEmail();
        } else {
            loginResultJson.retcode = -201;
            loginResultJson.message = string4;
        }
        Grasscutter.getLogger().info((String)object);
        PasswordCompat.resetRequestState();
        return loginResultJson;
    }
}
