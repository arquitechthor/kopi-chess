package com.arquitechthor.kopi.adapter.rest;

import static com.arquitechthor.kopi.adapter.rest.RestAdapterConfiguration.API;

import com.arquitechthor.kopi.adapter.rest.ApiModel.BotRecordResponse;
import com.arquitechthor.kopi.adapter.rest.ApiModel.BotResponse;
import com.arquitechthor.kopi.application.game.GetBotRecordsUseCase;
import com.arquitechthor.kopi.domain.game.BotProfile;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/// `GET /api/v1/bots`: the five bots, weakest first, described in the
/// language of `Accept-Language`, each with the person's record against it.
@RestController
class BotsController {

    private final MessageSource messages;
    private final GetBotRecordsUseCase botRecords;

    BotsController(MessageSource messages, GetBotRecordsUseCase botRecords) {
        this.messages = messages;
        this.botRecords = botRecords;
    }

    @GetMapping(API + "/bots")
    List<BotResponse> bots(@RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
        var locale = ApiLocale.of(language);
        var records = botRecords.records();
        return Arrays.stream(BotProfile.values())
                .map(profile -> new BotResponse(profile, profile.level(),
                        messages.getMessage("rest.bot." + profile + ".description", null, locale),
                        BotRecordResponse.of(records.get(profile))))
                .toList();
    }
}
