package com.evandev.fieldguide.network;

import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProgressUpdatePacket {

    private final boolean reset;
    private final boolean silent;

    private final List<String> unlocked;
    private final List<String> seen;
    private final Map<String, Long> discoveryTimes;
    private final Map<String, Long> discoveryGameTimes;
    private final List<String> revoked;
    private final Map<String, String> customNames;
    private final Map<String, String> customDescriptions;
    private final Map<String, String> entryPhotographs;
    private final List<String> killedOnly;
    private final Optional<String> journalTitle;
    private final Optional<List<PlayerFieldGuideProgress.JournalPageData>> journalPages;

    private ProgressUpdatePacket(Builder builder) {
        this.reset = builder.reset;
        this.silent = builder.silent;
        this.unlocked = builder.unlocked;
        this.seen = builder.seen;
        this.discoveryTimes = builder.discoveryTimes;
        this.discoveryGameTimes = builder.discoveryGameTimes;
        this.revoked = builder.revoked;
        this.customNames = builder.customNames;
        this.customDescriptions = builder.customDescriptions;
        this.entryPhotographs = builder.entryPhotographs;
        this.killedOnly = builder.killedOnly;
        this.journalTitle = builder.journalTitle;
        this.journalPages = builder.journalPages;
    }

    public ProgressUpdatePacket(FriendlyByteBuf buf) {
        this.reset = buf.readBoolean();
        this.silent = buf.readBoolean();
        this.unlocked = buf.readList(FriendlyByteBuf::readUtf);
        this.seen = buf.readList(FriendlyByteBuf::readUtf);
        this.discoveryTimes = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readLong);
        this.discoveryGameTimes = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readLong);
        this.revoked = buf.readList(FriendlyByteBuf::readUtf);
        this.customNames = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readUtf);
        this.customDescriptions = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readUtf);
        this.entryPhotographs = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readUtf);
        this.killedOnly = buf.readList(FriendlyByteBuf::readUtf);
        this.journalTitle = buf.readOptional(FriendlyByteBuf::readUtf);
        this.journalPages = buf.readOptional(b -> b.readList(b2 ->
                new PlayerFieldGuideProgress.JournalPageData(b2.readUtf(), b2.readUtf(), b2.readLong())));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(reset);
        buf.writeBoolean(silent);
        buf.writeCollection(unlocked, FriendlyByteBuf::writeUtf);
        buf.writeCollection(seen, FriendlyByteBuf::writeUtf);
        buf.writeMap(discoveryTimes, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeLong);
        buf.writeMap(discoveryGameTimes, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeLong);
        buf.writeCollection(revoked, FriendlyByteBuf::writeUtf);
        buf.writeMap(customNames, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);
        buf.writeMap(customDescriptions, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);
        buf.writeMap(entryPhotographs, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);
        buf.writeCollection(killedOnly, FriendlyByteBuf::writeUtf);
        buf.writeOptional(journalTitle, FriendlyByteBuf::writeUtf);
        buf.writeOptional(journalPages, (b, pages) ->
                b.writeCollection(pages, (b2, page) -> {
                    b2.writeUtf(page.title());
                    b2.writeUtf(page.content());
                    b2.writeLong(page.timestamp());
                }));
    }

    public boolean isReset() {
        return reset;
    }

    public boolean isSilent() {
        return silent;
    }

    public List<String> getUnlocked() {
        return unlocked;
    }

    public List<String> getSeen() {
        return seen;
    }

    public Map<String, Long> getDiscoveryTimes() {
        return discoveryTimes;
    }

    public Map<String, Long> getDiscoveryGameTimes() {
        return discoveryGameTimes;
    }

    public List<String> getRevoked() {
        return revoked;
    }

    public Map<String, String> getCustomNames() {
        return customNames;
    }

    public Map<String, String> getCustomDescriptions() {
        return customDescriptions;
    }

    public Map<String, String> getEntryPhotographs() {
        return entryPhotographs;
    }

    public List<String> getKilledOnly() {
        return killedOnly;
    }

    public Optional<String> getJournalTitle() {
        return journalTitle;
    }

    public Optional<List<PlayerFieldGuideProgress.JournalPageData>> getJournalPages() {
        return journalPages;
    }

    public static class Builder {
        private boolean reset = false;
        private boolean silent = false;
        private List<String> unlocked = Collections.emptyList();
        private List<String> seen = Collections.emptyList();
        private Map<String, Long> discoveryTimes = Collections.emptyMap();
        private Map<String, Long> discoveryGameTimes = Collections.emptyMap();
        private List<String> revoked = Collections.emptyList();
        private Map<String, String> customNames = Collections.emptyMap();
        private Map<String, String> customDescriptions = Collections.emptyMap();
        private Map<String, String> entryPhotographs = Collections.emptyMap();
        private List<String> killedOnly = Collections.emptyList();
        private Optional<String> journalTitle = Optional.empty();
        private Optional<List<PlayerFieldGuideProgress.JournalPageData>> journalPages = Optional.empty();

        public Builder reset(boolean reset) {
            this.reset = reset;
            return this;
        }

        public Builder silent(boolean silent) {
            this.silent = silent;
            return this;
        }

        public Builder unlocked(List<String> unlocked) {
            this.unlocked = unlocked;
            return this;
        }

        public Builder seen(List<String> seen) {
            this.seen = seen;
            return this;
        }

        public Builder discoveryTimes(Map<String, Long> times) {
            this.discoveryTimes = times;
            return this;
        }

        public Builder discoveryGameTimes(Map<String, Long> gameTimes) {
            this.discoveryGameTimes = gameTimes;
            return this;
        }

        public Builder revoked(List<String> revoked) {
            this.revoked = revoked;
            return this;
        }

        public Builder customNames(Map<String, String> names) {
            this.customNames = names;
            return this;
        }

        public Builder customDescriptions(Map<String, String> desc) {
            this.customDescriptions = desc;
            return this;
        }

        public Builder entryPhotographs(Map<String, String> photos) {
            this.entryPhotographs = photos;
            return this;
        }

        public Builder killedOnly(List<String> killedOnly) {
            this.killedOnly = killedOnly;
            return this;
        }

        public Builder journalTitle(String title) {
            this.journalTitle = Optional.ofNullable(title);
            return this;
        }

        public Builder journalPages(List<PlayerFieldGuideProgress.JournalPageData> pages) {
            this.journalPages = Optional.ofNullable(pages);
            return this;
        }

        public ProgressUpdatePacket build() {
            return new ProgressUpdatePacket(this);
        }
    }
}
