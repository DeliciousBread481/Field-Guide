package com.evandev.fieldguide.network;

import com.evandev.fieldguide.FieldGuideLimits;
import com.evandev.fieldguide.server.progress.FieldGuideProgressManager;
import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class UpdateJournalPacket {

    private static final int MAX_TITLE_LENGTH = FieldGuideLimits.MAX_JOURNAL_TITLE_LENGTH;
    private static final int MAX_PAGE_CONTENT_LENGTH = FieldGuideLimits.MAX_JOURNAL_PAGE_CONTENT_LENGTH;
    private static final int MAX_JOURNAL_PAGES = FieldGuideLimits.MAX_JOURNAL_PAGES;

    private final String title;
    private final List<PlayerFieldGuideProgress.JournalPageData> pages;

    public UpdateJournalPacket(String title, List<PlayerFieldGuideProgress.JournalPageData> pages) {
        this.title = title;
        this.pages = pages;
    }

    public UpdateJournalPacket(FriendlyByteBuf buf) {
        this.title = buf.readUtf(MAX_TITLE_LENGTH);
        this.pages = buf.readCollection(
                FriendlyByteBuf.limitValue(ArrayList::new, MAX_JOURNAL_PAGES),
                b -> new PlayerFieldGuideProgress.JournalPageData(
                        b.readUtf(MAX_TITLE_LENGTH),
                        b.readUtf(MAX_PAGE_CONTENT_LENGTH),
                        b.readLong()
                )
        );
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(title, MAX_TITLE_LENGTH);
        buf.writeCollection(pages, (b, page) -> {
            b.writeUtf(page.title(), MAX_TITLE_LENGTH);
            b.writeUtf(page.content(), MAX_PAGE_CONTENT_LENGTH);
            b.writeLong(page.timestamp());
        });
    }

    public void handleServer(ServerPlayer player) {
        if (player == null) return;
        PlayerFieldGuideProgress progress = FieldGuideProgressManager.getInstance().getProgress(player);
        if (progress == null) return;

        progress.setJournalTitle(title);
        progress.setJournalPages(pages);
    }
}
