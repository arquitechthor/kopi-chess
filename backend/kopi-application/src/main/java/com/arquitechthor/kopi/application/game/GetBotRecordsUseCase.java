package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.BotRecord;
import com.arquitechthor.kopi.domain.game.BotProfile;
import java.util.Map;

/// The person's record against each bot: won, lost, drawn, unfinished
/// (plan §9). Shown where the opponent is chosen.
public interface GetBotRecordsUseCase {

    /// One record for each of the five bots, `BotRecord.NONE` for a bot
    /// never played.
    Map<BotProfile, BotRecord> records();
}
