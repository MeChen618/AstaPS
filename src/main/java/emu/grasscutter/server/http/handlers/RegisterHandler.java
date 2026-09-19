package emu.grasscutter.server.http.handlers;

import static emu.grasscutter.config.Configuration.ACCOUNT;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.server.http.Router;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * A web form for creating an account, served only when {@code account.enableWebRegistration} is on.
 *
 * <p>The two pages come from {@code ./account/register.html} and {@code ./account/result.html},
 * which are not shipped: a server owner supplies their own. The result page is rendered by
 * replacing {@code %RESULT%} (success or error) and {@code %MESSAGE%}.
 */
public final class RegisterHandler {
    private static final Path PAGE_DIR = Path.of("account");
    private static final int MAX_USERNAME_LENGTH = 20;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 64;

    /** Deliberately loose: this checks the shape, and only a sent mail proves an address works. */
    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private RegisterHandler() {}

    public record Result(String status, String message) {
        static Result error(String message) {
            return new Result("error", message);
        }
    }

    public static void handleRegistration(@NotNull Context ctx) {
        var result = register(ctx);
        ctx.contentType(ContentType.TEXT_HTML);

        String page;
        try {
            page = Files.readString(PAGE_DIR.resolve("result.html"));
        } catch (Exception e) {
            // Falling back to plain text rather than a 500: the account was created either way, and
            // a missing template should not leave the person unsure whether it worked.
            Grasscutter.getLogger().warn("account/result.html is missing or unreadable.", e);
            ctx.contentType(ContentType.TEXT_PLAIN);
            ctx.result(result.status() + ": " + result.message());
            return;
        }

        ctx.result(page.replace("%RESULT%", result.status()).replace("%MESSAGE%", result.message()));
    }

    private static Result register(@NotNull Context ctx) {
        var username = ctx.formParam("account");
        var email = ctx.formParam("email");
        var password = ctx.formParam("password");
        var passwordAgain = ctx.formParam("password_v2");

        if (username == null || username.isBlank() || username.length() > MAX_USERNAME_LENGTH) {
            return Result.error(
                    "The username must not be empty or longer than "
                            + MAX_USERNAME_LENGTH
                            + " characters.");
        }
        if (email == null || !EMAIL.matcher(email).matches() || email.length() > 50) {
            return Result.error("That email address does not look valid.");
        }
        if (password == null
                || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH) {
            return Result.error(
                    "The password must be between "
                            + MIN_PASSWORD_LENGTH
                            + " and "
                            + MAX_PASSWORD_LENGTH
                            + " characters.");
        }
        if (!password.equals(passwordAgain)) {
            return Result.error("The two passwords do not match.");
        }

        var account = DatabaseHelper.createAccountWithHashedPassword(username, password, email);
        if (account == null) {
            // Same wording whether the name is taken or the write failed, so this cannot be used to
            // find out which usernames exist.
            return Result.error("That account could not be created.");
        }

        Grasscutter.getLogger().info("Registered account {} from the web form.", username);

        // The password is not echoed back. The person typed it, and a credential in an HTML
        // response ends up in browser history, caches and proxies.
        return new Result(
                "success",
                "Account "
                        + account.getUsername()
                        + " created. Sign in with \""
                        + account.getUsername()
                        + "&&<your password>\" in the username box.");
    }

    public static class RegisterRouter implements Router {
        @Override
        public void applyRoutes(Javalin javalin) {
            if (!ACCOUNT.enableWebRegistration) return;

            javalin.get(
                    "/account/register",
                    ctx -> {
                        var page = PAGE_DIR.resolve("register.html");
                        if (!Files.isReadable(page)) {
                            ctx.status(404);
                            ctx.contentType(ContentType.TEXT_PLAIN);
                            ctx.result("account/register.html was not found on the server.");
                            return;
                        }
                        ctx.contentType(ContentType.TEXT_HTML);
                        ctx.result(Files.readString(page));
                    });

            javalin.post("/account/register", RegisterHandler::handleRegistration);

            Grasscutter.getLogger()
                    .info("Web registration is enabled at /account/register.");
        }
    }
}
