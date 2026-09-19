package emu.grasscutter.game;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import emu.grasscutter.database.DatabaseHelper;

/** An IP address that is refused at login. */
@Entity(value = "banned_ips", useDiscriminator = false)
public class BannedIp {
    /** The address itself is the document id, so a ban is looked up by primary key. */
    @Id private String ip;

    private String reason;

    /** Seconds since the epoch, for the record only - an IP ban does not expire on its own. */
    private long banTime;

    @Deprecated // Morphia only. Do not use.
    public BannedIp() {}

    public BannedIp(String ip, String reason) {
        this.ip = ip;
        this.reason = reason;
        this.banTime = System.currentTimeMillis() / 1000;
    }

    public String getIp() {
        return this.ip;
    }

    public String getReason() {
        return this.reason;
    }

    public long getBanTime() {
        return this.banTime;
    }

    public void save() {
        DatabaseHelper.saveBannedIp(this);
    }
}
