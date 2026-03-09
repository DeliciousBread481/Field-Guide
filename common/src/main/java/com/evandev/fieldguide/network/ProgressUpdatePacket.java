package com.evandev.fieldguide.network;

import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProgressUpdatePacket {

    private final boolean reset;

    private final List<String> unlocked;
    private final List<String> seen;
    private final Map<String, Long> discoveryTimes;
    private final Map<String, Long> discoveryGameTimes;
    private final List<String> revoked;
    private final Map<String, String> customNames;
    private final Map<String, String> customDescriptions;
    private final Map<String, String> entryPhotographs;
    private final Optional<String> journalTitle;
    private final Optional<List<PlayerFieldGuideProgress.JournalPageData>> journalPages;

    private ProgressUpdatePacket(
            boolean reset,
            List<String> unlocked,
            List<String> seen,
            Map<String, Long> discoveryTimes,
            Map<String, Long> discoveryGameTimes,
            List<String> revoked,
            Map<String, String> customNames,
            Map<String, String> customDescriptions,
            Map<String, String> entryPhotographs,
            Optional<String> journalTitle,
            Optional<List<PlayerFieldGuideProgress.JournalPageData>> journalPages
    ) {
        this.reset = reset;
        this.unlocked = unlocked;
        this.seen = seen;
        this.discoveryTimes = discoveryTimes;
        this.discoveryGameTimes = discoveryGameTimes;
        this.revoked = revoked;
        this.customNames = customNames;
        this.customDescriptions = customDescriptions;
        this.entryPhotographs = entryPhotographs;
        this.journalTitle = journalTitle;
        this.journalPages = journalPages;
    }

    public static ProgressUpdatePacket fullSync(
            List<String> unlocked,
            List<String> seen,
            Map<String, Long> discoveryTimes,
            Map<String, Long> discoveryGameTimes,
            Map<String, String> customNames,
            Map<String, String> customDescriptions,
            Map<String, String> entryPhotographs,
            String journalTitle,
            List<PlayerFieldGuideProgress.JournalPageData> journalPages
    ) {
        return new ProgressUpdatePacket(true,
                unlocked,
                seen,
                discoveryTimes,
                discoveryGameTimes,
                Collections.emptyList(),
                customNames,
                customDescriptions,
                entryPhotographs,
                Optional.of(journalTitle),
                Optional.of(journalPages)
        );
    }

    public static ProgressUpdatePacket delta(
            List<String> unlocked,
            List<String> revoked,
            List<String> seen,
            Map<String, Long> discoveryTimes,
            Map<String, Long> discoveryGameTimes,
            Map<String, String> customNames,
            Map<String, String> customDescriptions,
            Map<String, String> entryPhotographs
    ) {
        return new ProgressUpdatePacket(false,
                unlocked,
                seen,
                discoveryTimes,
                discoveryGameTimes,
                revoked,
                customNames,
                customDescriptions,
                entryPhotographs,
                Optional.empty(),
                Optional.empty()
        );
    }

    public ProgressUpdatePacket(FriendlyByteBuf buf) {
        this.reset = buf.readBoolean();
        this.unlocked = buf.readList(FriendlyByteBuf::readUtf);
        this.seen = buf.readList(FriendlyByteBuf::readUtf);
        this.discoveryTimes = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readLong);
        this.discoveryGameTimes = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readLong);
        this.revoked = buf.readList(FriendlyByteBuf::readUtf);
        this.customNames = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readUtf);
        this.customDescriptions = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readUtf);
        this.entryPhotographs = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readUtf);
        this.journalTitle = buf.readOptional(FriendlyByteBuf::readUtf);
        this.journalPages = buf.readOptional(b -> b.readList(b2 ->
                new PlayerFieldGuideProgress.JournalPageData(b2.readUtf(), b2.readUtf(), b2.readLong())));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(reset);
        buf.writeCollection(unlocked, FriendlyByteBuf::writeUtf);
        buf.writeCollection(seen, FriendlyByteBuf::writeUtf);
        buf.writeMap(discoveryTimes, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeLong);
        buf.writeMap(discoveryGameTimes, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeLong);
        buf.writeCollection(revoked, FriendlyByteBuf::writeUtf);
        buf.writeMap(customNames, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);
        buf.writeMap(customDescriptions, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);
        buf.writeMap(entryPhotographs, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);
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

    public Optional<String> getJournalTitle() {
        return journalTitle;
    }

    public Optional<List<PlayerFieldGuideProgress.JournalPageData>> getJournalPages() {
        return journalPages;
    }
}
